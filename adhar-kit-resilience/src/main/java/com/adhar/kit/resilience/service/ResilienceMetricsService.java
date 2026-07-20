package com.adhar.kit.resilience.service;

import com.adhar.kit.resilience.event.ResilienceEventRecorder;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for collecting and exposing resilience metrics.
 *
 * <p>In addition to the Resilience4j instance metrics, this service exposes an
 * events section (see {@link #getEventMetrics()}) fed by the registered
 * {@link ResilienceEventRecorder}: circuit breaker state transitions, retry
 * attempts, rate limiter rejections, bulkhead rejections and time limiter
 * timeouts.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ResilienceMetricsService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ResilienceEventRecorder eventRecorder;

    /**
     * Creates a metrics service without event metrics support.
     */
    public ResilienceMetricsService(CircuitBreakerRegistry circuitBreakerRegistry,
                                    RetryRegistry retryRegistry,
                                    RateLimiterRegistry rateLimiterRegistry,
                                    BulkheadRegistry bulkheadRegistry) {
        this(circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, bulkheadRegistry, null);
    }

    /**
     * Creates a metrics service that also exposes recorded resilience events.
     *
     * @param eventRecorder recorder fed by {@code ResilienceEventListeners}; may be {@code null}
     */
    public ResilienceMetricsService(CircuitBreakerRegistry circuitBreakerRegistry,
                                    RetryRegistry retryRegistry,
                                    RateLimiterRegistry rateLimiterRegistry,
                                    BulkheadRegistry bulkheadRegistry,
                                    ResilienceEventRecorder eventRecorder) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.eventRecorder = eventRecorder;
    }

    /**
     * Get metrics for all circuit breakers.
     */
    public Map<String, CircuitBreakerMetrics> getCircuitBreakerMetrics() {
        Map<String, CircuitBreakerMetrics> metrics = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            CircuitBreaker.Metrics cbMetrics = cb.getMetrics();
            metrics.put(cb.getName(), CircuitBreakerMetrics.builder()
                    .state(cb.getState().name())
                    .failureRate(cbMetrics.getFailureRate())
                    .slowCallRate(cbMetrics.getSlowCallRate())
                    .numberOfSuccessfulCalls(cbMetrics.getNumberOfSuccessfulCalls())
                    .numberOfFailedCalls(cbMetrics.getNumberOfFailedCalls())
                    .numberOfSlowCalls(cbMetrics.getNumberOfSlowCalls())
                    .numberOfNotPermittedCalls(cbMetrics.getNumberOfNotPermittedCalls())
                    .build());
        });

        return metrics;
    }

    /**
     * Get metrics for a specific circuit breaker.
     */
    public CircuitBreakerMetrics getCircuitBreakerMetrics(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.Metrics cbMetrics = cb.getMetrics();

        return CircuitBreakerMetrics.builder()
                .state(cb.getState().name())
                .failureRate(cbMetrics.getFailureRate())
                .slowCallRate(cbMetrics.getSlowCallRate())
                .numberOfSuccessfulCalls(cbMetrics.getNumberOfSuccessfulCalls())
                .numberOfFailedCalls(cbMetrics.getNumberOfFailedCalls())
                .numberOfSlowCalls(cbMetrics.getNumberOfSlowCalls())
                .numberOfNotPermittedCalls(cbMetrics.getNumberOfNotPermittedCalls())
                .build();
    }

    /**
     * Get metrics for all retries.
     */
    public Map<String, RetryMetrics> getRetryMetrics() {
        Map<String, RetryMetrics> metrics = new HashMap<>();

        retryRegistry.getAllRetries().forEach(retry -> {
            Retry.Metrics retryMetrics = retry.getMetrics();
            metrics.put(retry.getName(), RetryMetrics.builder()
                    .numberOfSuccessfulCallsWithoutRetryAttempt(
                        retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt())
                    .numberOfSuccessfulCallsWithRetryAttempt(
                        retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt())
                    .numberOfFailedCallsWithoutRetryAttempt(
                        retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt())
                    .numberOfFailedCallsWithRetryAttempt(
                        retryMetrics.getNumberOfFailedCallsWithRetryAttempt())
                    .build());
        });

        return metrics;
    }

    /**
     * Get metrics for all rate limiters.
     */
    public Map<String, RateLimiterMetrics> getRateLimiterMetrics() {
        Map<String, RateLimiterMetrics> metrics = new HashMap<>();

        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            RateLimiter.Metrics rlMetrics = rl.getMetrics();
            metrics.put(rl.getName(), RateLimiterMetrics.builder()
                    .availablePermissions(rlMetrics.getAvailablePermissions())
                    .numberOfWaitingThreads(rlMetrics.getNumberOfWaitingThreads())
                    .build());
        });

        return metrics;
    }

    /**
     * Get metrics for all bulkheads.
     */
    public Map<String, BulkheadMetrics> getBulkheadMetrics() {
        Map<String, BulkheadMetrics> metrics = new HashMap<>();

        bulkheadRegistry.getAllBulkheads().forEach(bh -> {
            Bulkhead.Metrics bhMetrics = bh.getMetrics();
            metrics.put(bh.getName(), BulkheadMetrics.builder()
                    .availableConcurrentCalls(bhMetrics.getAvailableConcurrentCalls())
                    .maxAllowedConcurrentCalls(bhMetrics.getMaxAllowedConcurrentCalls())
                    .build());
        });

        return metrics;
    }

    /**
     * Get recorded resilience event counters (events section).
     *
     * <p>Returns an empty map when no {@link ResilienceEventRecorder} is configured
     * (e.g. events disabled via {@code adhar.resilience.events.enabled=false}).</p>
     *
     * @return map with per-category event counters keyed by instance name
     */
    public Map<String, Object> getEventMetrics() {
        if (eventRecorder == null) {
            return Map.of();
        }
        Map<String, Object> events = new LinkedHashMap<>();
        events.put("circuitBreakerTransitions", eventRecorder.getCircuitBreakerTransitionCounts());
        events.put("circuitBreakerLastTransitions", eventRecorder.getCircuitBreakerLastTransitions());
        events.put("retryAttempts", eventRecorder.getRetryAttemptCounts());
        events.put("rateLimiterRejections", eventRecorder.getRateLimiterRejectionCounts());
        events.put("bulkheadRejections", eventRecorder.getBulkheadRejectionCounts());
        events.put("timeLimiterTimeouts", eventRecorder.getTimeLimiterTimeoutCounts());
        return events;
    }

    @lombok.Builder
    @lombok.Data
    public static class CircuitBreakerMetrics {
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfSuccessfulCalls;
        private int numberOfFailedCalls;
        private int numberOfSlowCalls;
        private long numberOfNotPermittedCalls;
    }

    @lombok.Builder
    @lombok.Data
    public static class RetryMetrics {
        private long numberOfSuccessfulCallsWithoutRetryAttempt;
        private long numberOfSuccessfulCallsWithRetryAttempt;
        private long numberOfFailedCallsWithoutRetryAttempt;
        private long numberOfFailedCallsWithRetryAttempt;
    }

    @lombok.Builder
    @lombok.Data
    public static class RateLimiterMetrics {
        private int availablePermissions;
        private int numberOfWaitingThreads;
    }

    @lombok.Builder
    @lombok.Data
    public static class BulkheadMetrics {
        private int availableConcurrentCalls;
        private int maxAllowedConcurrentCalls;
    }
}

