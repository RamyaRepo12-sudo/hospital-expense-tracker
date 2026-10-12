package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretBoxTest {
    private static final String JWT = "jwt-secret-material-that-is-long-enough-1234";

    @Test
    void encryptsAndDecrypts() {
        SecretBox box = new SecretBox("", JWT);
        String sealed = box.seal("JBSWY3DPEHPK3PXP");
        assertNotEquals("JBSWY3DPEHPK3PXP", sealed);
        assertEquals("JBSWY3DPEHPK3PXP", box.open(sealed));
    }

    @Test
    void usesAFreshIvEachTime() {
        SecretBox box = new SecretBox("", JWT);
        assertNotEquals(box.seal("same"), box.seal("same"));
    }

    @Test
    void rejectsTamperingAndTheWrongKey() {
        SecretBox box = new SecretBox("dedicated-mfa-key-material-1234567890", JWT);
        byte[] raw = Base64.getDecoder().decode(box.seal("secret"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> box.open(tampered));
        String sealed = box.seal("secret");
        assertThrows(IllegalStateException.class, () -> new SecretBox("another-key-material-0987654321", JWT).open(sealed));
    }
}
