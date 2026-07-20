package com.adhar.kit.resilience.service;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link ResilienceMetricsService}.
 * Uses real in-memory Resilience4j registries; no external infrastructure.
 */
@DisplayName("ResilienceMetricsService Tests")
class ResilienceMetricsServiceTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private ResilienceMetricsService service;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        service = new ResilienceMetricsService(
                circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, bulkheadRegistry);
    }

    @Test
    @DisplayName("getCircuitBreakerMetrics returns empty map when no breakers registered")
    void circuitBreakerMetricsEmpty() {
        assertThat(service.getCircuitBreakerMetrics()).isEmpty();
    }

    @Test
    @DisplayName("getCircuitBreakerMetrics returns all registered breakers")
    void allCircuitBreakerMetrics() {
        circuitBreakerRegistry.circuitBreaker("cb-a").executeSupplier(() -> "ok");
        circuitBreakerRegistry.circuitBreaker("cb-b");

        Map<String, ResilienceMetricsService.CircuitBreakerMetrics> metrics =
                service.getCircuitBreakerMetrics();

        assertThat(metrics).containsKeys("cb-a", "cb-b");
        assertThat(metrics.get("cb-a").getState()).isEqualTo("CLOSED");
        assertThat(metrics.get("cb-a").getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCircuitBreakerMetrics(name) returns metrics for a single breaker")
    void singleCircuitBreakerMetrics() {
        circuitBreakerRegistry.circuitBreaker("cb-x").executeSupplier(() -> "ok");

        ResilienceMetricsService.CircuitBreakerMetrics metrics =
                service.getCircuitBreakerMetrics("cb-x");

        assertThat(metrics.getState()).isEqualTo("CLOSED");
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
        assertThat(metrics.getNumberOfSlowCalls()).isEqualTo(0);
        assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
        assertThat(metrics.getFailureRate()).isEqualTo(-1.0f);
        assertThat(metrics.getSlowCallRate()).isEqualTo(-1.0f);
    }

    @Test
    @DisplayName("getRetryMetrics returns all registered retries")
    void retryMetrics() {
        retryRegistry.retry("retry-a").executeSupplier(() -> "ok");

        Map<String, ResilienceMetricsService.RetryMetrics> metrics = service.getRetryMetrics();

        assertThat(metrics).containsKey("retry-a");
        assertThat(metrics.get("retry-a").getNumberOfSuccessfulCallsWithoutRetryAttempt())
                .isEqualTo(1);
        assertThat(metrics.get("retry-a").getNumberOfSuccessfulCallsWithRetryAttempt())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("getRateLimiterMetrics returns all registered rate limiters")
    void rateLimiterMetrics() {
        rateLimiterRegistry.rateLimiter("rl-a");

        Map<String, ResilienceMetricsService.RateLimiterMetrics> metrics =
                service.getRateLimiterMetrics();

        assertThat(metrics).containsKey("rl-a");
        assertThat(metrics.get("rl-a").getAvailablePermissions()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.get("rl-a").getNumberOfWaitingThreads()).isEqualTo(0);
    }

    @Test
    @DisplayName("getBulkheadMetrics returns all registered bulkheads")
    void bulkheadMetrics() {
        bulkheadRegistry.bulkhead("bh-a");

        Map<String, ResilienceMetricsService.BulkheadMetrics> metrics =
                service.getBulkheadMetrics();

        assertThat(metrics).containsKey("bh-a");
        assertThat(metrics.get("bh-a").getMaxAllowedConcurrentCalls()).isGreaterThan(0);
        assertThat(metrics.get("bh-a").getAvailableConcurrentCalls()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("getEventMetrics returns empty map when no event recorder is configured")
    void eventMetricsWithoutRecorder() {
        assertThat(service.getEventMetrics()).isEmpty();
    }

    @Test
    @DisplayName("getEventMetrics exposes the events section fed by the recorder")
    void eventMetricsWithRecorder() {
        com.adhar.kit.resilience.event.ResilienceEventRecorder recorder =
                new com.adhar.kit.resilience.event.ResilienceEventRecorder();
        recorder.recordCircuitBreakerTransition("cb-ev", "CLOSED", "OPEN");
        recorder.recordRetryAttempt("retry-ev");
        recorder.recordRateLimiterRejection("rl-ev");
        recorder.recordBulkheadRejection("bh-ev");
        recorder.recordTimeLimiterTimeout("tl-ev");

        ResilienceMetricsService serviceWithEvents = new ResilienceMetricsService(
                circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, bulkheadRegistry, recorder);

        Map<String, Object> events = serviceWithEvents.getEventMetrics();

        assertThat(events).containsKeys("circuitBreakerTransitions", "circuitBreakerLastTransitions",
                "retryAttempts", "rateLimiterRejections", "bulkheadRejections", "timeLimiterTimeouts");
        assertThat(section(events, "circuitBreakerTransitions")).containsEntry("cb-ev", 1L);
        assertThat(section(events, "circuitBreakerLastTransitions"))
                .containsEntry("cb-ev", "CLOSED->OPEN");
        assertThat(section(events, "retryAttempts")).containsEntry("retry-ev", 1L);
        assertThat(section(events, "rateLimiterRejections")).containsEntry("rl-ev", 1L);
        assertThat(section(events, "bulkheadRejections")).containsEntry("bh-ev", 1L);
        assertThat(section(events, "timeLimiterTimeouts")).containsEntry("tl-ev", 1L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> events, String key) {
        return (Map<String, Object>) events.get(key);
    }

    @Test
    @DisplayName("Lombok metrics value objects expose builder, accessors and value semantics")
    void metricsValueObjects() {
        ResilienceMetricsService.CircuitBreakerMetrics cb =
                ResilienceMetricsService.CircuitBreakerMetrics.builder()
                        .state("OPEN")
                        .failureRate(50.0f)
                        .slowCallRate(10.0f)
                        .numberOfSuccessfulCalls(5)
                        .numberOfFailedCalls(2)
                        .numberOfSlowCalls(1)
                        .numberOfNotPermittedCalls(3L)
                        .build();
        assertThat(cb.getState()).isEqualTo("OPEN");
        assertThat(cb.getFailureRate()).isEqualTo(50.0f);
        assertThat(cb.getSlowCallRate()).isEqualTo(10.0f);
        assertThat(cb.getNumberOfSlowCalls()).isEqualTo(1);
        assertThat(cb.getNumberOfNotPermittedCalls()).isEqualTo(3L);
        assertThat(cb.toString()).contains("OPEN");

        ResilienceMetricsService.CircuitBreakerMetrics cbSame =
                ResilienceMetricsService.CircuitBreakerMetrics.builder()
                        .state("OPEN")
                        .failureRate(50.0f)
                        .slowCallRate(10.0f)
                        .numberOfSuccessfulCalls(5)
                        .numberOfFailedCalls(2)
                        .numberOfSlowCalls(1)
                        .numberOfNotPermittedCalls(3L)
                        .build();
        assertThat(cb).isEqualTo(cbSame).hasSameHashCodeAs(cbSame);

        ResilienceMetricsService.CircuitBreakerMetrics different =
                ResilienceMetricsService.CircuitBreakerMetrics.builder()
                        .state("CLOSED")
                        .numberOfFailedCalls(9)
                        .build();
        assertThat(different.getState()).isEqualTo("CLOSED");
        assertThat(different.getNumberOfFailedCalls()).isEqualTo(9);
        assertThat(cb).isNotEqualTo(different);

        ResilienceMetricsService.RetryMetrics retry =
                ResilienceMetricsService.RetryMetrics.builder()
                        .numberOfSuccessfulCallsWithoutRetryAttempt(1)
                        .numberOfSuccessfulCallsWithRetryAttempt(2)
                        .numberOfFailedCallsWithoutRetryAttempt(3)
                        .numberOfFailedCallsWithRetryAttempt(4)
                        .build();
        assertThat(retry.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(3);
        assertThat(retry.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(4);
        assertThat(retry.toString()).isNotBlank();
        assertThat(retry).isEqualTo(ResilienceMetricsService.RetryMetrics.builder()
                .numberOfSuccessfulCallsWithoutRetryAttempt(1)
                .numberOfSuccessfulCallsWithRetryAttempt(2)
                .numberOfFailedCallsWithoutRetryAttempt(3)
                .numberOfFailedCallsWithRetryAttempt(4)
                .build());

        ResilienceMetricsService.RateLimiterMetrics rl =
                ResilienceMetricsService.RateLimiterMetrics.builder()
                        .availablePermissions(7)
                        .numberOfWaitingThreads(2)
                        .build();
        assertThat(rl.getAvailablePermissions()).isEqualTo(7);
        assertThat(rl.getNumberOfWaitingThreads()).isEqualTo(2);
        assertThat(rl.toString()).isNotBlank();
        assertThat(rl).isEqualTo(ResilienceMetricsService.RateLimiterMetrics.builder()
                .availablePermissions(7).numberOfWaitingThreads(2).build());

        ResilienceMetricsService.BulkheadMetrics bh =
                ResilienceMetricsService.BulkheadMetrics.builder()
                        .availableConcurrentCalls(4)
                        .maxAllowedConcurrentCalls(10)
                        .build();
        assertThat(bh.getAvailableConcurrentCalls()).isEqualTo(4);
        assertThat(bh.getMaxAllowedConcurrentCalls()).isEqualTo(10);
        assertThat(bh.toString()).isNotBlank();
        assertThat(bh).isEqualTo(ResilienceMetricsService.BulkheadMetrics.builder()
                .availableConcurrentCalls(4).maxAllowedConcurrentCalls(10).build());
    }
}
