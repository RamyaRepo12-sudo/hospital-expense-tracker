package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SsoEmailTest {
    @Test
    void usesTheVerifiedEmailInLowerCase() {
        assertEquals("meera@hospital.org", SsoService.emailFrom(Map.<String, Object>of("email", " Meera@Hospital.org ", "email_verified", true)));
    }

    @Test
    void refusesAnUnverifiedEmail() {
        assertNull(SsoService.emailFrom(Map.<String, Object>of("email", "meera@hospital.org", "email_verified", false)));
    }

    @Test
    void fallsBackToThePrincipalNameThatMicrosoftSends() {
        assertEquals("imran@hospital.org", SsoService.emailFrom(Map.<String, Object>of("preferred_username", "Imran@hospital.org")));
        assertEquals("a@b.org", SsoService.emailFrom(Map.<String, Object>of("upn", "a@b.org")));
    }

    @Test
    void returnsNothingWhenThereIsNoEmailAddress() {
        assertNull(SsoService.emailFrom(Map.<String, Object>of("sub", "123", "preferred_username", "not-an-email")));
    }
}
