package com.mevolthome.upsmonitor;

final class VoltageGaugeMath {
    private VoltageGaugeMath() {
    }

    static float angleForVoltage(float voltage) {
        float clampedVoltage = Math.max(0f, Math.min(300f, voltage));
        return 180f + clampedVoltage * 0.6f;
    }
}
