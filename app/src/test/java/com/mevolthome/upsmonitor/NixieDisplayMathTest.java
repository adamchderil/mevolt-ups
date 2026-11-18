package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NixieDisplayMathTest {
    @Test
    public void formatsVoltageAsThreeDigits() {
        assertEquals("000", NixieDisplayMath.voltageDigits(0));
        assertEquals("218", NixieDisplayMath.voltageDigits(218.4));
        assertEquals("219", NixieDisplayMath.voltageDigits(218.7));
    }

    @Test
    public void clampsVoltageAndMarksNonFiniteValuesAsMissing() {
        assertEquals("000", NixieDisplayMath.voltageDigits(-10));
        assertEquals("999", NixieDisplayMath.voltageDigits(1250));
        assertEquals("---", NixieDisplayMath.voltageDigits(Double.NaN));
        assertEquals("---", NixieDisplayMath.voltageDigits(Double.POSITIVE_INFINITY));
    }

    @Test
    public void mapsBatteryChargeToEightVisibleCells() {
        assertEquals(0, NixieDisplayMath.activeBatteryCells(0));
        assertEquals(5, NixieDisplayMath.activeBatteryCells(63));
        assertEquals(8, NixieDisplayMath.activeBatteryCells(100));
        assertEquals(0, NixieDisplayMath.activeBatteryCells(-5));
        assertEquals(8, NixieDisplayMath.activeBatteryCells(150));
    }
}
