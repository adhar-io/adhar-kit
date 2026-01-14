package com.adhar.kit.resilience.api;

import java.util.function.Supplier;

/**
 * Framework-agnostic Circuit Breaker Service.
 * Works with Spring Boot, Quarkus, and Micronaut.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface CircuitBreakerService {

    /**
     * Execute a supplier with circuit breaker protection.
     *
     * @param name circuit breaker name
     * @param supplier operation to execute
     * @param <T> return type
     * @return result
     */
    <T> T execute(String name, Supplier<T> supplier);

    /**
     * Execute with fallback.
     *
     * @param name circuit breaker name
     * @param supplier operation to execute
     * @param fallback fallback operation
     * @param <T> return type
     * @return result
     */
    <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback);

    /**
     * Get circuit breaker state.
     *
     * @param name circuit breaker name
     * @return state
     */
    State getState(String name);

    /**
     * Get metrics.
     *
     * @param name circuit breaker name
     * @return metrics
     */
    Metrics getMetrics(String name);

    /**
     * Reset circuit breaker.
     *
     * @param name circuit breaker name
     */
    void reset(String name);

    /**
     * Circuit breaker state.
     */
    enum State {
        CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN
    }

    /**
     * Circuit breaker metrics.
     */
    interface Metrics {
        float getFailureRate();
        int getNumberOfSuccessfulCalls();
        int getNumberOfFailedCalls();
        int getNumberOfNotPermittedCalls();
    }
}

