package com.adhar.kit.dapr.resilience;

/**
 * Raised when {@link DaprInvocationResilience} rejects a call because its internal circuit
 * breaker is open.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
