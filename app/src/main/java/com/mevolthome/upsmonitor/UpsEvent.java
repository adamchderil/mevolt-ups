package com.mevolthome.upsmonitor;

import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class UpsEvent {
    final Date timestamp;
    final String description;

    private UpsEvent(Date timestamp, String description) {
        this.timestamp = timestamp;
        this.description = description;
    }

    static List<UpsEvent> parseCsv(String body) throws IOException {
        List<UpsEvent> events = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return events;
        }

        String content = body.startsWith("\uFEFF") ? body.substring(1) : body;
        String[] lines = content.split("\\r?\\n");
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd.MM.yyyy H:mm:ss", Locale.ROOT);
        dateFormat.setLenient(false);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            int separator = line.indexOf(';');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IOException("Неверная запись журнала в строке " + (i + 1) + ".");
            }

            String dateText = line.substring(0, separator).trim();
            String description = line.substring(separator + 1).trim();
            ParsePosition position = new ParsePosition(0);
            Date timestamp = dateFormat.parse(dateText, position);
            if (timestamp == null || position.getIndex() != dateText.length()
                    || description.isEmpty()) {
                throw new IOException("Неверная запись журнала в строке " + (i + 1) + ".");
            }
            events.add(new UpsEvent(timestamp, description));
        }

        Collections.sort(events, (left, right) -> right.timestamp.compareTo(left.timestamp));
        return events;
    }
}
