package com.adhar.kit.core.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncUtilTest {

    @AfterAll
    static void restoreExecutor() {
        // Ensure subsequent test classes still have a usable executor.
        AsyncUtil.setExecutor(Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()));
    }

    @Test
    void runAsyncExecutesTask() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<Void> future = AsyncUtil.runAsync(counter::incrementAndGet);
        future.get(5, TimeUnit.SECONDS);
        assertEquals(1, counter.get());
    }

    @Test
    void supplyAsyncReturnsValue() throws Exception {
        CompletableFuture<String> future = AsyncUtil.supplyAsync(() -> "hello");
        assertEquals("hello", future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void executeInParallelAndWaitAllReturnsResults() {
        List<Integer> inputs = List.of(1, 2, 3, 4);
        List<CompletableFuture<Integer>> futures =
            AsyncUtil.executeInParallel(inputs, i -> i * 10);

        List<Integer> results = AsyncUtil.waitAll(futures);

        assertEquals(4, results.size());
        assertTrue(results.containsAll(List.of(10, 20, 30, 40)));
    }

    @Test
    void waitAllPropagatesFailureViaJoin() {
        CompletableFuture<String> good = CompletableFuture.completedFuture("good");
        CompletableFuture<String> bad = CompletableFuture.failedFuture(new RuntimeException("nope"));

        // allOf().join() surfaces the first failure as a CompletionException.
        assertThrows(java.util.concurrent.CompletionException.class, () ->
            AsyncUtil.waitAll(List.of(good, bad)));
    }

    @Test
    void waitAllWithTimeoutReturnsResults() throws Exception {
        List<CompletableFuture<Integer>> futures = AsyncUtil.executeInParallel(
            List.of(1, 2, 3), i -> i + 1);

        List<Integer> results = AsyncUtil.waitAll(futures, 5, TimeUnit.SECONDS);

        assertEquals(3, results.size());
        assertTrue(results.containsAll(List.of(2, 3, 4)));
    }

    @Test
    void waitAllWithTimeoutThrowsTimeoutException() {
        CompletableFuture<String> never = new CompletableFuture<>();
        assertThrows(TimeoutException.class, () ->
            AsyncUtil.waitAll(List.of(never), 50, TimeUnit.MILLISECONDS));
    }

    @Test
    void waitAllWithTimeoutWrapsExecutionException() {
        CompletableFuture<String> failing = CompletableFuture.failedFuture(new IllegalStateException("err"));
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            AsyncUtil.waitAll(List.of(failing), 5, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
    }

    @Test
    void executeWithTimeoutReturnsValue() throws Exception {
        String result = AsyncUtil.executeWithTimeout(() -> "computed", 5, TimeUnit.SECONDS);
        assertEquals("computed", result);
    }

    @Test
    void executeWithTimeoutThrowsTimeoutException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            assertThrows(TimeoutException.class, () ->
                AsyncUtil.executeWithTimeout(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "late";
                }, 50, TimeUnit.MILLISECONDS));
        } finally {
            latch.countDown();
        }
    }

    @Test
    void setExecutorAndShutdownStopsExecutor() {
        ExecutorService custom = Executors.newSingleThreadExecutor();
        AsyncUtil.setExecutor(custom);

        AsyncUtil.shutdown();

        assertTrue(custom.isShutdown());

        // Restore a working executor for any later tests in this class.
        AsyncUtil.setExecutor(Executors.newFixedThreadPool(2));
    }
}
