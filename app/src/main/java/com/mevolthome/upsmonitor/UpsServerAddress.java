package com.mevolthome.upsmonitor;

import java.util.Locale;

final class UpsServerAddress {
    private UpsServerAddress() {
    }

    static String normalizeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || !parts[i].matches("\\d{1,3}")) {
                return null;
            }
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return null;
            }
        }

        boolean privateAddress = octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
        boolean tailscaleAddress = octets[0] == 100
                && octets[1] >= 64 && octets[1] <= 127;
        return privateAddress || tailscaleAddress
                ? String.format(Locale.US, "%d.%d.%d.%d",
                octets[0], octets[1], octets[2], octets[3])
                : null;
    }

    static String normalizeHttpsHost(String value) {
        if (value == null) {
            return null;
        }

        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.length() > 253 || host.contains("/") || host.contains(":")
                || !host.matches("(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            return null;
        }
        return host;
    }
}
