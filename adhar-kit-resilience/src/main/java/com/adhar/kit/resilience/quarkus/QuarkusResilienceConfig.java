package com.adhar.kit.resilience.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Quarkus configuration for resilience.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@ConfigMapping(prefix = "adhar.resilience")
public interface QuarkusResilienceConfig {

    /**
     * Circuit breaker configuration.
     */
    CircuitBreakerConfig circuitBreaker();

    interface CircuitBreakerConfig {
        @WithDefault("50")
        float failureRateThreshold();

        @WithDefault("100")
        int slidingWindowSize();

        @WithDefault("PT60S")
        Duration waitDurationInOpenState();

        @WithDefault("10")
        int permittedNumberOfCallsInHalfOpenState();
    }
}

