package com.adhar.kit.health.spi;

/**
 * Framework-neutral snapshot of a single circuit breaker's state.
 *
 * <p>Decouples the {@link com.adhar.kit.health.indicator.CircuitBreakerHealthIndicator}
 * from any specific circuit-breaker library.</p>
 *
 * @param name  breaker name
 * @param state normalized breaker state
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record CircuitBreakerStatus(String name, State state) {

    /**
     * Normalized circuit-breaker states.
     */
    public enum State {
        /** Calls flow normally. */
        CLOSED,
        /** Calls are rejected; the breaker has tripped. */
        OPEN,
        /** Trial calls are permitted to probe recovery. */
        HALF_OPEN,
        /** Breaker disabled (always allows calls). */
        DISABLED,
        /** Breaker forced open (always rejects calls). */
        FORCED_OPEN,
        /** State could not be determined. */
        UNKNOWN
    }

    /**
     * Whether this breaker is currently rejecting calls.
     *
     * @return true when {@link State#OPEN} or {@link State#FORCED_OPEN}
     */
    public boolean isOpen() {
        return state == State.OPEN || state == State.FORCED_OPEN;
    }

    /**
     * Whether this breaker is probing recovery.
     *
     * @return true when {@link State#HALF_OPEN}
     */
    public boolean isHalfOpen() {
        return state == State.HALF_OPEN;
    }
}
