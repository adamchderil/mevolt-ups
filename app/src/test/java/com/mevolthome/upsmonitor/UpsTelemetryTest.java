package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;

public class UpsTelemetryTest {
    @Test
    public void parsesTenFieldUpsmonResponse() throws IOException {
        UpsTelemetry telemetry = UpsTelemetry.parse(
                "230\n229\n42\n50\n49\n96\n1\n2\n3\n2\n");

        assertEquals(230.0, telemetry.inputVoltage, 0.0);
        assertEquals(229.0, telemetry.outputVoltage, 0.0);
        assertEquals(42.0, telemetry.loadPercent, 0.0);
        assertEquals(50.0, telemetry.inputFrequency, 0.0);
        assertEquals(49.0, telemetry.outputFrequency, 0.0);
        assertEquals(96.0, telemetry.batteryPercent, 0.0);
        assertEquals(1, telemetry.avrMode);
        assertEquals(2, telemetry.utilityState);
        assertEquals(3, telemetry.upsState);
        assertEquals(2, telemetry.displayMode);
    }

    @Test(expected = IOException.class)
    public void rejectsTruncatedResponse() throws IOException {
        UpsTelemetry.parse("230\n229\n42\n");
    }

    @Test(expected = IOException.class)
    public void rejectsNonNumericMeasurement() throws IOException {
        UpsTelemetry.parse("invalid\n229\n42\n50\n27\n96\n1\n2\n3\n2\n");
    }
}
