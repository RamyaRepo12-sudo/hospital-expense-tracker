package com.hospital.expense.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Per-instance counters (RATE_LIMIT_STORE=memory). Also used by the unit tests with a fixed clock. */
public class MemoryRateStore implements RateStore {
    private final ConcurrentHashMap<String, long[]> map = new ConcurrentHashMap<>(); // id -> {count, expiresAt}
    private final LongSupplier clock;

    public MemoryRateStore() { this(System::currentTimeMillis); }

    public MemoryRateStore(LongSupplier clock) { this.clock = clock; }

    @Override
    public long hit(String key, long windowMs) {
        long now = clock.getAsLong();
        long w = now / windowMs;
        if (map.size() > 50_000) map.entrySet().removeIf(e -> e.getValue()[1] < now);
        return map.compute(key + "#" + w, (k, old) -> {
            if (old == null) return new long[]{1, (w + 1) * windowMs};
            old[0]++;
            return old;
        })[0];
    }

    @Override
    public long count(String key, long windowMs) {
        long[] v = map.get(key + "#" + clock.getAsLong() / windowMs);
        return v == null ? 0 : v[0];
    }

    @Override
    public void clear(String key, long windowMs) { map.remove(key + "#" + clock.getAsLong() / windowMs); }
}
