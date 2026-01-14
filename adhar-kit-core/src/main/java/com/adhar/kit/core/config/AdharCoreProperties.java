package com.adhar.kit.core.config;

import lombok.Data;

/**
 * Core configuration properties for Adhar Kit.
 *
 * <p>Central configuration for all core features.</p>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   core:
 *     enabled: true
 *     async:
 *       enabled: true
 *       pool-size: 10
 *       queue-capacity: 100
 *     retry:
 *       enabled: true
 *       max-attempts: 3
 *       backoff-multiplier: 2.0
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 5
 *       timeout-duration: 60000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class AdharCoreProperties {

    /**
     * Enable core features.
     */
    private boolean enabled = true;

    /**
     * Async configuration.
     */
    private AsyncConfig async = new AsyncConfig();

    /**
     * Retry configuration.
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Circuit breaker configuration.
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * Cache configuration.
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Validation configuration.
     */
    private ValidationConfig validation = new ValidationConfig();

    /**
     * Async configuration.
     */
    @Data
    public static class AsyncConfig {
        /**
         * Enable async processing.
         */
        private boolean enabled = true;

        /**
         * Thread pool core size.
         */
        private int poolSize = 10;

        /**
         * Thread pool max size.
         */
        private int maxPoolSize = 50;

        /**
         * Queue capacity.
         */
        private int queueCapacity = 100;

        /**
         * Thread name prefix.
         */
        private String threadNamePrefix = "adhar-async-";

        /**
         * Keep alive seconds.
         */
        private int keepAliveSeconds = 60;
    }

    /**
     * Retry configuration.
     */
    @Data
    public static class RetryConfig {
        /**
         * Enable retry.
         */
        private boolean enabled = true;

        /**
         * Maximum retry attempts.
         */
        private int maxAttempts = 3;

        /**
         * Initial delay in milliseconds.
         */
        private long initialDelay = 1000;

        /**
         * Maximum delay in milliseconds.
         */
        private long maxDelay = 30000;

        /**
         * Backoff multiplier.
         */
        private double backoffMultiplier = 2.0;
    }

    /**
     * Circuit breaker configuration.
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * Enable circuit breaker.
         */
        private boolean enabled = true;

        /**
         * Failure threshold.
         */
        private int failureThreshold = 5;

        /**
         * Success threshold to close circuit.
         */
        private int successThreshold = 2;

        /**
         * Timeout duration in milliseconds.
         */
        private long timeoutDuration = 60000;

        /**
         * Half-open wait duration in milliseconds.
         */
        private long halfOpenWaitDuration = 30000;
    }

    /**
     * Cache configuration.
     */
    @Data
    public static class CacheConfig {
        /**
         * Enable caching.
         */
        private boolean enabled = true;

        /**
         * Maximum cache size.
         */
        private long maxSize = 1000;

        /**
         * Time-to-live in seconds.
         */
        private long ttl = 300;

        /**
         * Time-to-idle in seconds.
         */
        private long tti = 180;
    }

    /**
     * Validation configuration.
     */
    @Data
    public static class ValidationConfig {
        /**
         * Enable validation.
         */
        private boolean enabled = true;

        /**
         * Fail fast on validation error.
         */
        private boolean failFast = true;

        /**
         * Validate on startup.
         */
        private boolean validateOnStartup = true;
    }
}

