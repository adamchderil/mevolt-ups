package com.mevolthome.upsmonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class UpsGatewayClient {
    static final String DEFAULT_HOST = "win-pjghmomb24s.tail184144.ts.net";

    private UpsGatewayClient() {
    }

    static AuthenticationResult authenticate(
            String host, int port, String username, char[] password) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(host, port, "auth", username, password);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return new AuthenticationResult(true,
                        "admin".equalsIgnoreCase(connection.getHeaderField("X-MeVolt-Role")));
            }
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return new AuthenticationResult(false, false);
            }
            throw new IOException("Сервер авторизации вернул HTTP " + responseCode + ".");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static UpsTelemetry fetchTelemetry(
            String host, int port, String username, char[] password) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(host, port, "ups.txt", username, password);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/plain");
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new AuthenticationRequiredException();
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Сервер вернул HTTP " + responseCode + ".");
            }
            return UpsTelemetry.parse(readBoundedBody(connection.getInputStream(), 1024));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static URL url(String host, int port, String endpoint) throws IOException {
        return new URL("https", host, port, "/upsmon/" + endpoint);
    }

    static void setAuthorization(
            HttpURLConnection connection, String username, char[] password) {
        connection.setInstanceFollowRedirects(false);
        byte[] credentials = (username + ":" + new String(password))
                .getBytes(StandardCharsets.UTF_8);
        try {
            connection.setRequestProperty("Authorization",
                    "Basic " + android.util.Base64.encodeToString(
                            credentials, android.util.Base64.NO_WRAP));
            connection.setRequestProperty("Cache-Control", "no-store");
        } finally {
            Arrays.fill(credentials, (byte) 0);
        }
    }

    private static HttpURLConnection openConnection(
            String host, int port, String endpoint, String username, char[] password)
            throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) url(host, port, endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        setAuthorization(connection, username, password);
        return connection;
    }

    private static String readBoundedBody(InputStream inputStream, int maxLength)
            throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() + line.length() > maxLength) {
                    throw new IOException("Ответ сервера слишком большой.");
                }
                body.append(line).append('\n');
            }
        }
        return body.toString();
    }

    static final class AuthenticationResult {
        final boolean accepted;
        final boolean admin;

        AuthenticationResult(boolean accepted, boolean admin) {
            this.accepted = accepted;
            this.admin = admin;
        }
    }

    static final class AuthenticationRequiredException extends IOException {
        AuthenticationRequiredException() {
            super("Сохранённая учётная запись больше не действительна.");
        }
    }
}
