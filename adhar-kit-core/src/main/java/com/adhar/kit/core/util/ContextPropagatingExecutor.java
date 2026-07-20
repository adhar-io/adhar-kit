package com.adhar.kit.core.util;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ExecutorService} decorator that propagates the SLF4J MDC (Mapped
 * Diagnostic Context) from the submitting thread to the worker thread.
 *
 * <p>The MDC is snapshotted at submit time, restored in the worker thread
 * before the task runs, and cleared afterwards so pooled threads never leak
 * context between tasks.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ExecutorService executor = new ContextPropagatingExecutor(
 *     Executors.newFixedThreadPool(4));
 *
 * MDC.put("correlationId", "abc-123");
 * executor.submit(() -> {
 *     // MDC contains correlationId=abc-123 here
 *     log.info("processing");  // logged with the correlation id
 * });
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ContextPropagatingExecutor implements ExecutorService {

    private final ExecutorService delegate;

    /**
     * Creates a context-propagating decorator around the given executor.
     *
     * @param delegate the executor that actually runs the tasks
     */
    public ContextPropagatingExecutor(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Gets the wrapped executor.
     *
     * @return the delegate executor
     */
    public ExecutorService getDelegate() {
        return delegate;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            applyContext(context);
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            applyContext(context);
            try {
                return task.call();
            } finally {
                MDC.clear();
            }
        };
    }

    private <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        List<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            wrapped.add(wrap(task));
        }
        return wrapped;
    }

    private static void applyContext(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
