package com.adhar.kit.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ContextPropagatingExecutor Tests")
class ContextPropagatingExecutorTest {

    private ContextPropagatingExecutor executor;

    @BeforeEach
    void setUp() {
        MDC.clear();
        executor = new ContextPropagatingExecutor(Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("rejects a null delegate")
    void rejectsNullDelegate() {
        assertThatThrownBy(() -> new ContextPropagatingExecutor(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getDelegate returns the wrapped executor")
    void getDelegateReturnsWrapped() {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            assertThat(new ContextPropagatingExecutor(delegate).getDelegate()).isSameAs(delegate);
        } finally {
            delegate.shutdownNow();
        }
    }

    @Nested
    @DisplayName("MDC Propagation")
    class MdcPropagation {

        @Test
        @DisplayName("execute propagates the submitter's MDC to the worker thread")
        void executePropagatesMdc() throws Exception {
            MDC.put("correlationId", "abc-123");
            List<String> observed = new CopyOnWriteArrayList<>();

            executor.execute(() -> observed.add(String.valueOf(MDC.get("correlationId"))));
            awaitQuiescence();

            assertThat(observed).containsExactly("abc-123");
        }

        @Test
        @DisplayName("submit(Callable) propagates the MDC")
        void submitCallablePropagatesMdc() throws Exception {
            MDC.put("correlationId", "xyz-789");

            Future<String> future = executor.submit(() -> MDC.get("correlationId"));

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("xyz-789");
        }

        @Test
        @DisplayName("submit(Runnable, result) propagates the MDC and returns the result")
        void submitRunnableWithResultPropagatesMdc() throws Exception {
            MDC.put("user", "tapas");
            List<String> observed = new CopyOnWriteArrayList<>();

            Future<String> future = executor.submit(
                () -> observed.add(String.valueOf(MDC.get("user"))), "done");

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(observed).containsExactly("tapas");
        }

        @Test
        @DisplayName("MDC is cleared in the worker thread after the task completes")
        void mdcClearedAfterTask() throws Exception {
            MDC.put("correlationId", "leak-check");
            executor.submit(() -> MDC.get("correlationId")).get(5, TimeUnit.SECONDS);

            // Submit a second task without any MDC on the caller side; the
            // single pooled worker thread must not see the previous context.
            MDC.clear();
            Future<String> second = executor.submit(() -> String.valueOf(MDC.get("correlationId")));

            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("null");
        }

        @Test
        @DisplayName("empty submitter MDC results in an empty worker MDC")
        void emptyMdcStaysEmpty() throws Exception {
            Future<String> future = executor.submit(() -> String.valueOf(MDC.get("anything")));

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("null");
        }

        @Test
        @DisplayName("invokeAll propagates the MDC to every task")
        void invokeAllPropagatesMdc() throws Exception {
            MDC.put("batch", "b-1");
            List<Callable<String>> tasks = List.of(
                () -> MDC.get("batch"),
                () -> MDC.get("batch"));

            List<Future<String>> futures = executor.invokeAll(tasks);

            for (Future<String> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("b-1");
            }
        }

        @Test
        @DisplayName("invokeAll with timeout propagates the MDC")
        void invokeAllWithTimeoutPropagatesMdc() throws Exception {
            MDC.put("batch", "b-2");

            List<Future<String>> futures = executor.invokeAll(
                List.of(() -> MDC.get("batch")), 5, TimeUnit.SECONDS);

            assertThat(futures.get(0).get()).isEqualTo("b-2");
        }

        @Test
        @DisplayName("invokeAny propagates the MDC")
        void invokeAnyPropagatesMdc() throws Exception {
            MDC.put("batch", "b-3");

            String result = executor.invokeAny(List.of(() -> MDC.get("batch")));

            assertThat(result).isEqualTo("b-3");
        }

        @Test
        @DisplayName("invokeAny with timeout propagates the MDC")
        void invokeAnyWithTimeoutPropagatesMdc() throws Exception {
            MDC.put("batch", "b-4");

            String result = executor.invokeAny(
                List.of(() -> MDC.get("batch")), 5, TimeUnit.SECONDS);

            assertThat(result).isEqualTo("b-4");
        }
    }

    @Nested
    @DisplayName("Lifecycle Delegation")
    class LifecycleDelegation {

        @Test
        @DisplayName("shutdown/isShutdown/isTerminated/awaitTermination delegate")
        void lifecycleDelegates() throws Exception {
            assertThat(executor.isShutdown()).isFalse();
            assertThat(executor.isTerminated()).isFalse();

            executor.shutdown();

            assertThat(executor.isShutdown()).isTrue();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("shutdownNow delegates and returns pending tasks")
        void shutdownNowDelegates() {
            List<Runnable> pending = executor.shutdownNow();

            assertThat(pending).isEmpty();
            assertThat(executor.isShutdown()).isTrue();
        }
    }

    private void awaitQuiescence() throws Exception {
        // Barrier task: everything submitted before it has completed once it runs
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }
}
