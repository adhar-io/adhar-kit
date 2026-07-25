package com.adhar.kit.notification;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe in-memory TTL store for idempotency keys.
 * <p>
 * Keys are remembered for the configured time-to-live
 * ({@code adhar.notification.idempotency.ttl-ms}); expired keys are purged lazily on
 * each registration. Suitable for single-instance deployments; distributed setups
 * should provide their own {@link NotificationIdempotencyStore} implementation.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryNotificationIdempotencyStore implements NotificationIdempotencyStore {

    private final ConcurrentHashMap<String, Long> expiries = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final LongSupplier clock;

    /**
     * Creates a store with the given key time-to-live using the system clock.
     *
     * @param ttlMs how long registered keys are remembered, in milliseconds
     */
    public InMemoryNotificationIdempotencyStore(long ttlMs) {
        this(ttlMs, System::currentTimeMillis);
    }

    /**
     * Creates a store with an explicit clock, primarily for testing.
     *
     * @param ttlMs how long registered keys are remembered, in milliseconds
     * @param clock supplier of the current time in milliseconds
     */
    public InMemoryNotificationIdempotencyStore(long ttlMs, LongSupplier clock) {
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    @Override
    public boolean register(String key) {
        long now = clock.getAsLong();
        purgeExpired(now);
        boolean[] first = {false};
        expiries.compute(key, (k, expiry) -> {
            if (expiry == null || expiry <= now) {
                first[0] = true;
                return now + ttlMs;
            }
            return expiry;
        });
        return first[0];
    }

    /**
     * Returns the number of non-purged keys currently tracked.
     *
     * @return the store size
     */
    public int size() {
        return expiries.size();
    }

    /**
     * Removes all registered keys.
     */
    public void clear() {
        expiries.clear();
    }

    private void purgeExpired(long now) {
        expiries.values().removeIf(expiry -> expiry <= now);
    }
}
