package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void locksAfterFiveFailuresAndResets() {
        LoginRateLimiter l = new LoginRateLimiter(new MemoryRateStore(() -> 5_000_000L));
        for (int i = 0; i < 4; i++) l.fail("finance|1.2.3.4");
        assertFalse(l.blocked("finance|1.2.3.4"));
        l.fail("finance|1.2.3.4");
        assertTrue(l.blocked("finance|1.2.3.4"));
        l.reset("finance|1.2.3.4");
        assertFalse(l.blocked("finance|1.2.3.4"));
    }

    @Test
    void keysAreIndependent() {
        LoginRateLimiter l = new LoginRateLimiter(new MemoryRateStore(() -> 5_000_000L));
        for (int i = 0; i < 5; i++) l.fail("a|1");
        assertTrue(l.blocked("a|1"));
        assertFalse(l.blocked("b|1"));
    }

    @Test
    void lockoutEndsWhenTheFifteenMinuteWindowEnds() {
        AtomicLong now = new AtomicLong(15L * 60_000 * 100);
        LoginRateLimiter l = new LoginRateLimiter(new MemoryRateStore(now::get));
        for (int i = 0; i < 5; i++) l.fail("a|1");
        assertTrue(l.blocked("a|1"));
        now.addAndGet(15L * 60_000);
        assertFalse(l.blocked("a|1"));
    }
}
