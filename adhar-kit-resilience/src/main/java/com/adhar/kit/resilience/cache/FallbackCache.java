package com.adhar.kit.resilience.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded, TTL-based "last known good" result cache used by the resilience aspect.
 *
 * <p>Successful invocations of methods carrying resilience annotations may store their
 * result here (keyed by method signature and argument hash). When a later invocation
 * fails after all retries are exhausted, or while the circuit breaker is open, the
 * aspect can return the most recent cached result instead of propagating the failure.</p>
 *
 * <p>The cache is bounded by a maximum entry count (least-recently-used eviction) and a
 * per-entry time-to-live. A {@code null} or non-positive TTL disables expiry, keeping
 * entries until they are evicted by the size bound.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class FallbackCache {

    /** Cached value together with its absolute expiry instant (in {@link System#nanoTime()} units). */
    private record Entry(Object value, long expiresAtNanos) {
        boolean isExpired() {
            return expiresAtNanos != Long.MAX_VALUE && System.nanoTime() > expiresAtNanos;
        }
    }

    private final int maxSize;
    private final Duration ttl;
    private final Map<String, Entry> store;

    /**
     * Creates a bounded fallback cache.
     *
     * @param maxSize maximum number of entries retained (LRU eviction); values below 1 are raised to 1
     * @param ttl     per-entry time-to-live; {@code null}, zero or negative disables expiry
     */
    public FallbackCache(int maxSize, Duration ttl) {
        this.maxSize = Math.max(1, maxSize);
        this.ttl = ttl;
        this.store = Collections.synchronizedMap(new LinkedHashMap<String, FallbackCache.Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, FallbackCache.Entry> eldest) {
                return size() > FallbackCache.this.maxSize;
            }
        });
    }

    /**
     * Stores the last successful result for the given key. {@code null} keys and
     * {@code null} values are ignored (a cached {@code null} is indistinguishable from a
     * miss and therefore not retained).
     *
     * @param key   cache key
     * @param value successful result to retain
     */
    public void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        long expiresAt = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? Long.MAX_VALUE
                : System.nanoTime() + ttl.toNanos();
        store.put(key, new Entry(value, expiresAt));
    }

    /**
     * Returns the last known good value for the given key, if present and not expired.
     *
     * @param key cache key
     * @return the cached value, or empty when absent or expired
     */
    public Optional<Object> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.ofNullable(entry.value());
    }

    /** @return current number of (possibly expired but not yet evicted) entries */
    public int size() {
        return store.size();
    }

    /** Removes all cached entries. */
    public void clear() {
        store.clear();
    }
}
