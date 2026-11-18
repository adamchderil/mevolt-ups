package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class UpsServerAddressTest {
    @Test
    public void acceptsTailscaleIpv4Address() {
        assertEquals("100.109.48.69",
                UpsServerAddress.normalizeIpv4("100.109.48.69"));
    }

    @Test
    public void rejectsAddressesOutsidePrivateAndTailscaleRanges() {
        assertNull(UpsServerAddress.normalizeIpv4("100.128.0.1"));
        assertNull(UpsServerAddress.normalizeIpv4("8.8.8.8"));
    }

    @Test
    public void normalizesHttpsHostAndRejectsAddressesWithoutAHostname() {
        assertEquals("win-pjghmomb24s.tail184144.ts.net",
                UpsServerAddress.normalizeHttpsHost(
                        "  https://WIN-pjghmomb24s.tail184144.ts.net/  "));
        assertNull(UpsServerAddress.normalizeHttpsHost("100.109.48.69"));
        assertNull(UpsServerAddress.normalizeHttpsHost("example.com/path"));
        assertNull(UpsServerAddress.normalizeHttpsHost("example.com:8443"));
    }
}
