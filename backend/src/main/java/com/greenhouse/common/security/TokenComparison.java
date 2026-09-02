package com.greenhouse.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TokenComparison {

    private TokenComparison() {
    }

    // Length-independent and constant-time for equal lengths, so a near-miss
    // cannot be distinguished from a wild guess by how long the check took.
    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
