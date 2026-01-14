package com.adhar.kit.resilience.micronaut;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

/**
 * Micronaut configuration for resilience.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties("adhar.resilience")
public class MicronautResilienceConfig {

    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    @Data
    public static class CircuitBreakerConfig {
        private float failureRateThreshold = 50.0f;
        private int slidingWindowSize = 100;
        private long waitDurationInOpenState = 60000;
        private int permittedNumberOfCallsInHalfOpenState = 10;
    }
}

