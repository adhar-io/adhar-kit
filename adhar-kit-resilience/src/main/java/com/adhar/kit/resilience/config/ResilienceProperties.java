package com.adhar.kit.resilience.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar Resilience module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.resilience")
public class ResilienceProperties {

    /**
     * Enable/disable resilience features.
     */
    private boolean enabled = true;

    /**
     * Circuit breaker configurations.
     */
    private Map<String, CircuitBreakerConfig> circuitBreaker = new HashMap<>();

    /**
     * Retry configurations.
     */
    private Map<String, RetryConfig> retry = new HashMap<>();

    /**
     * Rate limiter configurations.
     */
    private Map<String, RateLimiterConfig> rateLimiter = new HashMap<>();

    /**
     * Bulkhead configurations.
     */
    private Map<String, BulkheadConfig> bulkhead = new HashMap<>();

    /**
     * Time limiter configurations.
     */
    private Map<String, TimeLimiterConfig> timeLimiter = new HashMap<>();

    /**
     * Metrics configuration.
     */
    private MetricsConfig metrics = new MetricsConfig();

    @Data
    public static class CircuitBreakerConfig {
        private float failureRateThreshold = 50.0f;
        private float slowCallRateThreshold = 100.0f;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(60);
        private int slidingWindowSize = 100;
        private int minimumNumberOfCalls = 10;
        private int permittedNumberOfCallsInHalfOpenState = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = false;
        private boolean recordExceptions = true;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);
        private Duration maxWaitDuration = Duration.ofSeconds(5);
        private double exponentialBackoffMultiplier = 1.5;
        private boolean enableExponentialBackoff = false;
        private boolean enableRandomizedWait = false;
    }

    @Data
    public static class RateLimiterConfig {
        private int limitForPeriod = 10;
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private Duration timeoutDuration = Duration.ofSeconds(5);
    }

    @Data
    public static class BulkheadConfig {
        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ofMillis(0);
        private int coreThreadPoolSize = 10;
        private int maxThreadPoolSize = 50;
        private int queueCapacity = 100;
        private Duration keepAliveDuration = Duration.ofMillis(20);
    }

    @Data
    public static class TimeLimiterConfig {
        private Duration timeoutDuration = Duration.ofSeconds(1);
        private boolean cancelRunningFuture = true;
    }

    @Data
    public static class MetricsConfig {
        private boolean enabled = true;
        private boolean exportCircuitBreakerMetrics = true;
        private boolean exportRetryMetrics = true;
        private boolean exportRateLimiterMetrics = true;
        private boolean exportBulkheadMetrics = true;
        private boolean exportTimeLimiterMetrics = true;
    }
}

