package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class SessionCookiesTest {
    @Test
    void sessionCookieIsHttpOnlySameSiteStrictAndSecureOnHttps() {
        ResponseCookie c = SessionCookies.create("tok", true, 60_000);
        assertTrue(c.isHttpOnly());
        assertTrue(c.isSecure());
        assertEquals("Strict", c.getSameSite());
        assertEquals("/", c.getPath());
        assertEquals("tok", c.getValue());
    }

    @Test
    void secureFlagIsOffForPlainHttpDevelopment() {
        assertFalse(SessionCookies.create("tok", false, 60_000).isSecure());
    }

    @Test
    void clearingExpiresTheCookie() {
        ResponseCookie c = SessionCookies.clear(true);
        assertEquals(0, c.getMaxAge().getSeconds());
        assertEquals("", c.getValue());
    }
}
