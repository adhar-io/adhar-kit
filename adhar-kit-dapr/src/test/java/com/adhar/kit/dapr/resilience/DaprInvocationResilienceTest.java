package com.adhar.kit.dapr.resilience;

import com.adhar.kit.dapr.client.AdharDaprClient;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.HttpExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprInvocationResilience}: retry, timeout, fallback, and circuit
 * breaker behavior, all exercised with fast (millisecond-scale) settings so the suite stays
 * quick and non-flaky.
 */
@DisplayName("DaprInvocationResilience Tests")
class DaprInvocationResilienceTest {

    private static ResilienceSettings fastSettings(int maxAttempts, int failureThreshold, long openMillis) {
        return new ResilienceSettings(maxAttempts, Duration.ofMillis(5), Duration.ofMillis(500),
                failureThreshold, Duration.ofMillis(openMillis));
    }

    @Test
    @DisplayName("execute returns the call's result on first success")
    void executeReturnsResultOnSuccess() {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(3, 5, 1000));

        String result = resilience.execute(() -> "ok", null);

        assertThat(result).isEqualTo("ok");
        assertThat(resilience.isCircuitOpen()).isFalse();
    }

    @Test
    @DisplayName("execute retries on failure until success, up to maxAttempts")
    void executeRetriesUntilSuccess() {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(4, 10, 1000));
        AtomicInteger attempts = new AtomicInteger();

        String result = resilience.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("flaky");
            }
            return "recovered";
        }, null);

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute throws after exhausting all retry attempts with no fallback")
    void executeThrowsAfterExhaustingRetries() {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(3, 10, 1000));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> resilience.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }, null)).isInstanceOf(IllegalStateException.class).hasMessage("always fails");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("execute invokes the fallback after exhausting all retry attempts")
    void executeInvokesFallbackAfterExhaustingRetries() {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(2, 10, 1000));

        String result = resilience.execute(() -> {
            throw new IllegalStateException("boom");
        }, cause -> "fallback:" + cause.getMessage());

        assertThat(result).isEqualTo("fallback:boom");
    }

    @Test
    @DisplayName("execute times out a call that exceeds the configured duration")
    void executeTimesOutSlowCall() {
        ResilienceSettings settings = new ResilienceSettings(1, Duration.ofMillis(5), Duration.ofMillis(50), 10, Duration.ofSeconds(30));
        DaprInvocationResilience resilience = new DaprInvocationResilience(settings);

        String result = resilience.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "too-late";
        }, cause -> "timed-out:" + cause.getClass().getSimpleName());

        assertThat(result).isEqualTo("timed-out:DaprInvocationException");
    }

    @Test
    @DisplayName("circuit breaker opens after consecutive failures and rejects further calls")
    void circuitBreakerOpensAfterConsecutiveFailures() {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(1, 2, 5000));
        AtomicInteger callCount = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resilience.execute(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("fail-" + callCount.get());
            }, null)).isInstanceOf(RuntimeException.class);
        }

        assertThat(resilience.isCircuitOpen()).isTrue();
        int callsBeforeOpen = callCount.get();

        // Circuit is open: the supplier must not be invoked, and a CircuitBreakerOpenException
        // (or the fallback) is returned immediately.
        assertThatThrownBy(() -> resilience.execute(() -> {
            callCount.incrementAndGet();
            return "unreachable";
        }, null)).isInstanceOf(CircuitBreakerOpenException.class);

        assertThat(callCount.get()).isEqualTo(callsBeforeOpen);
    }

    @Test
    @DisplayName("circuit breaker transitions to half-open then closes on a successful trial call")
    void circuitBreakerHalfOpenClosesOnSuccess() throws InterruptedException {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(1, 1, 20));

        assertThatThrownBy(() -> resilience.execute(() -> {
            throw new RuntimeException("fail");
        }, null)).isInstanceOf(RuntimeException.class);
        assertThat(resilience.isCircuitOpen()).isTrue();

        Thread.sleep(50);

        String result = resilience.execute(() -> "recovered", null);

        assertThat(result).isEqualTo("recovered");
        assertThat(resilience.isCircuitOpen()).isFalse();
    }

    @Test
    @DisplayName("circuit breaker re-opens when the half-open trial call fails")
    void circuitBreakerHalfOpenReopensOnFailure() throws InterruptedException {
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(1, 1, 20));

        assertThatThrownBy(() -> resilience.execute(() -> {
            throw new RuntimeException("fail-1");
        }, null)).isInstanceOf(RuntimeException.class);
        assertThat(resilience.isCircuitOpen()).isTrue();

        Thread.sleep(50);

        assertThatThrownBy(() -> resilience.execute(() -> {
            throw new RuntimeException("fail-2");
        }, null)).isInstanceOf(RuntimeException.class);

        assertThat(resilience.isCircuitOpen()).isTrue();
    }

    @Test
    @DisplayName("invokeService wraps AdharDaprClient.invokeService with resilience applied")
    void invokeServiceWrapsAdharDaprClient() {
        DaprClient daprClient = mock(DaprClient.class);
        when(daprClient.invokeMethod(eq("app"), eq("/endpoint"), any(), any(HttpExtension.class), eq(String.class)))
                .thenReturn(Mono.just("response"));
        AdharDaprClient adharDaprClient = new AdharDaprClient(daprClient);
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(3, 5, 1000));

        String result = resilience.invokeService(adharDaprClient, "app", "POST", "/endpoint", "request", String.class);

        assertThat(result).isEqualTo("response");
        verify(daprClient, times(1)).invokeMethod(eq("app"), eq("/endpoint"), any(), any(HttpExtension.class), eq(String.class));
    }

    @Test
    @DisplayName("invokeService with fallback recovers from AdharDaprClient failures")
    void invokeServiceWithFallbackRecoversFromFailure() {
        DaprClient daprClient = mock(DaprClient.class);
        when(daprClient.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
                .thenThrow(new RuntimeException("boom"));
        AdharDaprClient adharDaprClient = new AdharDaprClient(daprClient);
        DaprInvocationResilience resilience = new DaprInvocationResilience(fastSettings(2, 5, 1000));

        String result = resilience.invokeService(adharDaprClient, "app", "POST", "/endpoint", "request", String.class,
                cause -> "fallback-response");

        assertThat(result).isEqualTo("fallback-response");
    }

    @Test
    @DisplayName("default constructor uses ResilienceSettings.defaults()")
    void defaultConstructorUsesDefaultSettings() {
        DaprInvocationResilience resilience = new DaprInvocationResilience();

        assertThat(resilience.execute(() -> "ok", null)).isEqualTo("ok");
    }

    @Test
    @DisplayName("ResilienceSettings validates its arguments")
    void resilienceSettingsValidatesArguments() {
        assertThatThrownBy(() -> new ResilienceSettings(0, Duration.ofMillis(1), Duration.ofMillis(1), 1, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResilienceSettings(1, Duration.ofMillis(1), Duration.ofMillis(1), 0, Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResilienceSettings(1, null, Duration.ofMillis(1), 1, Duration.ofMillis(1)))
                .isInstanceOf(NullPointerException.class);

        ResilienceSettings defaults = ResilienceSettings.defaults();
        assertThat(defaults.maxAttempts()).isEqualTo(3);
        assertThat(defaults.failureThreshold()).isEqualTo(5);
    }
}
