package com.adhar.kit.health.spi;

import java.util.List;

/**
 * SPI for discovering circuit breakers and their current state.
 *
 * <p>Lets {@link com.adhar.kit.health.indicator.CircuitBreakerHealthIndicator} report on
 * open breakers <b>without</b> a hard dependency on any circuit-breaker library. Provide
 * one implementation per source of breakers (e.g. one for resilience4j); the indicator
 * aggregates across all discovered providers.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@FunctionalInterface
public interface CircuitBreakerStateProvider {

    /**
     * Returns the current state of every known circuit breaker.
     *
     * @return breaker states (never {@code null}; empty when none are registered)
     */
    List<CircuitBreakerStatus> states();
}
