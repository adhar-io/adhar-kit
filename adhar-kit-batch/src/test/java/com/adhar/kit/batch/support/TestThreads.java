package com.adhar.kit.batch.support;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Threads a test owns for the work it runs beside its own thread: one daemon platform thread per task,
 * all interrupted when the test ends.
 *
 * <p>Not {@code CompletableFuture.supplyAsync(task)} without an executor: that runs on the JVM's common
 * pool, which every test in the fork shares and which is sized from the CPU count. In the 2-CPU CI pod a
 * test that failed or flaked left a task parked on it (a held chunk, a slow job, a spinning clock), and a
 * later test's partition worker then waited behind those tasks until the manager waiting for it gave up:
 * {@code RemotePartitioningTest} timed out after two minutes in the release pipeline while passing in
 * nine seconds on a laptop, and with it the adhar-kit release and every Java service build that resolves
 * the kit from Nexus (2026-11-18). {@code NoTestRunsOnTheCommonPoolTest} keeps it that way.</p>
 */
public final class TestThreads implements Executor, AutoCloseable {

    private final ExecutorService executor =
            Executors.newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("test-task-", 0).factory());

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /** Interrupts whatever the test left running; call it from {@code @AfterEach}. */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
