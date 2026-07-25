package com.adhar.kit.dapr.resilience;

/**
 * Raised when a resilience-wrapped Dapr invocation fails (all retries exhausted, or timed out)
 * and no fallback was supplied.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class DaprInvocationException extends RuntimeException {

    public DaprInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
