package com.hospital.expense.security;

import org.springframework.stereotype.Component;

/** Brute-force protection: 5 failures per key (username + address) lock that key until the 15-minute window ends. */
@Component
public class LoginRateLimiter {
    public static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private final RateStore store;

    public LoginRateLimiter(RateStore store) { this.store = store; }

    public boolean blocked(String key) { return store.count(key, WINDOW_MS) >= MAX_FAILURES; }

    public void fail(String key) { store.hit(key, WINDOW_MS); }

    public void reset(String key) { store.clear(key, WINDOW_MS); }
}
