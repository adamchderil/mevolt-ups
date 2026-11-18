package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdaptiveLayoutMathTest {
    @Test
    public void usesTwoColumnsForCompactScreens() {
        assertEquals(2, AdaptiveLayoutMath.stateColumnCount(399, 1f));
    }

    @Test
    public void usesThreeColumnsForWideScreensWithDefaultFont() {
        assertEquals(3, AdaptiveLayoutMath.stateColumnCount(400, 1.1f));
    }

    @Test
    public void usesTwoColumnsWhenFontScaleIsLarge() {
        assertEquals(2, AdaptiveLayoutMath.stateColumnCount(420, 1.2f));
    }

    @Test
    public void stacksVoltageMetersOnCompactScreens() {
        assertEquals(1, AdaptiveLayoutMath.voltageMeterColumnCount(359, 1f));
        assertEquals(2, AdaptiveLayoutMath.voltageMeterColumnCount(360, 1f));
        assertEquals(1, AdaptiveLayoutMath.voltageMeterColumnCount(420, 1.3f));
    }
}
