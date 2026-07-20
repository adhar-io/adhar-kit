package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.Bulkhead;
import com.adhar.kit.resilience.annotation.CircuitBreaker;
import com.adhar.kit.resilience.annotation.RateLimit;
import com.adhar.kit.resilience.annotation.Retry;
import com.adhar.kit.resilience.annotation.TimeLimiter;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the unified {@link ResilienceAspect#applyResilience(ProceedingJoinPoint)}
 * advice: annotation attribute honoring, decorator composition, async support and
 * fallback resolution.
 */
@DisplayName("Unified ResilienceAspect Tests")
class UnifiedResilienceAspectTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private TimeLimiterRegistry timeLimiterRegistry;
    private ResilienceAspect aspect;
    private final Fixture fixture = new Fixture();
    private final AtomicInteger attempts = new AtomicInteger();

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        aspect = new ResilienceAspect(circuitBreakerRegistry, retryRegistry,
                rateLimiterRegistry, bulkheadRegistry, timeLimiterRegistry);
        attempts.set(0);
    }

    // ----- helpers ---------------------------------------------------------

    private ProceedingJoinPoint joinPoint(String methodName, Callable<Object> behavior) throws Throwable {
        Method method = null;
        for (Method candidate : Fixture.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                method = candidate;
                break;
            }
        }
        assertThat(method).as("fixture method %s", methodName).isNotNull();

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(fixture);
        when(jp.proceed()).thenAnswer(invocation -> behavior.call());
        return jp;
    }

    // ----- attribute honoring ---------------------------------------------

    @Test
    @DisplayName("@Retry(maxAttempts=5) actually attempts 5 times")
    void retryAttemptsHonored() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("retryFive", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        });

        assertThatThrownBy(() -> aspect.applyResilience(jp)).isInstanceOf(IllegalStateException.class);

        assertThat(attempts.get()).isEqualTo(5);
        assertThat(retryRegistry.retry("u-retry").getRetryConfig().getMaxAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("@RateLimit(limitForPeriod=1) rejects the second call in the period")
    void rateLimitAttributesHonored() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("limited", () -> "ok");

        assertThat(aspect.applyResilience(jp)).isEqualTo("ok");
        assertThatThrownBy(() -> aspect.applyResilience(jp)).isInstanceOf(RequestNotPermitted.class);

        assertThat(rateLimiterRegistry.rateLimiter("u-rl").getRateLimiterConfig().getLimitForPeriod())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("@Bulkhead attributes are applied to the created instance")
    void bulkheadAttributesHonored() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("bulk", () -> "ok");

        assertThat(aspect.applyResilience(jp)).isEqualTo("ok");

        var config = bulkheadRegistry.bulkhead("u-bh").getBulkheadConfig();
        assertThat(config.getMaxConcurrentCalls()).isEqualTo(3);
        assertThat(config.getMaxWaitDuration()).isEqualTo(Duration.ofMillis(10));
    }

    @Test
    @DisplayName("@CircuitBreaker attributes are applied to the created instance")
    void circuitBreakerAttributesHonored() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("guardedByCircuitBreaker", () -> "ok");

        assertThat(aspect.applyResilience(jp)).isEqualTo("ok");

        var config = circuitBreakerRegistry.circuitBreaker("u-cb-only").getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(7);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(2);
        assertThat(config.getFailureRateThreshold()).isEqualTo(25f);
    }

    @Test
    @DisplayName("unset (sentinel) attributes fall back to the registry default config")
    void sentinelUsesRegistryDefaults() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("plainDefaults", () -> "ok");

        assertThat(aspect.applyResilience(jp)).isEqualTo("ok");

        assertThat(retryRegistry.retry("u-default-retry").getRetryConfig().getMaxAttempts())
                .isEqualTo(retryRegistry.getDefaultConfig().getMaxAttempts());
    }

    @Test
    @DisplayName("named instances are cached and reused across invocations")
    void instancesAreCached() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("plainDefaults", () -> "ok");
        aspect.applyResilience(jp);
        var first = retryRegistry.retry("u-default-retry");
        aspect.applyResilience(jp);
        assertThat(retryRegistry.retry("u-default-retry")).isSameAs(first);
        assertThat(retryRegistry.getAllRetries()).hasSize(1);
    }

    // ----- composition -----------------------------------------------------

    @Test
    @DisplayName("stacked @Retry + @CircuitBreaker compose (circuit breaker records every attempt)")
    void retryAndCircuitBreakerCompose() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("retryWithCircuitBreaker", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("down");
        });

        assertThatThrownBy(() -> aspect.applyResilience(jp)).isInstanceOf(IllegalStateException.class);

        // retry is outermost: 3 attempts; the inner circuit breaker saw all 3 failures
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(circuitBreakerRegistry.circuitBreaker("u-cb").getMetrics().getNumberOfFailedCalls())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("composed chain uses the first configured fallback (retry runs before falling back)")
    void composedFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("composedFallback", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        Object result = aspect.applyResilience(jp);

        assertThat(result).isEqualTo("fb:boom");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("class-level annotations apply to methods without method-level annotations")
    void classLevelAnnotation() throws Throwable {
        ClassLevelFixture target = new ClassLevelFixture();
        Method method = ClassLevelFixture.class.getDeclaredMethod("work");
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("work");
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(target);
        when(jp.proceed()).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        });

        assertThatThrownBy(() -> aspect.applyResilience(jp)).isInstanceOf(IllegalStateException.class);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("methods without any resilience annotation proceed untouched")
    void noAnnotationsProceeds() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("unannotated", () -> "plain");
        assertThat(aspect.applyResilience(jp)).isEqualTo("plain");
    }

    // ----- sync time limiter ----------------------------------------------

    @Test
    @DisplayName("sync @TimeLimiter times out a slow method")
    void syncTimeLimiterTimesOut() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("slowSync", () -> {
            Thread.sleep(2_000);
            return "late";
        });

        assertThatThrownBy(() -> aspect.applyResilience(jp)).isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("sync @TimeLimiter returns the value of a fast method")
    void syncTimeLimiterFastPath() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("slowSync", () -> "fast");
        assertThat(aspect.applyResilience(jp)).isEqualTo("fast");
    }

    // ----- async support ---------------------------------------------------

    @Test
    @DisplayName("async method returning CompletableFuture completes normally")
    void asyncSuccess() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncRetry",
                () -> CompletableFuture.completedFuture("async-ok"));

        Object result = aspect.applyResilience(jp);

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS)).isEqualTo("async-ok");
    }

    @Test
    @DisplayName("async @Retry retries a failed future and recovers")
    void asyncRetryRecovers() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncRetry", () ->
                attempts.incrementAndGet() < 2
                        ? CompletableFuture.failedFuture(new IllegalStateException("flaky"))
                        : CompletableFuture.completedFuture("recovered"));

        Object result = aspect.applyResilience(jp);

        assertThat(((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS)).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("async @TimeLimiter times out a never-completing future")
    void asyncTimeLimiterTimesOut() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncNever", CompletableFuture::new);

        Object result = aspect.applyResilience(jp);

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThatThrownBy(() -> ((CompletableFuture<?>) result).get(3, TimeUnit.SECONDS))
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("async fallback resolves when the future completes exceptionally")
    void asyncFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncWithFallback",
                () -> CompletableFuture.failedFuture(new IllegalStateException("async-down")));

        Object result = aspect.applyResilience(jp);

        assertThat(((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS)).isEqualTo("fb:async-down");
    }

    @Test
    @DisplayName("async fallback also applies when proceed() throws synchronously")
    void asyncFallbackOnSyncThrow() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncWithFallback", () -> {
            throw new IllegalStateException("sync-down");
        });

        Object result = aspect.applyResilience(jp);

        assertThat(((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS)).isEqualTo("fb:sync-down");
    }

    @Test
    @DisplayName("async fallback returning a CompletionStage is used as-is")
    void asyncFallbackReturningFuture() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("asyncWithFutureFallback",
                () -> CompletableFuture.failedFuture(new IllegalStateException("down")));

        Object result = aspect.applyResilience(jp);

        assertThat(((CompletableFuture<?>) result).get(2, TimeUnit.SECONDS)).isEqualTo("future-fb");
    }

    // ----- test fixtures ---------------------------------------------------

    /** Fixture whose annotated methods drive the unified advice; bodies are never invoked. */
    @SuppressWarnings("unused")
    static class Fixture {

        @Retry(name = "u-retry", maxAttempts = 5, waitDuration = 1)
        String retryFive() {
            return null;
        }

        @Retry(name = "u-retry-cb", maxAttempts = 3, waitDuration = 1)
        @CircuitBreaker(name = "u-cb", slidingWindowSize = 10, minimumNumberOfCalls = 10)
        String retryWithCircuitBreaker() {
            return null;
        }

        @CircuitBreaker(name = "u-cb-only", slidingWindowSize = 7, minimumNumberOfCalls = 2,
                failureRateThreshold = 25f, waitDurationInOpenState = 1000)
        String guardedByCircuitBreaker() {
            return null;
        }

        @RateLimit(name = "u-rl", limitForPeriod = 1, limitRefreshPeriod = 60_000, timeoutDuration = 0)
        String limited() {
            return null;
        }

        @Bulkhead(name = "u-bh", maxConcurrentCalls = 3, maxWaitDuration = 10)
        String bulk() {
            return null;
        }

        @Retry(name = "u-default-retry")
        String plainDefaults() {
            return null;
        }

        @Retry(name = "u-fb-retry", maxAttempts = 2, waitDuration = 1)
        @CircuitBreaker(name = "u-fb-cb", fallbackMethod = "fallback")
        String composedFallback() {
            return null;
        }

        @TimeLimiter(name = "u-sync-tl", timeoutDuration = 250)
        String slowSync() {
            return null;
        }

        @Retry(name = "u-async-retry", maxAttempts = 3, waitDuration = 1)
        CompletableFuture<String> asyncRetry() {
            return null;
        }

        @TimeLimiter(name = "u-async-tl", timeoutDuration = 100)
        CompletableFuture<String> asyncNever() {
            return null;
        }

        @CircuitBreaker(name = "u-async-fb", fallbackMethod = "fallback")
        CompletableFuture<String> asyncWithFallback() {
            return null;
        }

        @CircuitBreaker(name = "u-async-future-fb", fallbackMethod = "futureFallback")
        CompletableFuture<String> asyncWithFutureFallback() {
            return null;
        }

        String unannotated() {
            return null;
        }

        private String fallback(Throwable ex) {
            return "fb:" + ex.getMessage();
        }

        private CompletableFuture<String> futureFallback(Throwable ex) {
            return CompletableFuture.completedFuture("future-fb");
        }
    }

    @Retry(name = "class-level-retry", maxAttempts = 2, waitDuration = 1)
    @SuppressWarnings("unused")
    static class ClassLevelFixture {
        String work() {
            return null;
        }
    }
}
