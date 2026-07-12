package com.adhar.kit.resilience.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link SpringCircuitBreakerAdapter}.
 * Uses a real (in-memory) Resilience4j registry; no external infrastructure.
 */
@DisplayName("SpringCircuitBreakerAdapter Tests")
class SpringCircuitBreakerAdapterTest {

    private CircuitBreakerRegistry registry;
    private SpringCircuitBreakerAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        adapter = new SpringCircuitBreakerAdapter(registry);
    }

    @Test
    @DisplayName("getSupportedFramework returns SPRING_BOOT")
    void getSupportedFramework() {
        assertThat(adapter.getSupportedFramework()).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    @DisplayName("getService returns the adapter itself")
    void getService() {
        assertThat(adapter.getService()).isSameAs(adapter);
    }

    @Test
    @DisplayName("execute returns the supplier result")
    void executeReturnsResult() {
        String result = adapter.execute("svc", () -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("execute propagates supplier exceptions")
    void executePropagatesExceptions() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> adapter.execute("svc", () -> {
                    throw new IllegalStateException("boom");
                }));
    }

    @Test
    @DisplayName("executeWithFallback returns supplier result on success")
    void executeWithFallbackSuccess() {
        String result = adapter.executeWithFallback("svc", () -> "primary", () -> "fallback");
        assertThat(result).isEqualTo("primary");
    }

    @Test
    @DisplayName("executeWithFallback returns fallback result on failure")
    void executeWithFallbackOnFailure() {
        String result = adapter.executeWithFallback("svc",
                () -> {
                    throw new RuntimeException("failure");
                },
                () -> "fallback");
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    @DisplayName("getState maps every Resilience4j state")
    void getStateMapsAllStates() {
        CircuitBreaker cb = registry.circuitBreaker("svc");

        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.CLOSED);

        cb.transitionToOpenState();
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.OPEN);

        cb.transitionToHalfOpenState();
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.HALF_OPEN);

        cb.transitionToDisabledState();
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.DISABLED);

        cb.transitionToForcedOpenState();
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.FORCED_OPEN);
    }

    @Test
    @DisplayName("getMetrics exposes underlying Resilience4j metrics")
    void getMetricsReflectsCalls() {
        adapter.execute("svc", () -> "ok");
        try {
            adapter.execute("svc", () -> {
                throw new RuntimeException("fail");
            });
        } catch (RuntimeException ignored) {
            // expected
        }

        CircuitBreakerService.Metrics metrics = adapter.getMetrics("svc");
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(metrics.getNumberOfNotPermittedCalls()).isEqualTo(0);
        assertThat(metrics.getFailureRate()).isEqualTo(-1.0f); // below sliding window minimum
    }

    @Test
    @DisplayName("reset returns the circuit breaker to CLOSED")
    void resetReturnsToClosed() {
        CircuitBreaker cb = registry.circuitBreaker("svc");
        cb.transitionToOpenState();
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.OPEN);

        adapter.reset("svc");
        assertThat(adapter.getState("svc")).isEqualTo(CircuitBreakerService.State.CLOSED);
    }
}
