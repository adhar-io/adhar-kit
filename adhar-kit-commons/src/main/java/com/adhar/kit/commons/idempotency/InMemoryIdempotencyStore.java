package com.adhar.kit.commons.idempotency;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TTL-aware, in-memory {@link IdempotencyStore} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Suitable for single-instance deployments and tests. Expired entries are replaced
 * lazily on access; call {@link #purgeExpired()} (e.g. from a scheduled task) to reclaim
 * memory eagerly. For multi-instance deployments provide a distributed
 * {@link IdempotencyStore} bean instead.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    /** A stored idempotency record. */
    private record Entry(boolean completed, Object result, long expiresAtMillis) {

        boolean isExpired(long nowMillis) {
            return expiresAtMillis <= nowMillis;
        }
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * Creates a store using the system UTC clock.
     */
    public InMemoryIdempotencyStore() {
        this(Clock.systemUTC());
    }

    /**
     * Creates a store using the given clock (useful for deterministic TTL tests).
     *
     * @param clock the clock used for TTL calculations
     */
    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Outcome begin(String key, long ttlSeconds) {
        long now = clock.millis();
        Entry[] existing = new Entry[1];
        entries.compute(key, (k, current) -> {
            if (current != null && !current.isExpired(now)) {
                existing[0] = current;
                return current;
            }
            return new Entry(false, null, now + ttlSeconds * 1000);
        });
        Entry prior = existing[0];
        if (prior == null) {
            return Outcome.acquired();
        }
        return prior.completed() ? Outcome.completed(prior.result()) : Outcome.inProgress();
    }

    @Override
    public void complete(String key, Object result, long ttlSeconds) {
        entries.put(key, new Entry(true, result, clock.millis() + ttlSeconds * 1000));
    }

    @Override
    public void abort(String key) {
        entries.remove(key);
    }

    /**
     * Eagerly removes all expired records.
     */
    public void purgeExpired() {
        long now = clock.millis();
        entries.values().removeIf(entry -> entry.isExpired(now));
    }

    /**
     * Returns the number of records currently held (including expired but not yet purged ones).
     *
     * @return the record count
     */
    public int size() {
        return entries.size();
    }
}
