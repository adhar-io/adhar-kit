package com.adhar.kit.messaging.outbox;

/**
 * Lifecycle status of an {@link OutboxEntry}.
 */
public enum OutboxStatus {

    /** Persisted but not yet attempted (or not yet successfully) relayed to the broker. */
    PENDING,

    /** Successfully published to the broker by the {@link OutboxRelay}. */
    PUBLISHED,

    /**
     * The last relay attempt failed but the entry is still eligible for retry
     * on a subsequent relay pass.
     */
    FAILED,

    /**
     * Relaying failed {@code max-attempts} times; the entry is parked and will
     * no longer be retried automatically.
     */
    DEAD
}
