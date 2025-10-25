package com.adhar.kit.resilience.service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for collecting and exposing resilience metrics.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ResilienceMetricsService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

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

