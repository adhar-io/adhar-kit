package com.adhar.kit.dapr.state;

/**
 * Optimistic-concurrency mode for writes, as Dapr defines it. {@link #LAST_WRITE} is Dapr's default;
 * {@link #FIRST_WRITE} makes a write carrying an ETag fail when the stored ETag has moved on.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum Concurrency {

    /** A write with a stale ETag is rejected (surfaced as a conflict). */
    FIRST_WRITE,

    /** The last write wins. */
    LAST_WRITE;

    io.dapr.client.domain.StateOptions.Concurrency toSdk() {
        return this == FIRST_WRITE
                ? io.dapr.client.domain.StateOptions.Concurrency.FIRST_WRITE
                : io.dapr.client.domain.StateOptions.Concurrency.LAST_WRITE;
    }
}
