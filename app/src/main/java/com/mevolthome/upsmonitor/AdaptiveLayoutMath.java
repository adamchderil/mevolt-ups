package com.mevolthome.upsmonitor;

final class AdaptiveLayoutMath {
    private AdaptiveLayoutMath() {
    }

    static int stateColumnCount(int screenWidthDp, float fontScale) {
        return screenWidthDp < 400 || fontScale > 1.1f ? 2 : 3;
    }
}
