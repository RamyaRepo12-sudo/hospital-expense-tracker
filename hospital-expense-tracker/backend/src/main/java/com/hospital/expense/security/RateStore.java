package com.hospital.expense.security;

/** Fixed-window counters. The MongoDB implementation is shared by every running instance. */
public interface RateStore {
    /** Adds one to the counter for this key in the current window and returns the new count. */
    long hit(String key, long windowMs);

    /** Current count without changing it. */
    long count(String key, long windowMs);

    void clear(String key, long windowMs);
}
