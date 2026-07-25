package com.adhar.kit.dapr.resilience;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for {@link DaprInvocationResilience}.
 *
 * @param maxAttempts       total number of attempts (including the first), minimum 1
 * @param retryBackoff      base backoff between attempts; attempt {@code n} sleeps
 *                          {@code retryBackoff * n} (linear backoff)
 * @param timeout           per-attempt timeout
 * @param failureThreshold  consecutive failures before the circuit breaker opens
 * @param openStateDuration how long the circuit stays open before allowing a trial call
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record ResilienceSettings(
        int maxAttempts,
        Duration retryBackoff,
        Duration timeout,
        int failureThreshold,
        Duration openStateDuration) {

    public ResilienceSettings {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        Objects.requireNonNull(retryBackoff, "retryBackoff must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(openStateDuration, "openStateDuration must not be null");
    }

    /**
     * Sensible defaults: 3 attempts, 100ms linear backoff, 5s timeout, circuit opens after
     * 5 consecutive failures for 30s.
     *
     * @return default settings
     */
    public static ResilienceSettings defaults() {
        return new ResilienceSettings(3, Duration.ofMillis(100), Duration.ofSeconds(5), 5, Duration.ofSeconds(30));
    }
}
