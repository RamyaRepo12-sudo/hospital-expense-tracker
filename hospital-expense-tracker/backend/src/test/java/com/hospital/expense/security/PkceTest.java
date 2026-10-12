package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PkceTest {
    @Test
    void matchesTheRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void randomValuesAreLongEnoughAndDifferent() {
        assertEquals(64, Pkce.random(48).length());
        assertNotEquals(Pkce.random(24), Pkce.random(24));
    }
}
