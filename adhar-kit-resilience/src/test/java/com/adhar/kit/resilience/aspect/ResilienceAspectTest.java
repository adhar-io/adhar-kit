package com.adhar.kit.resilience.aspect;

import com.adhar.kit.resilience.annotation.Bulkhead;
import com.adhar.kit.resilience.annotation.CircuitBreaker;
import com.adhar.kit.resilience.annotation.RateLimit;
import com.adhar.kit.resilience.annotation.Retry;
import com.adhar.kit.resilience.annotation.TimeLimiter;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ResilienceAspect}.
 *
 * <p>Uses real in-memory Resilience4j registries and a mocked {@link ProceedingJoinPoint};
 * no Spring context, AspectJ weaving, or external infrastructure is required.</p>
 */
@DisplayName("ResilienceAspect Tests")
class ResilienceAspectTest {

    private ResilienceAspect aspect;
    private final Target target = new Target();

    @BeforeEach
    void setUp() {
        aspect = new ResilienceAspect(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults());
    }

    // ----- helpers ---------------------------------------------------------

    private ProceedingJoinPoint joinPoint(Object proceedResult, Throwable proceedError) throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("guarded");
        when(jp.getTarget()).thenReturn(target);
        when(sig.getMethod()).thenReturn(Target.class.getDeclaredMethods()[0]);
        if (proceedError != null) {
            when(jp.proceed()).thenThrow(proceedError);
        } else {
            when(jp.proceed()).thenReturn(proceedResult);
        }
        return jp;
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(String method, Class<A> type) {
        try {
            return Holder.class.getDeclaredMethod(method).getAnnotation(type);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- circuit breaker -------------------------------------------------

    @Test
    @DisplayName("circuit breaker returns the underlying result on success")
    void circuitBreakerSuccess() throws Throwable {
        Object result = aspect.applyCircuitBreaker(
                joinPoint("ok", null), annotation("cbWithFallback", CircuitBreaker.class));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("circuit breaker invokes fallback (with exception arg) on failure")
    void circuitBreakerFallback() throws Throwable {
        Object result = aspect.applyCircuitBreaker(
                joinPoint(null, new RuntimeException("orig")),
                annotation("cbWithFallback", CircuitBreaker.class));
        assertThat(result).asString().startsWith("fallback:").contains("orig");
    }

    @Test
    @DisplayName("circuit breaker rethrows when no fallback is configured")
    void circuitBreakerNoFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyCircuitBreaker(jp, annotation("noFallback", CircuitBreaker.class)))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- retry -----------------------------------------------------------

    @Test
    @DisplayName("retry returns the underlying result on success")
    void retrySuccess() throws Throwable {
        Object result = aspect.applyRetry(joinPoint("ok", null), annotation("retryWithFallback", Retry.class));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("retry invokes fallback on persistent failure")
    void retryFallback() throws Throwable {
        Object result = aspect.applyRetry(
                joinPoint(null, new RuntimeException("orig")),
                annotation("retryWithFallback", Retry.class));
        assertThat(result).asString().startsWith("fallback:").contains("orig");
    }

    @Test
    @DisplayName("retry rethrows when no fallback is configured")
    void retryNoFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyRetry(jp, annotation("retryNoFallback", Retry.class)))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- rate limiter ----------------------------------------------------

    @Test
    @DisplayName("rate limiter returns the underlying result on success")
    void rateLimitSuccess() throws Throwable {
        Object result = aspect.applyRateLimit(
                joinPoint("ok", null), annotation("rateWithFallback", RateLimit.class));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("rate limiter invokes fallback on failure")
    void rateLimitFallback() throws Throwable {
        Object result = aspect.applyRateLimit(
                joinPoint(null, new RuntimeException("orig")),
                annotation("rateWithFallback", RateLimit.class));
        assertThat(result).asString().startsWith("fallback:").contains("orig");
    }

    @Test
    @DisplayName("rate limiter rethrows when no fallback is configured")
    void rateLimitNoFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyRateLimit(jp, annotation("rateNoFallback", RateLimit.class)))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- bulkhead --------------------------------------------------------

    @Test
    @DisplayName("bulkhead returns the underlying result on success")
    void bulkheadSuccess() throws Throwable {
        Object result = aspect.applyBulkhead(
                joinPoint("ok", null), annotation("bulkWithFallback", Bulkhead.class));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("bulkhead invokes fallback on failure")
    void bulkheadFallback() throws Throwable {
        Object result = aspect.applyBulkhead(
                joinPoint(null, new RuntimeException("orig")),
                annotation("bulkWithFallback", Bulkhead.class));
        assertThat(result).asString().startsWith("fallback:").contains("orig");
    }

    @Test
    @DisplayName("bulkhead rethrows when no fallback is configured")
    void bulkheadNoFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyBulkhead(jp, annotation("bulkNoFallback", Bulkhead.class)))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- time limiter ----------------------------------------------------

    @Test
    @DisplayName("time limiter returns the underlying result on success")
    void timeLimiterSuccess() throws Throwable {
        Object result = aspect.applyTimeLimiter(
                joinPoint("ok", null), annotation("timeWithFallback", TimeLimiter.class));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("time limiter invokes (no-arg) fallback on failure")
    void timeLimiterFallback() throws Throwable {
        Object result = aspect.applyTimeLimiter(
                joinPoint(null, new RuntimeException("orig")),
                annotation("timeWithFallback", TimeLimiter.class));
        assertThat(result).isEqualTo("fallback-noarg");
    }

    @Test
    @DisplayName("time limiter rethrows when no fallback is configured")
    void timeLimiterNoFallback() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyTimeLimiter(jp, annotation("timeNoFallback", TimeLimiter.class)))
                .isInstanceOf(Exception.class);
    }

    // ----- fallback failure path -------------------------------------------

    @Test
    @DisplayName("a fallback method that itself throws is wrapped in a RuntimeException")
    void fallbackThatThrows() throws Throwable {
        ProceedingJoinPoint jp = joinPoint(null, new RuntimeException("orig"));
        assertThatThrownBy(() -> aspect.applyCircuitBreaker(jp, annotation("cbWithFailingFallback", CircuitBreaker.class)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallback method execution failed");
    }

    // ----- test fixtures ---------------------------------------------------

    /** Target whose fallback methods are resolved reflectively by the aspect. */
    static class Target {
        @SuppressWarnings("unused")
        private String fallback(RuntimeException ex) {
            return "fallback:" + ex.getMessage();
        }

        @SuppressWarnings("unused")
        private String fallbackNoArg() {
            return "fallback-noarg";
        }

        @SuppressWarnings("unused")
        private String failingFallback(RuntimeException ex) {
            throw new IllegalStateException("fallback boom");
        }
    }

    /** Holder of annotated methods used purely to obtain annotation instances. */
    @SuppressWarnings("unused")
    static class Holder {
        @CircuitBreaker(name = "cb", fallbackMethod = "fallback")
        void cbWithFallback() {}

        @CircuitBreaker(name = "cb-no-fb")
        void noFallback() {}

        @CircuitBreaker(name = "cb-fail", fallbackMethod = "failingFallback")
        void cbWithFailingFallback() {}

        @Retry(name = "retry", fallbackMethod = "fallback")
        void retryWithFallback() {}

        @Retry(name = "retry-no-fb")
        void retryNoFallback() {}

        @RateLimit(name = "rate", fallbackMethod = "fallback")
        void rateWithFallback() {}

        @RateLimit(name = "rate-no-fb")
        void rateNoFallback() {}

        @Bulkhead(name = "bulk", fallbackMethod = "fallback")
        void bulkWithFallback() {}

        @Bulkhead(name = "bulk-no-fb")
        void bulkNoFallback() {}

        @TimeLimiter(name = "time", fallbackMethod = "fallbackNoArg")
        void timeWithFallback() {}

        @TimeLimiter(name = "time-no-fb")
        void timeNoFallback() {}
    }
}
