package com.adhar.kit.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Async execution utility for non-blocking operations.
 *
 * <p>Provides convenient methods for async execution with CompletableFuture.</p>
 *
 * <p><b>Example - Run Async:</b></p>
 * <pre>{@code
 * AsyncUtil.runAsync(() -> {
 *     // Long-running task
 *     processData();
 * });
 * }</pre>
 *
 * <p><b>Example - Supply Async:</b></p>
 * <pre>{@code
 * CompletableFuture<User> future = AsyncUtil.supplyAsync(() -> {
 *     return userRepository.findById(userId);
 * });
 *
 * User user = future.get();
 * }</pre>
 *
 * <p><b>Example - Parallel Execution:</b></p>
 * <pre>{@code
 * List<CompletableFuture<Order>> futures = AsyncUtil.executeInParallel(
 *     orderIds,
 *     orderId -> orderService.getOrder(orderId)
 * );
 *
 * List<Order> orders = AsyncUtil.waitAll(futures);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AsyncUtil {

    private static ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );

    /**
     * Sets custom executor.
     *
     * @param executorService the executor service
     */
    public static void setExecutor(ExecutorService executorService) {
        executor = executorService;
    }

    /**
     * Runs task asynchronously.
     *
     * @param runnable the task to run
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    /**
     * Supplies value asynchronously.
     *
     * @param supplier the value supplier
     * @param <T> value type
     * @return CompletableFuture with value
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /**
     * Executes tasks in parallel.
     *
     * @param items items to process
     * @param processor item processor
     * @param <T> item type
     * @param <R> result type
     * @return list of futures
     */
    public static <T, R> List<CompletableFuture<R>> executeInParallel(
            List<T> items,
            java.util.function.Function<T, R> processor) {

        List<CompletableFuture<R>> futures = new ArrayList<>();

        for (T item : items) {
            CompletableFuture<R> future = supplyAsync(() -> processor.apply(item));
            futures.add(future);
        }

        return futures;
    }

    /**
     * Waits for all futures to complete.
     *
     * @param futures list of futures
     * @param <T> result type
     * @return list of results
     */
    public static <T> List<T> waitAll(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        allOf.join();

        List<T> results = new ArrayList<>();
        for (CompletableFuture<T> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("Failed to get future result", e);
            }
        }

        return results;
    }

    /**
     * Waits for all futures with timeout.
     *
     * @param futures list of futures
     * @param timeout timeout value
     * @param unit timeout unit
     * @param <T> result type
     * @return list of results
     * @throws TimeoutException if timeout
     */
    public static <T> List<T> waitAll(List<CompletableFuture<T>> futures,
                                      long timeout,
                                      TimeUnit unit) throws TimeoutException {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(timeout, unit);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to wait for futures", e);
            throw new RuntimeException(e);
        }

        List<T> results = new ArrayList<>();
        for (CompletableFuture<T> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("Failed to get future result", e);
            }
        }

        return results;
    }

    /**
     * Executes with timeout.
     *
     * @param supplier the value supplier
     * @param timeout timeout value
     * @param unit timeout unit
     * @param <T> value type
     * @return value
     * @throws TimeoutException if timeout
     */
    public static <T> T executeWithTimeout(Supplier<T> supplier,
                                           long timeout,
                                           TimeUnit unit) throws TimeoutException {
        CompletableFuture<T> future = supplyAsync(supplier);

        try {
            return future.get(timeout, unit);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Shuts down the executor.
     */
    public static void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

