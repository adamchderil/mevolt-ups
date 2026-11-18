package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VoltageGaugeMathTest {
    @Test
    public void mapsVoltageAcrossTheGaugeRange() {
        assertEquals(180f, VoltageGaugeMath.angleForVoltage(0f), 0.001f);
        assertEquals(270f, VoltageGaugeMath.angleForVoltage(150f), 0.001f);
        assertEquals(360f, VoltageGaugeMath.angleForVoltage(300f), 0.001f);
    }

    @Test
    public void clampsValuesOutsideTheGaugeRange() {
        assertEquals(180f, VoltageGaugeMath.angleForVoltage(-10f), 0.001f);
        assertEquals(360f, VoltageGaugeMath.angleForVoltage(350f), 0.001f);
    }
}
