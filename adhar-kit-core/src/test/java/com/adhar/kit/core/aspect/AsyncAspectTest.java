package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Async;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AsyncAspect Tests")
class AsyncAspectTest {

    private static final String WORKER_PREFIX = "async-aspect-test-";

    private ExecutorService executor;
    private AsyncAspect aspect;

    /**
     * Real annotated sample methods; the annotation instances are read via
     * reflection and passed to the aspect exactly as Spring AOP would.
     */
    @SuppressWarnings("unused")
    static class Sample {

        @Async
        void fireAndForget() {
        }

        @Async
        CompletableFuture<String> future() {
            return null;
        }

        @Async(timeout = 100)
        CompletableFuture<String> slow() {
            return null;
        }

        @Async
        String unsupported() {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName(WORKER_PREFIX + t.threadId());
            t.setDaemon(true);
            return t;
        });
        aspect = new AsyncAspect(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static Async annotationOf(String methodName) throws Exception {
        return Sample.class.getDeclaredMethod(methodName).getAnnotation(Async.class);
    }

    private static ProceedingJoinPoint mockJoinPoint(String methodName) throws Exception {
        Method method = Sample.class.getDeclaredMethod(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("Sample." + methodName + "()");
        return joinPoint;
    }

    @Test
    @DisplayName("rejects a null executor")
    void rejectsNullExecutor() {
        assertThatThrownBy(() -> new AsyncAspect(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("void methods run on the executor thread and return null")
    void voidMethodRunsAsync() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("fireAndForget");
        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            executed.countDown();
            return null;
        });

        Object result = aspect.applyAsync(joinPoint, annotationOf("fireAndForget"));

        assertThat(result).isNull();
        assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith(WORKER_PREFIX);
    }

    @Test
    @DisplayName("void method failures are swallowed (fire-and-forget) after logging")
    void voidMethodFailureDoesNotPropagate() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("fireAndForget");
        CountDownLatch executed = new CountDownLatch(1);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executed.countDown();
            throw new IllegalStateException("boom");
        });

        Object result = aspect.applyAsync(joinPoint, annotationOf("fireAndForget"));

        assertThat(result).isNull();
        assertThat(executed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("CompletableFuture methods complete with the async result")
    void futureMethodCompletes() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("future");
        AtomicReference<String> threadName = new AtomicReference<>();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture("hello");
        });

        Object result = aspect.applyAsync(joinPoint, annotationOf("future"));

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).get(5, TimeUnit.SECONDS)).isEqualTo("hello");
        assertThat(threadName.get()).startsWith(WORKER_PREFIX);
    }

    @Test
    @DisplayName("a null future completes with null")
    void nullFutureCompletesWithNull() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("future");
        when(joinPoint.proceed()).thenReturn(null);

        CompletableFuture<?> result =
            (CompletableFuture<?>) aspect.applyAsync(joinPoint, annotationOf("future"));

        assertThat(result.get(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("failures complete the returned future exceptionally")
    void futureMethodFailureCompletesExceptionally() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("future");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("async boom"));

        CompletableFuture<?> result =
            (CompletableFuture<?>) aspect.applyAsync(joinPoint, annotationOf("future"));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("async boom");
    }

    @Test
    @DisplayName("honors the timeout attribute for future-returning methods")
    void honorsTimeout() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("slow");
        when(joinPoint.proceed()).thenReturn(new CompletableFuture<String>()); // never completes

        CompletableFuture<?> result =
            (CompletableFuture<?>) aspect.applyAsync(joinPoint, annotationOf("slow"));

        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("unsupported return types fall back to synchronous execution")
    void unsupportedReturnTypeExecutesSynchronously() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("unsupported");
        AtomicReference<String> threadName = new AtomicReference<>();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return "sync-result";
        });

        Object result = aspect.applyAsync(joinPoint, annotationOf("unsupported"));

        assertThat(result).isEqualTo("sync-result");
        assertThat(threadName.get()).isEqualTo(Thread.currentThread().getName());
    }
}
