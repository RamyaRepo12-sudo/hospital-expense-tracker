package com.hospital.expense.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** The session lives in an HttpOnly, SameSite=Strict cookie so JavaScript (and any injected script) can never read it. */
public final class SessionCookies {
    public static final String NAME = "hfcc_session";

    private SessionCookies() {}

    public static ResponseCookie create(String token, boolean secure, long ttlMs) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(secure).sameSite("Strict").path("/")
                .maxAge(Duration.ofMillis(ttlMs)).build();
    }

    public static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(NAME, "").httpOnly(true).secure(secure).sameSite("Strict").path("/").maxAge(0).build();
    }
}
