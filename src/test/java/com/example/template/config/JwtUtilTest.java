package com.example.template.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.template.model.Role;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-at-least-32-characters-long-000";

    @Test
    void generateAndParse_roundTrips() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
        String token = jwtUtil.generateToken("alice", Set.of(Role.ADMIN));

        assertEquals("alice", jwtUtil.getUsernameFromToken(token));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void expiredToken_isInvalid() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, -1_000); // issued already expired
        String token = jwtUtil.generateToken("bob", Set.of(Role.USER));

        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void tamperedToken_isInvalid() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
        String token = jwtUtil.generateToken("carol", Set.of(Role.USER));
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2]; // mutate the payload

        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void tokenSignedWithDifferentKey_isInvalid() {
        JwtUtil issuer = new JwtUtil(SECRET, 60_000);
        JwtUtil verifier = new JwtUtil("a-different-secret-at-least-32-characters-long", 60_000);
        String token = issuer.generateToken("dave", Set.of(Role.USER));

        assertFalse(verifier.validateToken(token));
    }

    @Test
    void shortSecret_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short", 60_000));
    }
}
