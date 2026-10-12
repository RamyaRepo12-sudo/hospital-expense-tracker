package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryCodesTest {
    @Test
    void generatesTenUniqueReadableCodes() {
        List<String> codes = RecoveryCodes.generate(10);
        assertEquals(10, codes.size());
        assertEquals(10, new HashSet<>(codes).size());
        codes.forEach(c -> assertTrue(c.matches("[a-z2-9]{5}-[a-z2-9]{5}"), c));
    }

    @Test
    void hashingIgnoresCaseAndDashesButDependsOnTheKey() {
        assertEquals(RecoveryCodes.hash("abcde-fghjk", "k1"), RecoveryCodes.hash("ABCDE FGHJK", "k1"));
        assertNotEquals(RecoveryCodes.hash("abcde-fghjk", "k1"), RecoveryCodes.hash("abcde-fghjk", "k2"));
        assertNotEquals(RecoveryCodes.hash("abcde-fghjk", "k1"), RecoveryCodes.hash("abcde-fghjm", "k1"));
    }

    @Test
    void tellsRecoveryCodesApartFromSixDigitCodes() {
        assertTrue(RecoveryCodes.looksLikeRecoveryCode("abcde-fghjk"));
        assertFalse(RecoveryCodes.looksLikeRecoveryCode("123456"));
        assertFalse(RecoveryCodes.looksLikeRecoveryCode("abc"));
    }
}
