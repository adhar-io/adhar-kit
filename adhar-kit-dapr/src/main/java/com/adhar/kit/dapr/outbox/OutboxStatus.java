package com.adhar.kit.dapr.outbox;

/**
 * Lifecycle status of an {@link OutboxEvent}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum OutboxStatus {

    /** Persisted, awaiting relay to the pub/sub broker. */
    PENDING,

    /** Successfully published to the broker. */
    PUBLISHED,

    /** Exhausted all publish attempts; parked for manual intervention. */
    DEAD
}
