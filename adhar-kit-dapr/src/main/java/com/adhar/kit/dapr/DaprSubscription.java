package com.adhar.kit.dapr;

/**
 * A live subscription (configuration changes, a streaming topic subscription, a programmatic
 * topic handler) that the caller ends with {@link #unsubscribe()}. Closing is idempotent.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public interface DaprSubscription extends AutoCloseable {

    /** An identifier for the subscription (Dapr's id where it assigns one). */
    String id();

    /** Ends the subscription; later calls do nothing. */
    void unsubscribe();

    /** {@code true} until {@link #unsubscribe()} has run. */
    boolean isActive();

    @Override
    default void close() {
        unsubscribe();
    }
}
