package com.adhar.kit.dapr.state;

/**
 * Raised by {@link StateRepository#update} when an update could not be applied after
 * exhausting all optimistic-concurrency retries due to repeated ETag conflicts.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class OptimisticConcurrencyException extends RuntimeException {

    public OptimisticConcurrencyException(String message) {
        super(message);
    }
}
