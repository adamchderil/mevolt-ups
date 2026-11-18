package com.mevolthome.upsmonitor;

import java.io.IOException;

final class UpsTelemetry {
    final double inputVoltage;
    final double outputVoltage;
    final double loadPercent;
    final double inputFrequency;
    final double outputFrequency;
    final double batteryPercent;
    final int avrMode;
    final int utilityState;
    final int upsState;
    final int displayMode;

    private UpsTelemetry(double[] values, int[] states) {
        inputVoltage = values[0];
        outputVoltage = values[1];
        loadPercent = values[2];
        inputFrequency = values[3];
        outputFrequency = values[4];
        batteryPercent = values[5];
        avrMode = states[0];
        utilityState = states[1];
        upsState = states[2];
        displayMode = states[3];
    }

    static UpsTelemetry parse(String body) throws IOException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Сервер прислал пустой ответ.");
        }

        String[] fields = trimmed.split("\\s+");
        if (fields.length != 10) {
            throw new IOException("Ожидалось 10 полей телеметрии, получено " + fields.length + ".");
        }

        double[] values = new double[6];
        int[] states = new int[4];
        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.parseDouble(fields[i]);
                if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
                    throw new NumberFormatException("Не конечное число");
                }
            }
            for (int i = 0; i < states.length; i++) {
                states[i] = Integer.parseInt(fields[i + values.length]);
            }
        } catch (NumberFormatException exception) {
            throw new IOException("Ответ UPSMON содержит неверный формат данных.", exception);
        }

        return new UpsTelemetry(values, states);
    }

    boolean hasOnlyZeroMeasurements() {
        return inputVoltage == 0
                && outputVoltage == 0
                && loadPercent == 0
                && inputFrequency == 0
                && outputFrequency == 0
                && batteryPercent == 0;
    }
}
