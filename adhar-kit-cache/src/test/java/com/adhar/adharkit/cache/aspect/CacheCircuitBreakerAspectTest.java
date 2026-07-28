package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheCircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheCircuitBreakerAspect} exercising the
 * closed/open/half-open state machine through a real annotated sample class and
 * the {@link TestJoinPoint} double.
 */
@DisplayName("CacheCircuitBreakerAspect Tests")
class CacheCircuitBreakerAspectTest {

    private CacheCircuitBreakerAspect aspect;
    private Sample sample;

    /**
     * Sample class annotated with a variety of circuit-breaker configurations.
     */
    static class Sample {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger fallbackCalls = new AtomicInteger();
        volatile boolean fail = false;

        // threshold 2, long timeout, short open wait
        @CacheCircuitBreaker(cacheName = "cb-basic", fallbackMethod = "fallback",
            failureThreshold = 2, timeout = 5000, waitDurationInOpenState = 60)
        public String basic(String id) {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return "v-" + id;
        }

        public String fallback(String id) {
            fallbackCalls.incrementAndGet();
            return "fallback-" + id;
        }

        // opens after a single failure, short open wait for half-open tests
        @CacheCircuitBreaker(cacheName = "cb-half", fallbackMethod = "fallback",
            failureThreshold = 1, timeout = 5000, waitDurationInOpenState = 50)
        public String half(String id) {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return "h-" + id;
        }

        // slow call: timeout of 1 ms is always exceeded
        @CacheCircuitBreaker(cacheName = "cb-slow", fallbackMethod = "fallback",
            failureThreshold = 1, timeout = 1, waitDurationInOpenState = 60)
        public String slow(String id) {
            calls.incrementAndGet();
            sleep(30);
            return "s-" + id;
        }

        // no fallback configured (blank) with a very long open wait
        @CacheCircuitBreaker(cacheName = "cb-blank", fallbackMethod = "",
            failureThreshold = 1, timeout = 5000, waitDurationInOpenState = 60000)
        public String blankFallback(String id) {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("primary-boom");
            }
            return "b-" + id;
        }

        // fallback name that does not resolve
        @CacheCircuitBreaker(cacheName = "cb-nofb", fallbackMethod = "doesNotExist",
            failureThreshold = 1, timeout = 5000, waitDurationInOpenState = 60000)
        public String missingFallback(String id) {
            calls.incrementAndGet();
            throw new IllegalStateException("primary-boom");
        }

        // fallback that itself throws
        @CacheCircuitBreaker(cacheName = "cb-fbthrow", fallbackMethod = "throwingFallback",
            failureThreshold = 1, timeout = 5000, waitDurationInOpenState = 60000)
        public String failingWithThrowingFallback(String id) {
            calls.incrementAndGet();
            throw new IllegalStateException("primary-boom");
        }

        public String throwingFallback(String id) {
            throw new IllegalArgumentException("fallback-boom");
        }

        static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new CacheCircuitBreakerAspect();
        sample = new Sample();
    }

    private Object invoke(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(sample, methodName, args);
        return aspect.aroundCircuitBreaker(joinPoint,
            joinPoint.method().getAnnotation(CacheCircuitBreaker.class));
    }

    private Object invokeUnchecked(String methodName, Object... args) {
        try {
            return invoke(methodName, args);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    @Test
    @DisplayName("closed circuit proceeds normally and records success")
    void closedProceeds() throws Throwable {
        assertEquals("v-1", invoke("basic", "1"));
        assertEquals(1, sample.calls.get());
        assertEquals(CacheCircuitBreakerAspect.State.CLOSED, aspect.getState("cb-basic"));
        assertEquals(0, aspect.getFailureCount("cb-basic"));
    }

    @Test
    @DisplayName("unknown cache reports CLOSED and zero failures")
    void unknownCacheDefaults() {
        assertEquals(CacheCircuitBreakerAspect.State.CLOSED, aspect.getState("nope"));
        assertEquals(0, aspect.getFailureCount("nope"));
    }

    @Test
    @DisplayName("a failure below threshold is recorded but keeps the circuit closed and returns the fallback")
    void failureBelowThresholdReturnsFallback() throws Throwable {
        sample.fail = true;
        assertEquals("fallback-1", invoke("basic", "1"));
        assertEquals(1, sample.fallbackCalls.get());
        assertEquals(CacheCircuitBreakerAspect.State.CLOSED, aspect.getState("cb-basic"));
        assertEquals(1, aspect.getFailureCount("cb-basic"));
    }

    @Test
    @DisplayName("reaching the failure threshold opens the circuit")
    void reachingThresholdOpens() throws Throwable {
        sample.fail = true;
        invoke("basic", "1");
        invoke("basic", "1");
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-basic"));
        // failure count resets to 0 when opened
        assertEquals(0, aspect.getFailureCount("cb-basic"));
    }

    @Test
    @DisplayName("an open circuit short-circuits to the fallback without invoking the method")
    void openShortCircuitsToFallback() throws Throwable {
        sample.fail = true;
        invoke("half", "1"); // threshold 1 => opens
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-half"));
        int callsAfterOpen = sample.calls.get();

        // still within the open wait window => fallback, method not invoked
        assertEquals("fallback-2", invoke("half", "2"));
        assertEquals(callsAfterOpen, sample.calls.get(), "open circuit must not invoke the method");
    }

    @Test
    @DisplayName("a successful half-open trial closes the circuit")
    void halfOpenSuccessCloses() throws Throwable {
        sample.fail = true;
        invoke("half", "1");
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-half"));

        sample.fail = false;
        // wait for the open window to elapse, then the trial should succeed
        await().atMost(2, SECONDS).until(() -> "h-1".equals(invokeUnchecked("half", "1")));
        assertEquals(CacheCircuitBreakerAspect.State.CLOSED, aspect.getState("cb-half"));
    }

    @Test
    @DisplayName("a failed half-open trial re-opens the circuit")
    void halfOpenFailureReopens() throws Throwable {
        sample.fail = true;
        invoke("half", "1");
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-half"));

        // wait past the open window, then a failing trial must re-open
        await().atMost(2, SECONDS).until(() -> {
            invokeUnchecked("half", "1"); // still failing => fallback returned
            return aspect.getState("cb-half") == CacheCircuitBreakerAspect.State.OPEN
                && sample.calls.get() >= 2;
        });
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-half"));
    }

    @Test
    @DisplayName("a slow call is counted as a failure")
    void slowCallCountedAsFailure() throws Throwable {
        // returns normally but exceeds the 1 ms timeout => failure => opens (threshold 1)
        assertEquals("s-1", invoke("slow", "1"));
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-slow"));
    }

    @Test
    @DisplayName("without a resolvable fallback the original exception propagates while closed")
    void noFallbackPropagatesWhileClosed() {
        sample.fail = true;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> invoke("blankFallback", "1"));
        assertEquals("primary-boom", ex.getMessage());
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-blank"));
    }

    @Test
    @DisplayName("an open circuit with no fallback falls through to the method")
    void openWithNoFallbackFallsThrough() throws Throwable {
        sample.fail = true;
        assertThrows(IllegalStateException.class, () -> invoke("blankFallback", "1"));
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-blank"));

        // open + no fallback => falls through to proceed(); method is invoked
        sample.fail = false;
        int before = sample.calls.get();
        assertEquals("b-2", invoke("blankFallback", "2"));
        assertEquals(before + 1, sample.calls.get());
        // state untouched by the fall-through
        assertEquals(CacheCircuitBreakerAspect.State.OPEN, aspect.getState("cb-blank"));
    }

    @Test
    @DisplayName("an unresolvable fallback method name propagates the original exception")
    void unresolvableFallbackPropagates() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> invoke("missingFallback", "1"));
        assertEquals("primary-boom", ex.getMessage());
    }

    @Test
    @DisplayName("a throwing fallback propagates its cause with the primary failure suppressed")
    void throwingFallbackPropagatesCause() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> invoke("failingWithThrowingFallback", "1"));
        assertEquals("fallback-boom", ex.getMessage());
        assertEquals(1, ex.getSuppressed().length, "primary failure should be suppressed on the fallback error");
        assertEquals("primary-boom", ex.getSuppressed()[0].getMessage());
    }
}
