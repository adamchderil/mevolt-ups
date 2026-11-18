package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class UpsEventTest {
    @Test
    public void parsesRussianAndEnglishEventsNewestFirst() throws IOException {
        List<UpsEvent> events = UpsEvent.parseCsv(
                "\uFEFF17.11.2026 20:28:21;UPS Self Test\r\n"
                        + "18.11.2026 0:14:44;Соединение восстановлено\r\n");

        assertEquals(2, events.size());
        assertEquals("Соединение восстановлено", events.get(0).description);
        assertEquals("UPS Self Test", events.get(1).description);
        assertEquals("18.11.2026 00:14:44",
                new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
                        .format(events.get(0).timestamp));
    }

    @Test(expected = IOException.class)
    public void rejectsMalformedEventLine() throws IOException {
        UpsEvent.parseCsv("not a CSV event");
    }
}
