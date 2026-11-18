package com.mevolthome.upsmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class EncryptedCredentialStore {
    private static final String PREFERENCES = "upsmon_credentials_encrypted";
    private static final String KEY_ALIAS = "mevolt_ups_credentials";
    private static final String IV_KEY = "iv";
    private static final String DATA_KEY = "data";

    private EncryptedCredentialStore() {
    }

    static void save(Context context, String username, char[] password)
            throws GeneralSecurityException, IOException {
        SecretKey key = getOrCreateKey();
        byte[] plaintext = (username + "\n" + new String(password))
                .getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plaintext);
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFERENCES, Context.MODE_PRIVATE);
            boolean saved = preferences.edit()
                    .putString(IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(DATA_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .commit();
            Arrays.fill(encrypted, (byte) 0);
            if (!saved) {
                throw new IOException("Не удалось сохранить защищённые данные входа.");
            }
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    static Credentials load(Context context) throws GeneralSecurityException, IOException {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        String encodedIv = preferences.getString(IV_KEY, null);
        String encodedData = preferences.getString(DATA_KEY, null);
        if (encodedIv == null && encodedData == null) {
            return null;
        }
        if (encodedIv == null || encodedData == null) {
            throw new IOException("Сохранённые данные входа повреждены.");
        }

        KeyStore keyStore = loadKeyStore();
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            throw new IOException("Ключ защищённого хранилища недоступен.");
        }
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        byte[] iv;
        byte[] encrypted;
        try {
            iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            encrypted = Base64.decode(encodedData, Base64.NO_WRAP);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Сохранённые данные входа повреждены.", exception);
        }
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            plaintext = cipher.doFinal(encrypted);
            String decoded = new String(plaintext, StandardCharsets.UTF_8);
            int separator = decoded.indexOf('\n');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw new IOException("Сохранённые данные входа имеют неверный формат.");
            }
            return new Credentials(decoded.substring(0, separator),
                    decoded.substring(separator + 1).toCharArray());
        } finally {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    static boolean hasSavedCredentials(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        return preferences.contains(IV_KEY) && preferences.contains(DATA_KEY);
    }

    static void clear(Context context) throws GeneralSecurityException {
        boolean cleared = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        if (!cleared) {
            throw new GeneralSecurityException("Не удалось удалить сохранённые данные входа.");
        }
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        generator.init(specification);
        return generator.generateKey();
    }

    private static KeyStore loadKeyStore() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (IOException exception) {
            throw new GeneralSecurityException("Не удалось открыть хранилище Android Keystore.",
                    exception);
        }
        return keyStore;
    }

    static final class Credentials implements AutoCloseable {
        final String username;
        final char[] password;

        Credentials(String username, char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void close() {
            Arrays.fill(password, '\0');
        }
    }
}
