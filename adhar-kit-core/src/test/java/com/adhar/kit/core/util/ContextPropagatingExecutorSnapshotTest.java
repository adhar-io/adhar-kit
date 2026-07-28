package com.adhar.kit.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ContextPropagatingExecutor pluggable-snapshot Tests")
class ContextPropagatingExecutorSnapshotTest {

    private ContextPropagatingExecutor executor;

    @BeforeEach
    void setUp() {
        ThreadLocalContextSnapshot.HOLDER.remove();
        executor = new ContextPropagatingExecutor(
            Executors.newSingleThreadExecutor(),
            List.of(new ThreadLocalContextSnapshot()));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextSnapshot.HOLDER.remove();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("propagates a custom (non-MDC) snapshot to the worker thread")
    void propagatesCustomSnapshot() throws Exception {
        ThreadLocalContextSnapshot.HOLDER.set("tenant-42");

        Future<String> future = executor.submit(() -> ThreadLocalContextSnapshot.HOLDER.get());

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("tenant-42");
    }

    @Test
    @DisplayName("resets the custom context on the worker thread after the task")
    void resetsCustomContextAfterTask() throws Exception {
        ThreadLocalContextSnapshot.HOLDER.set("tenant-1");
        executor.submit(() -> ThreadLocalContextSnapshot.HOLDER.get()).get(5, TimeUnit.SECONDS);

        // Second task submitted with no context: the pooled worker must not leak
        // the previous tenant.
        ThreadLocalContextSnapshot.HOLDER.remove();
        Future<String> second = executor.submit(
            () -> String.valueOf(ThreadLocalContextSnapshot.HOLDER.get()));

        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("null");
    }

    @Test
    @DisplayName("explicit-snapshots constructor rejects nulls")
    void rejectsNulls() {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            assertThatNullPointerException()
                .isThrownBy(() -> new ContextPropagatingExecutor(delegate, null));
            assertThatNullPointerException()
                .isThrownBy(() -> new ContextPropagatingExecutor(null, List.of()));
        } finally {
            delegate.shutdownNow();
        }
    }

    @Test
    @DisplayName("an empty snapshot set still runs the task")
    void emptySnapshotsRunsTask() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ContextPropagatingExecutor noContext =
            new ContextPropagatingExecutor(delegate, List.of());
        try {
            assertThat(noContext.submit(() -> "ok").get(5, TimeUnit.SECONDS)).isEqualTo("ok");
        } finally {
            noContext.shutdownNow();
        }
    }
}
