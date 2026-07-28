package com.adhar.kit.resilience.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Resilience event listener configuration.
     */
    private EventsConfig events = new EventsConfig();

    /**
     * Actuator endpoint configuration.
     */
    private EndpointConfig endpoint = new EndpointConfig();

    /**
     * Circuit breaker health contributor configuration.
     */
    private HealthConfig health = new HealthConfig();

    /**
     * "Last known good" fallback cache configuration.
     */
    private FallbackCacheConfig fallbackCache = new FallbackCacheConfig();

    /**
     * Chaos-engineering hooks configuration (disabled by default).
     */
    private ChaosConfig chaos = new ChaosConfig();

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
    public static class EventsConfig {
        /**
         * Enable/disable Resilience4j event listeners (state transitions, retry
         * attempts, rate limiter/bulkhead rejections, time limiter timeouts).
         */
        private boolean enabled = true;
    }

    @Data
    public static class EndpointConfig {
        /**
         * Enable/disable the {@code resilience} actuator endpoint
         * (requires Spring Boot Actuator on the classpath).
         */
        private boolean enabled = true;
    }

    @Data
    public static class MetricsConfig {
        private boolean enabled = true;
        private boolean exportCircuitBreakerMetrics = true;
        private boolean exportRetryMetrics = true;
        private boolean exportRateLimiterMetrics = true;
        private boolean exportBulkheadMetrics = true;
        private boolean exportTimeLimiterMetrics = true;
        /**
         * Bridge Resilience4j events into the shared {@code PlatformMetrics} (requires
         * {@code adhar-kit-metrics} on the classpath).
         */
        private boolean bridgeToPlatformMetrics = true;
    }

    @Data
    public static class HealthConfig {
        /**
         * Enable/disable the circuit breaker health contributor (requires Spring Boot
         * Actuator's health API on the classpath).
         */
        private boolean enabled = true;
        /**
         * Names of circuit breakers whose OPEN state must mark the application DOWN. When
         * empty, every circuit breaker is treated as critical.
         */
        private List<String> criticalCircuitBreakers = new ArrayList<>();
    }

    @Data
    public static class FallbackCacheConfig {
        /**
         * Enable the fallback cache globally for every annotated method. When {@code false}
         * the cache is still available for methods that opt in via {@code fallbackCache=true}
         * on their {@code @Retry}/{@code @CircuitBreaker} annotation.
         */
        private boolean enabled = false;
        /**
         * Maximum number of cached last-known-good results (LRU eviction).
         */
        private int maxSize = 1000;
        /**
         * Time-to-live for cached results. Zero or negative disables expiry.
         */
        private Duration ttl = Duration.ofMinutes(5);
    }

    @Data
    public static class ChaosConfig {
        /**
         * Master switch for chaos injection. Disabled by default.
         */
        private boolean enabled = false;
        /**
         * Enable latency injection within the configured millisecond range.
         */
        private boolean latencyEnabled = false;
        private long minLatencyMs = 0;
        private long maxLatencyMs = 0;
        /**
         * Enable probabilistic error injection.
         */
        private boolean errorEnabled = false;
        /**
         * Probability in {@code [0,1]} of injecting an error on a matching call.
         */
        private double errorProbability = 0.0;
        /**
         * Method-name matchers. Empty means every annotated method is eligible; otherwise a
         * method matches when its simple name equals, or contains, any listed entry.
         */
        private List<String> includedMethods = new ArrayList<>();
    }
}

