package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Async;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Aspect implementation for the {@link Async} annotation.
 *
 * <p>Submits the annotated method to the configured executor. Supported return
 * types are {@code void} (fire-and-forget) and {@link CompletableFuture}
 * (the returned future completes when the async invocation does). Any other
 * return type falls back to synchronous execution with a warning. The
 * annotation's {@code timeout} attribute is applied to
 * {@code CompletableFuture} results.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Slf4j
public class AsyncAspect {

    private final Executor executor;

    /**
     * Creates the aspect with the executor used for async dispatch.
     *
     * @param executor executor that runs the annotated methods
     */
    public AsyncAspect(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Dispatches methods annotated with {@link Async} to the executor.
     *
     * @param joinPoint the intercepted method invocation
     * @param async the annotation instance
     * @return {@code null} for void methods, a {@link CompletableFuture} for
     *         future-returning methods, or the synchronous result otherwise
     * @throws Throwable if the fallback synchronous invocation fails
     */
    @Around("@annotation(async)")
    public Object applyAsync(ProceedingJoinPoint joinPoint, Async async) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getMethod().getReturnType();

        if (returnType == void.class || returnType == Void.class) {
            log.debug("Dispatching @Async void method {}", signature.toShortString());
            CompletableFuture.runAsync(() -> proceed(joinPoint), executor)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        log.error("@Async method {} failed", signature.toShortString(), error);
                    }
                });
            return null;
        }

        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            log.debug("Dispatching @Async future method {}", signature.toShortString());
            CompletableFuture<Object> future =
                CompletableFuture.supplyAsync(() -> proceed(joinPoint), executor)
                    .thenCompose(AsyncAspect::flatten);
            if (async.timeout() > 0) {
                future = future.orTimeout(async.timeout(), TimeUnit.MILLISECONDS);
            }
            return future;
        }

        log.warn("@Async method {} returns {} (only void and CompletableFuture are supported); "
            + "executing synchronously", signature.toShortString(), returnType.getSimpleName());
        return joinPoint.proceed();
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Object> flatten(Object result) {
        return result == null
            ? CompletableFuture.completedFuture(null)
            : (CompletableFuture<Object>) result;
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throw t instanceof CompletionException ce ? ce : new CompletionException(t);
        }
    }
}
