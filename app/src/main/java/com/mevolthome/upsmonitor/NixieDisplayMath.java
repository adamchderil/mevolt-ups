package com.mevolthome.upsmonitor;

import java.util.Locale;

final class NixieDisplayMath {
    private NixieDisplayMath() {
    }

    static String voltageDigits(double voltage) {
        if (Double.isNaN(voltage) || Double.isInfinite(voltage)) {
            return "---";
        }
        int rounded = (int) Math.max(0, Math.min(999, Math.round(voltage)));
        return String.format(Locale.US, "%03d", rounded);
    }

    static int activeBatteryCells(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) {
            return 0;
        }
        double clamped = Math.max(0, Math.min(100, percent));
        return (int) Math.round(clamped * 8 / 100d);
    }
}
