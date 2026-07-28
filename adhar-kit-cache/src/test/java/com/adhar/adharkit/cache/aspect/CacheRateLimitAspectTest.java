package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheRateLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheRateLimitAspect} covering the token bucket and the
 * concurrency semaphore.
 */
@DisplayName("CacheRateLimitAspect Tests")
class CacheRateLimitAspectTest {

    private CacheRateLimitAspect aspect;
    private Sample sample;

    static class Sample {
        final AtomicInteger calls = new AtomicInteger();
        CountDownLatch started;
        CountDownLatch release;

        @CacheRateLimit(maxConcurrent = 10, maxPerSecond = 1000, timeout = 1000)
        public String fast(String id) {
            calls.incrementAndGet();
            return "f-" + id;
        }

        // tiny bucket, no wait budget => second immediate call is rejected
        @CacheRateLimit(maxConcurrent = 10, maxPerSecond = 1, timeout = 0)
        public String throttled(String id) {
            calls.incrementAndGet();
            return "t-" + id;
        }

        // small refill rate with a generous timeout => waits then succeeds
        @CacheRateLimit(maxConcurrent = 10, maxPerSecond = 2, timeout = 3000)
        public String refilling(String id) {
            calls.incrementAndGet();
            return "r-" + id;
        }

        // single concurrency permit, blocks until released
        @CacheRateLimit(maxConcurrent = 1, maxPerSecond = 1000, timeout = 100)
        public String blocking(String id) throws InterruptedException {
            started.countDown();
            release.await();
            calls.incrementAndGet();
            return "b-" + id;
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new CacheRateLimitAspect();
        sample = new Sample();
    }

    private Object invoke(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(sample, methodName, args);
        return aspect.aroundCacheRateLimit(joinPoint,
            joinPoint.method().getAnnotation(CacheRateLimit.class));
    }

    @Test
    @DisplayName("a call within limits proceeds and registers a limiter")
    void withinLimitsProceeds() throws Throwable {
        assertEquals("f-1", invoke("fast", "1"));
        assertEquals(1, sample.calls.get());
        assertEquals(1, aspect.getLimiterCount());
    }

    @Test
    @DisplayName("distinct methods each get their own limiter")
    void distinctMethodsGetDistinctLimiters() throws Throwable {
        invoke("fast", "1");
        invoke("refilling", "1");
        assertEquals(2, aspect.getLimiterCount());
    }

    @Test
    @DisplayName("exceeding the per-second rate throws with no wait budget")
    void rateExceededThrows() throws Throwable {
        assertEquals("t-1", invoke("throttled", "1")); // consumes the single token
        CacheRateLimitAspect.CacheRateLimitExceededException ex =
            assertThrows(CacheRateLimitAspect.CacheRateLimitExceededException.class,
                () -> invoke("throttled", "2"));
        assertTrue(ex.getMessage().contains("Rate limit"), ex.getMessage());
        assertEquals(1, sample.calls.get(), "rejected call must not invoke the method");
    }

    @Test
    @DisplayName("a token that is not immediately available is awaited until it refills")
    void tokenAwaitedUntilRefill() throws Throwable {
        // capacity is 2 tokens; the third call must wait for a refill within the timeout
        assertEquals("r-1", invoke("refilling", "1"));
        assertEquals("r-2", invoke("refilling", "2"));
        assertEquals("r-3", invoke("refilling", "3"));
        assertEquals(3, sample.calls.get());
    }

    @Test
    @DisplayName("exceeding the concurrency cap throws while a permit is held")
    void concurrencyExceededThrows() throws Throwable {
        sample.started = new CountDownLatch(1);
        sample.release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> holder = pool.submit(() -> {
                try {
                    return invoke("blocking", "1");
                } catch (Throwable t) {
                    throw new IllegalStateException(t);
                }
            });
            assertTrue(sample.started.await(2, TimeUnit.SECONDS), "holder should have started");

            // the single permit is held; this call cannot acquire within timeout=100ms
            CacheRateLimitAspect.CacheRateLimitExceededException ex =
                assertThrows(CacheRateLimitAspect.CacheRateLimitExceededException.class,
                    () -> invoke("blocking", "2"));
            assertTrue(ex.getMessage().contains("Concurrency limit"), ex.getMessage());

            sample.release.countDown();
            assertEquals("b-1", holder.get(2, TimeUnit.SECONDS));
        } finally {
            sample.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a concurrency permit is released after the call so the next one succeeds")
    void permitReleasedAfterCall() throws Throwable {
        sample.started = new CountDownLatch(1);
        sample.release = new CountDownLatch(1);
        sample.release.countDown(); // don't block
        assertEquals("b-1", invoke("blocking", "1"));
        // permit was released in the finally block, so a second call also succeeds
        assertEquals("b-2", invoke("blocking", "2"));
        assertEquals(2, sample.calls.get());
    }

    @Test
    @DisplayName("the exceeded exception carries its message")
    void exceptionMessage() {
        CacheRateLimitAspect.CacheRateLimitExceededException ex =
            new CacheRateLimitAspect.CacheRateLimitExceededException("nope");
        assertEquals("nope", ex.getMessage());
    }
}
