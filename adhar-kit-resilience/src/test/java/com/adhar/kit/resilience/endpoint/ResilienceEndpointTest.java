package com.adhar.kit.resilience.endpoint;

import com.adhar.kit.resilience.event.ResilienceEventRecorder;
import com.adhar.kit.resilience.service.ResilienceMetricsService;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ResilienceEndpoint} actuator endpoint.
 */
@DisplayName("ResilienceEndpoint Tests")
class ResilienceEndpointTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private ResilienceEventRecorder recorder;
    private ResilienceEndpoint endpoint;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
        recorder = new ResilienceEventRecorder();
        ResilienceMetricsService metricsService = new ResilienceMetricsService(
                circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, bulkheadRegistry, recorder);
        endpoint = new ResilienceEndpoint(metricsService, circuitBreakerRegistry);
    }

    @Test
    @DisplayName("read operation exposes all metric sections including events")
    void readOperationExposesAllSections() {
        circuitBreakerRegistry.circuitBreaker("ep-cb").executeSupplier(() -> "ok");
        recorder.recordRetryAttempt("ep-retry");

        Map<String, Object> snapshot = endpoint.resilience();

        assertThat(snapshot).containsKeys(
                "circuitBreakers", "retries", "rateLimiters", "bulkheads", "events");

        @SuppressWarnings("unchecked")
        Map<String, Object> circuitBreakers = (Map<String, Object>) snapshot.get("circuitBreakers");
        assertThat(circuitBreakers).containsKey("ep-cb");

        @SuppressWarnings("unchecked")
        Map<String, Object> events = (Map<String, Object>) snapshot.get("events");
        assertThat(events).containsKeys("circuitBreakerTransitions", "retryAttempts",
                "rateLimiterRejections", "bulkheadRejections", "timeLimiterTimeouts");
        @SuppressWarnings("unchecked")
        Map<String, Long> retryAttempts = (Map<String, Long>) events.get("retryAttempts");
        assertThat(retryAttempts).containsEntry("ep-retry", 1L);
    }

    @Test
    @DisplayName("write operation resets an open circuit breaker back to CLOSED")
    void resetCircuitBreaker() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ep-reset");
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Map<String, String> result = endpoint.resetCircuitBreaker("ep-reset");

        assertThat(result)
                .containsEntry("name", "ep-reset")
                .containsEntry("result", "reset")
                .containsEntry("state", "CLOSED");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("write operation reports unknown circuit breakers without creating them")
    void resetUnknownCircuitBreaker() {
        Map<String, String> result = endpoint.resetCircuitBreaker("does-not-exist");

        assertThat(result)
                .containsEntry("name", "does-not-exist")
                .containsEntry("result", "not-found");
        assertThat(circuitBreakerRegistry.find("does-not-exist")).isEmpty();
    }
}
