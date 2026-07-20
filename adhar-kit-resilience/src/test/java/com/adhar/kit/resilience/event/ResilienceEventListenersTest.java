package com.adhar.kit.resilience.event;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Unit tests for {@link ResilienceEventListeners} and {@link ResilienceEventRecorder}
 * using real in-memory Resilience4j registries.
 */
@DisplayName("ResilienceEventListeners Tests")
class ResilienceEventListenersTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private TimeLimiterRegistry timeLimiterRegistry;
    private ResilienceEventRecorder recorder;
    private ResilienceEventListeners listeners;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        recorder = new ResilienceEventRecorder();
        listeners = new ResilienceEventListeners(circuitBreakerRegistry, retryRegistry,
                rateLimiterRegistry, bulkheadRegistry, timeLimiterRegistry, recorder);
        listeners.register();
    }

    @Test
    @DisplayName("circuit breaker state transitions are counted with the last transition")
    void circuitBreakerTransitions() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ev-cb");

        cb.transitionToOpenState();

        assertThat(recorder.getCircuitBreakerTransitionCounts()).containsEntry("ev-cb", 1L);
        assertThat(recorder.getCircuitBreakerLastTransitions()).containsEntry("ev-cb", "CLOSED->OPEN");

        cb.transitionToHalfOpenState();
        assertThat(recorder.getCircuitBreakerTransitionCounts()).containsEntry("ev-cb", 2L);
        assertThat(recorder.getCircuitBreakerLastTransitions()).containsEntry("ev-cb", "OPEN->HALF_OPEN");
    }

    @Test
    @DisplayName("retry attempts are counted per retry instance")
    void retryAttempts() {
        var retry = retryRegistry.retry("ev-retry", RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());

        Throwable thrown = catchThrowable(() -> retry.executeSupplier(() -> {
            throw new IllegalStateException("always");
        }));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        // maxAttempts=3 -> 2 retry (wait) events before the final failure
        assertThat(recorder.getRetryAttemptCounts()).containsEntry("ev-retry", 2L);
    }

    @Test
    @DisplayName("rate limiter rejections are counted")
    void rateLimiterRejections() {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("ev-rl", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());

        assertThat(limiter.executeSupplier(() -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> limiter.executeSupplier(() -> "again"))
                .isInstanceOf(RequestNotPermitted.class);

        assertThat(recorder.getRateLimiterRejectionCounts()).containsEntry("ev-rl", 1L);
    }

    @Test
    @DisplayName("bulkhead call rejections are counted")
    void bulkheadRejections() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("ev-bh", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());

        assertThat(bulkhead.tryAcquirePermission()).isTrue(); // occupy the only slot
        assertThatThrownBy(() -> bulkhead.executeSupplier(() -> "full"))
                .isInstanceOf(BulkheadFullException.class);

        assertThat(recorder.getBulkheadRejectionCounts()).containsEntry("ev-bh", 1L);
    }

    @Test
    @DisplayName("time limiter timeouts are counted")
    void timeLimiterTimeouts() {
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("ev-tl", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(50))
                .build());

        Throwable thrown = catchThrowable(() ->
                timeLimiter.executeFutureSupplier(CompletableFuture::new));

        assertThat(thrown).isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(recorder.getTimeLimiterTimeoutCounts()).containsEntry("ev-tl", 1L);
    }

    @Test
    @DisplayName("instances created after register() are attached via entry-added events")
    void dynamicallyAddedInstancesAreCovered() {
        // created AFTER register(): still observed
        CircuitBreaker late = circuitBreakerRegistry.circuitBreaker("ev-late-cb");
        late.transitionToForcedOpenState();

        assertThat(recorder.getCircuitBreakerTransitionCounts()).containsKey("ev-late-cb");
    }

    @Test
    @DisplayName("register() is idempotent (no duplicate counting)")
    void registerIsIdempotent() {
        listeners.register(); // second call must be a no-op

        circuitBreakerRegistry.circuitBreaker("ev-idem").transitionToOpenState();

        assertThat(recorder.getCircuitBreakerTransitionCounts()).containsEntry("ev-idem", 1L);
    }

    @Test
    @DisplayName("recorder.reset() clears all counters")
    void recorderReset() {
        circuitBreakerRegistry.circuitBreaker("ev-reset").transitionToOpenState();
        assertThat(recorder.getCircuitBreakerTransitionCounts()).isNotEmpty();

        recorder.reset();

        assertThat(recorder.getCircuitBreakerTransitionCounts()).isEmpty();
        assertThat(recorder.getCircuitBreakerLastTransitions()).isEmpty();
        assertThat(recorder.getRetryAttemptCounts()).isEmpty();
        assertThat(recorder.getRateLimiterRejectionCounts()).isEmpty();
        assertThat(recorder.getBulkheadRejectionCounts()).isEmpty();
        assertThat(recorder.getTimeLimiterTimeoutCounts()).isEmpty();
    }
}
