package com.mevolthome.upsmonitor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GithubReleaseClientTest {
    @Test
    public void comparesNumericVersionParts() {
        assertTrue(GithubReleaseClient.isNewerVersion("v1.10", "1.9"));
        assertTrue(GithubReleaseClient.isNewerVersion("1.9.1", "1.9"));
        assertFalse(GithubReleaseClient.isNewerVersion("v1.8", "1.8"));
        assertFalse(GithubReleaseClient.isNewerVersion("v1.7", "1.8"));
        assertFalse(GithubReleaseClient.isNewerVersion("latest", "1.8"));
    }
}
