package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TotpTest {
    // RFC 6238 appendix B test key (SHA-1): the ASCII text "12345678901234567890"
    private static final byte[] KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        assertEquals("94287082", Totp.code(KEY, 59L / 30, 8));
        assertEquals("07081804", Totp.code(KEY, 1111111109L / 30, 8));
        assertEquals("14050471", Totp.code(KEY, 1111111111L / 30, 8));
        assertEquals("89005924", Totp.code(KEY, 1234567890L / 30, 8));
        assertEquals("69279037", Totp.code(KEY, 2000000000L / 30, 8));
        assertEquals("287082", Totp.code(KEY, 1, 6));
    }

    @Test
    void base32RoundTripsAndMatchesTheKnownEncoding() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(KEY));
        assertArrayEquals(KEY, Totp.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"));
        assertArrayEquals(KEY, Totp.decode(Totp.base32(KEY)));
        String fresh = Totp.newSecret();
        assertEquals(32, fresh.length());
    }

    @Test
    void acceptsTheCurrentAndNeighbouringStepButNeverReplaysOne() {
        String secret = Totp.base32(KEY);
        assertEquals(1, Totp.verify(secret, "287082", 59_000L, 0));
        assertEquals(1, Totp.verify(secret, "287082", 89_000L, 0)); // phone clock one step behind
        assertEquals(-1, Totp.verify(secret, "287082", 59_000L, 1)); // same code twice
        assertEquals(-1, Totp.verify(secret, "287082", 200_000L, 0)); // far too old
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = Totp.base32(KEY);
        assertEquals(-1, Totp.verify(secret, "28708", 59_000L, 0));
        assertEquals(-1, Totp.verify(secret, "abcdef", 59_000L, 0));
        assertEquals(-1, Totp.verify(secret, null, 59_000L, 0));
        assertEquals(-1, Totp.verify(secret, "000000", 59_000L, 0));
    }

    @Test
    void buildsAScannableUri() {
        String uri = Totp.otpauthUri("Hospital Finance", "meera@x.org", "ABC");
        assertTrue(uri.startsWith("otpauth://totp/Hospital%20Finance:meera%40x.org?secret=ABC"));
        assertTrue(uri.contains("&issuer=Hospital%20Finance"));
    }
}
