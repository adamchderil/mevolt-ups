package com.mevolthome.upsmonitor;

final class AdaptiveLayoutMath {
    private AdaptiveLayoutMath() {
    }

    static int stateColumnCount(int screenWidthDp, float fontScale) {
        return screenWidthDp < 400 || fontScale > 1.1f ? 2 : 3;
    }

    static int voltageMeterColumnCount(int screenWidthDp, float fontScale) {
        return screenWidthDp < 360 || fontScale > 1.25f ? 1 : 2;
    }
}
