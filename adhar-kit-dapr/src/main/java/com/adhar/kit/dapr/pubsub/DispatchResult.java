package com.adhar.kit.dapr.pubsub;

/**
 * Result of dispatching a single CloudEvent to a subscribed handler.
 *
 * @param status the dispatch outcome
 * @param cause  the failure cause, or {@code null} on success
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record DispatchResult(DispatchStatus status, Throwable cause) {

    public static DispatchResult success() {
        return new DispatchResult(DispatchStatus.SUCCESS, null);
    }

    public static DispatchResult retry(Throwable cause) {
        return new DispatchResult(DispatchStatus.RETRY, cause);
    }

    public static DispatchResult drop(Throwable cause) {
        return new DispatchResult(DispatchStatus.DROP, cause);
    }
}
