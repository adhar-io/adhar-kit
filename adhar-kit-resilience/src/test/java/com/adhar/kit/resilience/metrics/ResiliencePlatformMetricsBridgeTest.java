package com.adhar.kit.resilience.metrics;

import com.adhar.kit.metrics.auto.PlatformMetrics;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ResiliencePlatformMetricsBridge}. Uses real in-memory
 * Resilience4j registries and a mocked {@link PlatformMetrics} to verify event
 * translation. The dependency on {@code adhar-kit-metrics} is optional but present on the
 * test classpath, so {@link PlatformMetrics} can be mocked directly.
 */
@DisplayName("ResiliencePlatformMetricsBridge Tests")
class ResiliencePlatformMetricsBridgeTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private TimeLimiterRegistry timeLimiterRegistry;
    private PlatformMetrics platformMetrics;
    private ResiliencePlatformMetricsBridge bridge;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        platformMetrics = mock(PlatformMetrics.class);
        bridge = new ResiliencePlatformMetricsBridge(circuitBreakerRegistry, retryRegistry,
                rateLimiterRegistry, bulkheadRegistry, timeLimiterRegistry, platformMetrics);
        bridge.register();
    }

    @Test
    @DisplayName("circuit breaker state transitions are recorded with the target state name")
    void circuitBreakerStateTransition() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("cb-1");

        cb.transitionToOpenState();

        verify(platformMetrics).recordCircuitBreakerState("cb-1", "OPEN");
    }

    @Test
    @DisplayName("a retry (wait) event is recorded as an unsuccessful attempt")
    void retryAttemptRecorded() {
        Retry retry = retryRegistry.retry("retry-1", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());

        Throwable thrown = catchThrowable(() -> retry.executeSupplier(() -> {
            throw new IllegalStateException("always");
        }));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(platformMetrics, atLeastOnce()).recordRetryAttempt(eq("retry-1"), anyInt(), eq(false));
        // never succeeded -> no successful-attempt record
        verify(platformMetrics, never()).recordRetryAttempt(eq("retry-1"), anyInt(), eq(true));
    }

    @Test
    @DisplayName("a success after retries is recorded as a successful attempt")
    void retrySuccessAfterRetryRecorded() {
        Retry retry = retryRegistry.retry("retry-2", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        AtomicInteger calls = new AtomicInteger();

        String result = retry.executeSupplier(() -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("first fails");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        verify(platformMetrics, atLeastOnce()).recordRetryAttempt(eq("retry-2"), anyInt(), eq(false));
        verify(platformMetrics, atLeastOnce()).recordRetryAttempt(eq("retry-2"), anyInt(), eq(true));
    }

    @Test
    @DisplayName("a first-try success is not recorded as a retry attempt")
    void retryFirstTrySuccessNotRecorded() {
        Retry retry = retryRegistry.retry("retry-3");

        assertThat(retry.executeSupplier(() -> "ok")).isEqualTo("ok");

        verify(platformMetrics, never()).recordRetryAttempt(eq("retry-3"), anyInt(), eq(true));
    }

    @Test
    @DisplayName("rate limiter rejections are recorded")
    void rateLimiterRejectionRecorded() {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("rl-1", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());

        assertThat(limiter.executeSupplier(() -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> limiter.executeSupplier(() -> "again"))
                .isInstanceOf(RequestNotPermitted.class);

        verify(platformMetrics).recordRateLimitReject("rl-1");
    }

    @Test
    @DisplayName("bulkhead rejections are recorded as a zero-duration failed operation latency")
    void bulkheadRejectionRecorded() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("bh-1", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());

        assertThat(bulkhead.tryAcquirePermission()).isTrue(); // occupy the only slot
        assertThatThrownBy(() -> bulkhead.executeSupplier(() -> "full"))
                .isInstanceOf(BulkheadFullException.class);

        verify(platformMetrics).recordOperationLatency("resilience", "bulkhead.bh-1.rejected", 0, false);
    }

    @Test
    @DisplayName("time limiter timeouts are recorded as a zero-duration failed operation latency")
    void timeLimiterTimeoutRecorded() {
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("tl-1", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(50))
                .build());

        Throwable thrown = catchThrowable(() ->
                timeLimiter.executeFutureSupplier(CompletableFuture::new));

        assertThat(thrown).isInstanceOf(java.util.concurrent.TimeoutException.class);
        verify(platformMetrics).recordOperationLatency("resilience", "timeLimiter.tl-1.timeout", 0, false);
    }

    @Test
    @DisplayName("instances created after register() are attached via entry-added events")
    void dynamicallyAddedInstancesAreCovered() {
        // created AFTER register()
        CircuitBreaker late = circuitBreakerRegistry.circuitBreaker("cb-late");
        late.transitionToForcedOpenState();

        verify(platformMetrics).recordCircuitBreakerState("cb-late", "FORCED_OPEN");
    }

    @Test
    @DisplayName("register() is idempotent (a second call attaches no duplicate listeners)")
    void registerIsIdempotent() {
        bridge.register(); // second call must be a no-op

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("cb-idem");
        cb.transitionToOpenState();

        // exactly one record, not two
        verify(platformMetrics).recordCircuitBreakerState("cb-idem", "OPEN");
    }
}
