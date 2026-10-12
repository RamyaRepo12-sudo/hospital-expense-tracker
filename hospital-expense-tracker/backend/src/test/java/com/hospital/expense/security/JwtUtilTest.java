package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hospital.expense.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtUtilTest {
    private static final String SECRET = "a-test-secret-that-is-definitely-longer-than-32-chars";

    @Test
    void tokenRoundTrip() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        String token = jwt.create(new User("finance", "hash", "Meera", "FINANCE_ADMIN"));
        assertEquals("finance", jwt.parse(token).getSubject());
        assertEquals("FINANCE_ADMIN", jwt.parse(token).get("role", String.class));
    }

    @Test
    void tokenCarriesTheTokenVersion() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        User u = new User("finance", "hash", "Meera", "FINANCE_ADMIN");
        u.tokenVersion = 3;
        assertEquals(3, jwt.parse(jwt.create(u)).get("tv", Integer.class));
    }

    @Test
    void scopedTokensAreSingleUseTypesAndNeverSessions() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        String mfa = jwt.createScoped("finance", "mfa", java.util.Map.of("tv", 2), 60_000);
        assertEquals("finance", jwt.parseScoped(mfa, "mfa").getSubject());
        assertThrows(JwtException.class, () -> jwt.parseScoped(mfa, "sso"));
        assertEquals("mfa", jwt.parse(mfa).get("purpose", String.class));
        org.junit.jupiter.api.Assertions.assertNull(jwt.parse(jwt.create(new User("finance", "h", "M", "STAFF"))).get("purpose"));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", 1000));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, -1000);
        String token = jwt.create(new User("finance", "hash", "Meera", "FINANCE_ADMIN"));
        assertThrows(JwtException.class, () -> jwt.parse(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtUtil("another-secret-another-secret-another-secret", 60_000).create(new User("x", "h", "X", "STAFF"));
        assertThrows(JwtException.class, () -> new JwtUtil(SECRET, 60_000).parse(token));
    }
}
