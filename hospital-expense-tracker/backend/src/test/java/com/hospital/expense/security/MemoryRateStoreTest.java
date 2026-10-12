package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MemoryRateStoreTest {
    @Test
    void countsWithinAWindowAndStartsOverInTheNext() {
        AtomicLong now = new AtomicLong(60_000L * 10);
        MemoryRateStore s = new MemoryRateStore(now::get);
        assertEquals(1, s.hit("k", 60_000));
        assertEquals(2, s.hit("k", 60_000));
        assertEquals(2, s.count("k", 60_000));
        now.addAndGet(60_000);
        assertEquals(0, s.count("k", 60_000));
        assertEquals(1, s.hit("k", 60_000));
    }

    @Test
    void clearRemovesTheCounter() {
        MemoryRateStore s = new MemoryRateStore(() -> 123_456_789L);
        s.hit("k", 60_000);
        s.clear("k", 60_000);
        assertEquals(0, s.count("k", 60_000));
    }
}
