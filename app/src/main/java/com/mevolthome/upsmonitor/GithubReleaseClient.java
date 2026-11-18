package com.mevolthome.upsmonitor;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GithubReleaseClient {
    private static final String RELEASE_API =
            "https://api.github.com/repos/adamchderil/mevolt-ups/releases/latest";
    private static final long MAXIMUM_APK_SIZE = 100L * 1024L * 1024L;
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^v?(\\d+(?:\\.\\d+)*)$");

    private GithubReleaseClient() {
    }

    static UpdateInfo checkForUpdate(String currentVersion) throws IOException, JSONException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MeVolt-UPS-Android");
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub вернул HTTP " + responseCode + ".");
            }

            JSONObject release = new JSONObject(readBoundedBody(connection.getInputStream()));
            String tag = release.getString("tag_name");
            if (!isNewerVersion(tag, currentVersion)) {
                return null;
            }

            JSONArray assets = release.getJSONArray("assets");
            JSONObject apkAsset = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                String name = asset.optString("name", "");
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    if ("mevolt-ups.apk".equalsIgnoreCase(name)) {
                        apkAsset = asset;
                        break;
                    }
                    if (apkAsset == null) {
                        apkAsset = asset;
                    }
                }
            }
            if (apkAsset == null) {
                throw new IOException("В последнем релизе GitHub нет APK-файла.");
            }

            long size = apkAsset.optLong("size", 0);
            URL downloadUrl = new URL(apkAsset.getString("browser_download_url"));
            if (size <= 0 || size > MAXIMUM_APK_SIZE
                    || !"https".equalsIgnoreCase(downloadUrl.getProtocol())
                    || !isGithubAssetHost(downloadUrl.getHost())) {
                throw new IOException("APK в релизе имеет недопустимый размер или адрес.");
            }
            return new UpdateInfo(tag, downloadUrl, size);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static boolean isNewerVersion(String releaseTag, String currentVersion) {
        int[] release = parseVersion(releaseTag);
        int[] current = parseVersion(currentVersion);
        if (release == null || current == null) {
            return false;
        }
        int length = Math.max(release.length, current.length);
        for (int index = 0; index < length; index++) {
            int releasePart = index < release.length ? release[index] : 0;
            int currentPart = index < current.length ? current[index] : 0;
            if (releasePart != currentPart) {
                return releasePart > currentPart;
            }
        }
        return false;
    }

    static File download(Context context, UpdateInfo update) throws IOException {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Не удалось подготовить папку для обновления.");
        }

        File temporary = File.createTempFile("mevolt-update-", ".apk", directory);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) update.downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            URL finalUrl = connection.getURL();
            if (responseCode != HttpURLConnection.HTTP_OK
                    || !"https".equalsIgnoreCase(finalUrl.getProtocol())
                    || !isGithubAssetHost(finalUrl.getHost())) {
                throw new IOException("Не удалось безопасно загрузить APK из GitHub.");
            }

            int declaredLength = connection.getContentLength();
            if (declaredLength > MAXIMUM_APK_SIZE
                    || (declaredLength > 0 && declaredLength != update.size)) {
                throw new IOException("Размер загружаемого APK не совпадает с релизом.");
            }

            long received = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    received += count;
                    if (received > MAXIMUM_APK_SIZE || received > update.size) {
                        throw new IOException("Загружаемый APK превышает допустимый размер.");
                    }
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (received != update.size) {
                throw new IOException("Загружен неполный APK-файл.");
            }
            return temporary;
        } catch (IOException exception) {
            if (!temporary.delete() && temporary.exists()) {
                exception.addSuppressed(new IOException(
                        "Не удалось удалить временный APK-файл."));
            }
            throw exception;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBoundedBody(InputStream input) throws IOException {
        StringBuilder body = new StringBuilder();
        byte[] buffer = new byte[4096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > 1024 * 1024) {
                throw new IOException("Ответ GitHub слишком большой.");
            }
            body.append(new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static int[] parseVersion(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        String[] parts = matcher.group(1).split("\\.");
        int[] version = new int[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                version[index] = Integer.parseInt(parts[index]);
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return version;
    }

    private static boolean isGithubAssetHost(String host) {
        return "github.com".equalsIgnoreCase(host)
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".githubusercontent.com");
    }

    static final class UpdateInfo {
        final String version;
        final URL downloadUrl;
        final long size;

        UpdateInfo(String version, URL downloadUrl, long size) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.size = size;
        }
    }
}
