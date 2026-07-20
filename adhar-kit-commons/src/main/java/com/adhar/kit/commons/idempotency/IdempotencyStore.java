package com.adhar.kit.commons.idempotency;

/**
 * Storage abstraction for idempotency records used by {@link IdempotencyAspect}.
 *
 * <p>Implementations must provide an <b>atomic</b> {@link #begin(String, long)} so that
 * concurrent duplicate calls cannot both acquire the same key. The default implementation
 * is {@link InMemoryIdempotencyStore}; distributed backends (Redis, JDBC, ...) can be
 * plugged in by registering a custom {@code IdempotencyStore} bean.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface IdempotencyStore {

    /**
     * State of an idempotency key as observed by {@link #begin(String, long)}.
     */
    enum Status {
        /** The key was free (or expired) and has been claimed by the caller. */
        ACQUIRED,
        /** Another call with the same key is currently executing. */
        IN_PROGRESS,
        /** A previous call with the same key completed within the TTL window. */
        COMPLETED
    }

    /**
     * Result of {@link #begin(String, long)} - the key status plus the stored
     * result when {@link Status#COMPLETED}.
     *
     * @param status the key status
     * @param result the previously stored result ({@code null} unless {@code COMPLETED})
     */
    record Outcome(Status status, Object result) {

        /** Creates an {@link Status#ACQUIRED} outcome. */
        public static Outcome acquired() {
            return new Outcome(Status.ACQUIRED, null);
        }

        /** Creates an {@link Status#IN_PROGRESS} outcome. */
        public static Outcome inProgress() {
            return new Outcome(Status.IN_PROGRESS, null);
        }

        /** Creates a {@link Status#COMPLETED} outcome carrying the stored result. */
        public static Outcome completed(Object result) {
            return new Outcome(Status.COMPLETED, result);
        }
    }

    /**
     * Atomically claims the key for execution, or reports its current state.
     *
     * @param key        the fully resolved idempotency key
     * @param ttlSeconds time-to-live of the in-progress marker in seconds
     * @return the outcome for the key
     */
    Outcome begin(String key, long ttlSeconds);

    /**
     * Stores the successful result for the key, making it available to duplicate
     * calls until the TTL elapses.
     *
     * @param key        the fully resolved idempotency key
     * @param result     the method result to cache (may be {@code null})
     * @param ttlSeconds time-to-live of the record in seconds
     */
    void complete(String key, Object result, long ttlSeconds);

    /**
     * Releases the key after a failed execution so the operation can be retried.
     *
     * @param key the fully resolved idempotency key
     */
    void abort(String key);
}
