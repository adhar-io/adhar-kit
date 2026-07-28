package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.CircuitBreaker;
import com.adhar.kit.resilience.annotation.Retry;
import com.adhar.kit.resilience.cache.FallbackCache;
import com.adhar.kit.resilience.chaos.ChaosPolicy;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the fallback-cache and chaos-injection branches added to
 * {@link ResilienceAspect}. Uses real in-memory Resilience4j registries, a real
 * {@link FallbackCache} / {@link ChaosPolicy} and a mocked {@link ProceedingJoinPoint}.
 */
@DisplayName("ResilienceAspect fallback-cache & chaos Tests")
class ResilienceAspectFallbackCacheChaosTest {

    private final Target target = new Target();

    private ResilienceAspect aspectWith(FallbackCache cache, boolean global, ChaosPolicy chaos) {
        return new ResilienceAspect(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                cache, global, chaos);
    }

    // ----- helpers ---------------------------------------------------------

    private ProceedingJoinPoint jp(Method method, Object[] args, Object result, Throwable error) throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn(method.getName());
        when(sig.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(target);
        when(jp.getArgs()).thenReturn(args);
        if (error != null) {
            when(jp.proceed()).thenThrow(error);
        } else {
            when(jp.proceed()).thenReturn(result);
        }
        return jp;
    }

    private static Method syncMethod() throws NoSuchMethodException {
        return Target.class.getDeclaredMethod("syncOp");
    }

    private static Method asyncMethod() throws NoSuchMethodException {
        return Target.class.getDeclaredMethod("asyncOp");
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(String method, Class<A> type) {
        try {
            return Holder.class.getDeclaredMethod(method).getAnnotation(type);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- sync fallback cache ---------------------------------------------

    @Test
    @DisplayName("successful result is cached and served on a later failure (opt-in via annotation)")
    void cachesSuccessAndServesOnFailure() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);
        CircuitBreaker ann = annotation("cbCache", CircuitBreaker.class);
        Object[] args = {"a"};

        Object ok = aspect.applyCircuitBreaker(jp(syncMethod(), args, "v1", null), ann);
        assertThat(ok).isEqualTo("v1");
        assertThat(cache.size()).isEqualTo(1);

        Object served = aspect.applyCircuitBreaker(
                jp(syncMethod(), args, null, new RuntimeException("boom")), ann);
        assertThat(served).isEqualTo("v1");
    }

    @Test
    @DisplayName("failure with cache enabled but empty cache rethrows the original cause")
    void failureWithEmptyCacheRethrows() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);

        assertThatThrownBy(() -> aspect.applyCircuitBreaker(
                jp(syncMethod(), new Object[]{"x"}, null, new IllegalStateException("boom")),
                annotation("cbCache", CircuitBreaker.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("a configured fallbackMethod takes precedence over the fallback cache")
    void fallbackMethodTakesPrecedenceOverCache() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);
        CircuitBreaker ann = annotation("cbCacheFb", CircuitBreaker.class);
        Object[] args = {"a"};

        // prime the cache with a successful result
        aspect.applyCircuitBreaker(jp(syncMethod(), args, "cached", null), ann);

        Object served = aspect.applyCircuitBreaker(
                jp(syncMethod(), args, null, new RuntimeException("orig")), ann);
        assertThat(served).asString().startsWith("fallback:").contains("orig");
    }

    @Test
    @DisplayName("global fallback-cache flag enables caching for a plain annotation")
    void globalFlagEnablesCaching() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, true, null);
        CircuitBreaker ann = annotation("cbPlain", CircuitBreaker.class);
        Object[] args = {"a"};

        aspect.applyCircuitBreaker(jp(syncMethod(), args, "v1", null), ann);
        Object served = aspect.applyCircuitBreaker(
                jp(syncMethod(), args, null, new RuntimeException("boom")), ann);

        assertThat(served).isEqualTo("v1");
    }

    @Test
    @DisplayName("retry annotation opt-in enables the fallback cache")
    void retryOptInEnablesCaching() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);
        Retry ann = annotation("retryCache", Retry.class);
        Object[] args = {"a"};

        aspect.applyRetry(jp(syncMethod(), args, "v1", null), ann);
        Object served = aspect.applyRetry(
                jp(syncMethod(), args, null, new RuntimeException("boom")), ann);

        assertThat(served).isEqualTo("v1");
    }

    @Test
    @DisplayName("with no fallback cache bean, an opt-in annotation still just rethrows on failure")
    void noCacheBeanRethrows() throws Throwable {
        ResilienceAspect aspect = aspectWith(null, false, null);

        assertThatThrownBy(() -> aspect.applyCircuitBreaker(
                jp(syncMethod(), new Object[]{"x"}, null, new IllegalStateException("boom")),
                annotation("cbCache", CircuitBreaker.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ----- chaos injection -------------------------------------------------

    @Test
    @DisplayName("chaos error injection propagates when no fallback is configured and target is never invoked")
    void chaosErrorPropagates() throws Throwable {
        ChaosPolicy chaos = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of());
        ResilienceAspect aspect = aspectWith(null, false, chaos);
        ProceedingJoinPoint jp = jp(syncMethod(), new Object[]{"a"}, "ok", null);

        assertThatThrownBy(() -> aspect.applyCircuitBreaker(jp, annotation("cbPlain", CircuitBreaker.class)))
                .isInstanceOf(ChaosPolicy.ChaosInjectedException.class);
        verify(jp, never()).proceed();
    }

    @Test
    @DisplayName("chaos-injected errors are observed by the decorators, so the fallback method is invoked")
    void chaosErrorTriggersFallback() throws Throwable {
        ChaosPolicy chaos = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of());
        ResilienceAspect aspect = aspectWith(null, false, chaos);
        ProceedingJoinPoint jp = jp(syncMethod(), new Object[]{"a"}, "ok", null);

        Object result = aspect.applyRetry(jp, annotation("retryChaosFb", Retry.class));

        assertThat(result).asString().startsWith("fallback:");
        verify(jp, never()).proceed();
    }

    @Test
    @DisplayName("a disabled chaos policy is a no-op and the real invocation proceeds")
    void disabledChaosProceeds() throws Throwable {
        ChaosPolicy chaos = new ChaosPolicy(false, false, 0, 0, true, 1.0, List.of());
        ResilienceAspect aspect = aspectWith(null, false, chaos);

        Object result = aspect.applyCircuitBreaker(
                jp(syncMethod(), new Object[]{"a"}, "ok", null),
                annotation("cbPlain", CircuitBreaker.class));

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("chaos with a non-matching method name does not interfere")
    void chaosNonMatchingMethod() throws Throwable {
        ChaosPolicy chaos = new ChaosPolicy(true, false, 0, 0, true, 1.0, List.of("otherName"));
        ResilienceAspect aspect = aspectWith(null, false, chaos);

        Object result = aspect.applyCircuitBreaker(
                jp(syncMethod(), new Object[]{"a"}, "ok", null),
                annotation("cbPlain", CircuitBreaker.class));

        assertThat(result).isEqualTo("ok");
    }

    // ----- async fallback cache --------------------------------------------

    @Test
    @DisplayName("async: successful future is cached and served on a later async failure")
    @SuppressWarnings("unchecked")
    void asyncCachesSuccessAndServesOnFailure() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);
        CircuitBreaker ann = annotation("cbCache", CircuitBreaker.class);
        Object[] args = {"a"};

        Object okFuture = aspect.applyCircuitBreaker(
                jp(asyncMethod(), args, CompletableFuture.completedFuture("av1"), null), ann);
        assertThat(((CompletableFuture<Object>) okFuture).get()).isEqualTo("av1");
        assertThat(cache.size()).isEqualTo(1);

        Object servedFuture = aspect.applyCircuitBreaker(
                jp(asyncMethod(), args, CompletableFuture.failedFuture(new RuntimeException("boom")), null), ann);
        assertThat(((CompletableFuture<Object>) servedFuture).get()).isEqualTo("av1");
    }

    @Test
    @DisplayName("async: failure with an empty cache completes exceptionally")
    @SuppressWarnings("unchecked")
    void asyncFailureEmptyCacheFails() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);

        Object future = aspect.applyCircuitBreaker(
                jp(asyncMethod(), new Object[]{"x"},
                        CompletableFuture.failedFuture(new IllegalStateException("boom")), null),
                annotation("cbCache", CircuitBreaker.class));

        assertThatThrownBy(() -> ((CompletableFuture<Object>) future).get())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("async: with no cache opt-in the raw future is returned unchanged")
    @SuppressWarnings("unchecked")
    void asyncNoCacheReturnsFuture() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);

        Object future = aspect.applyCircuitBreaker(
                jp(asyncMethod(), new Object[]{"a"}, CompletableFuture.completedFuture("plain"), null),
                annotation("cbPlain", CircuitBreaker.class));

        assertThat(((CompletableFuture<Object>) future).get()).isEqualTo("plain");
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("async: a fallbackMethod result is returned and successful results are still cached")
    @SuppressWarnings("unchecked")
    void asyncFallbackMethodAndCaching() throws Throwable {
        FallbackCache cache = new FallbackCache(10, null);
        ResilienceAspect aspect = aspectWith(cache, false, null);
        CircuitBreaker ann = annotation("cbCacheFb", CircuitBreaker.class);
        Object[] args = {"a"};

        Object okFuture = aspect.applyCircuitBreaker(
                jp(asyncMethod(), args, CompletableFuture.completedFuture("av1"), null), ann);
        assertThat(((CompletableFuture<Object>) okFuture).get()).isEqualTo("av1");
        assertThat(cache.size()).isEqualTo(1);

        Object fbFuture = aspect.applyCircuitBreaker(
                jp(asyncMethod(), args, CompletableFuture.failedFuture(new RuntimeException("orig")), null), ann);
        assertThat(((CompletableFuture<Object>) fbFuture).get()).asString().startsWith("fallback:");
    }

    // ----- fixtures --------------------------------------------------------

    /** Target with sync and async operations plus reflectively resolved fallback methods. */
    @SuppressWarnings("unused")
    static class Target {
        String syncOp() {
            return "real";
        }

        CompletableFuture<Object> asyncOp() {
            return CompletableFuture.completedFuture("real");
        }

        private String fallback(RuntimeException ex) {
            return "fallback:" + ex.getMessage();
        }
    }

    /** Holder of annotated methods used purely to obtain annotation instances. */
    @SuppressWarnings("unused")
    static class Holder {
        @CircuitBreaker(name = "cb-cache", fallbackCache = true)
        void cbCache() {}

        @CircuitBreaker(name = "cb-cache-fb", fallbackMethod = "fallback", fallbackCache = true)
        void cbCacheFb() {}

        @CircuitBreaker(name = "cb-plain")
        void cbPlain() {}

        @Retry(name = "retry-cache", fallbackCache = true, maxAttempts = 1, waitDuration = 0)
        void retryCache() {}

        @Retry(name = "retry-chaos-fb", fallbackMethod = "fallback", maxAttempts = 1, waitDuration = 0)
        void retryChaosFb() {}
    }
}
