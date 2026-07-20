package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheLock;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheLockAspect} single-flight behavior.
 */
@DisplayName("CacheLockAspect Tests")
class CacheLockAspectTest {

    private CacheLockAspect aspect;
    private LockService service;

    /**
     * Real annotated sample class.
     */
    static class LockService {
        final AtomicInteger invocations = new AtomicInteger();
        volatile CountDownLatch gate;

        @CacheLock(lockKey = "#reportId")
        public String generateReport(String reportId) throws InterruptedException {
            invocations.incrementAndGet();
            Thread.sleep(150);
            return "report-" + reportId;
        }

        @CacheLock(lockKey = "#reportId")
        public String generateFailing(String reportId) {
            invocations.incrementAndGet();
            throw new IllegalStateException("report-boom");
        }

        @CacheLock(lockKey = "#key", waitTime = 1)
        public String slowGated(String key) throws InterruptedException {
            invocations.incrementAndGet();
            gate.await(10, TimeUnit.SECONDS);
            return "gated-" + invocations.get();
        }

        @CacheLock(lockKey = "#key", failFast = true)
        public String failFast(String key) throws InterruptedException {
            invocations.incrementAndGet();
            gate.await(10, TimeUnit.SECONDS);
            return "ff-" + key;
        }

        @CacheLock(lockKey = "#key")
        public String gatedFailing(String key) throws InterruptedException {
            invocations.incrementAndGet();
            gate.await(10, TimeUnit.SECONDS);
            throw new IllegalStateException("gated-boom");
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new CacheLockAspect(new CacheKeyGenerator());
        service = new LockService();
    }

    private Object invoke(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, methodName, args);
        return aspect.aroundCacheLock(joinPoint, joinPoint.method().getAnnotation(CacheLock.class));
    }

    @Test
    @Timeout(30)
    @DisplayName("N concurrent callers of the same key run the loader exactly once")
    void singleFlightConcurrency() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return invoke("generateReport", "r1");
                    } catch (Throwable t) {
                        throw new ExecutionException(t);
                    }
                }));
            }
            start.countDown();

            for (Future<Object> future : futures) {
                assertEquals("report-r1", future.get(20, TimeUnit.SECONDS));
            }
            assertEquals(1, service.invocations.get(),
                "single-flight must collapse concurrent computations into one");
            assertEquals(0, aspect.getInFlightCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("different keys compute independently")
    void differentKeysAreIndependent() throws Throwable {
        assertEquals("report-a", invoke("generateReport", "a"));
        assertEquals("report-b", invoke("generateReport", "b"));
        assertEquals(2, service.invocations.get());
    }

    @Test
    @Timeout(30)
    @DisplayName("leader exception is propagated to followers")
    void leaderExceptionPropagatesToFollowers() throws Exception {
        // simple case: single caller gets the exception and the lock is released
        assertThrows(IllegalStateException.class, () -> invoke("generateFailing", "e1"));
        assertEquals(0, aspect.getInFlightCount());
        // lock released: a second call computes again
        assertThrows(IllegalStateException.class, () -> invoke("generateFailing", "e1"));
        assertEquals(2, service.invocations.get());
    }

    @Test
    @Timeout(30)
    @DisplayName("waiting follower receives the leader's exception")
    void waitingFollowerReceivesLeaderException() throws Exception {
        service.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> leader = pool.submit(() -> {
                try {
                    return invoke("gatedFailing", "ge1");
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
            long deadline = System.currentTimeMillis() + 5000;
            while (aspect.getInFlightCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            Future<Object> follower = pool.submit(() -> {
                try {
                    return invoke("gatedFailing", "ge1");
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
            Thread.sleep(200); // let the follower start waiting on the leader's future
            service.gate.countDown();

            ExecutionException leaderEx = assertThrows(ExecutionException.class,
                () -> leader.get(10, TimeUnit.SECONDS));
            ExecutionException followerEx = assertThrows(ExecutionException.class,
                () -> follower.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, rootCause(leaderEx));
            assertInstanceOf(IllegalStateException.class, rootCause(followerEx));
            assertEquals(1, service.invocations.get(),
                "the follower must share the leader's failure, not recompute");
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Test
    @Timeout(30)
    @DisplayName("follower falls back to local computation after waitTime")
    void followerTimesOutAndComputesLocally() throws Exception {
        service.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> leader = pool.submit(() -> {
                try {
                    return invoke("slowGated", "t1");
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
            // wait until the leader is registered in flight
            long deadline = System.currentTimeMillis() + 5000;
            while (aspect.getInFlightCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(1, aspect.getInFlightCount());

            Future<Object> follower = pool.submit(() -> {
                try {
                    return invoke("slowGated", "t1");
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });

            // follower waits waitTime=1s, times out, then proceeds locally (also gated);
            // release the gate so both complete
            Thread.sleep(1500);
            service.gate.countDown();

            assertNotNull(leader.get(10, TimeUnit.SECONDS));
            assertNotNull(follower.get(10, TimeUnit.SECONDS));
            assertEquals(2, service.invocations.get(),
                "follower must compute locally after waitTime expires");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("failFast=true fails immediately while the lock is held")
    void failFastThrowsWhileLocked() throws Exception {
        service.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> leader = pool.submit(() -> {
                try {
                    return invoke("failFast", "f1");
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
            long deadline = System.currentTimeMillis() + 5000;
            while (aspect.getInFlightCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            assertThrows(CacheLockAspect.CacheLockAcquisitionException.class,
                () -> invoke("failFast", "f1"));

            service.gate.countDown();
            assertEquals("ff-f1", leader.get(10, TimeUnit.SECONDS));
            assertEquals(1, service.invocations.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
