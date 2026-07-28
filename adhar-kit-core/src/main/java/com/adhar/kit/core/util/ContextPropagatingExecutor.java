package com.adhar.kit.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ExecutorService} decorator that propagates thread-bound context from
 * the submitting thread to the worker thread via a pluggable set of
 * {@link ContextSnapshot} providers.
 *
 * <p>Each registered snapshot is captured at submit time, restored in the worker
 * thread before the task runs, and reset afterwards so pooled threads never leak
 * context between tasks. By default the providers come from the
 * {@linkplain ContextSnapshotRegistry#getDefault() default registry}, which
 * always includes the built-in SLF4J MDC snapshot ({@link MdcContextSnapshot})
 * plus any {@link ContextSnapshot} discovered via the
 * {@link java.util.ServiceLoader} or registered programmatically. This lets
 * external modules (e.g. a {@code TenantContext}) propagate their own context
 * without {@code adhar-kit-core} depending on them.</p>
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
    private final List<ContextSnapshot> snapshots;

    /**
     * Creates a context-propagating decorator around the given executor, using
     * the snapshot providers from the default {@link ContextSnapshotRegistry}
     * (MDC plus any registered or ServiceLoader-discovered providers).
     *
     * @param delegate the executor that actually runs the tasks
     */
    public ContextPropagatingExecutor(ExecutorService delegate) {
        this(delegate, ContextSnapshotRegistry.getDefault().snapshots());
    }

    /**
     * Creates a context-propagating decorator around the given executor using an
     * explicit, fixed set of snapshot providers (bypassing the default
     * registry). The providers are captured at construction time.
     *
     * @param delegate the executor that actually runs the tasks
     * @param snapshots the snapshot providers to propagate
     */
    public ContextPropagatingExecutor(ExecutorService delegate, List<ContextSnapshot> snapshots) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        this.snapshots = List.copyOf(snapshots);
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
        Object[] captured = captureAll();
        return () -> {
            restoreAll(captured);
            try {
                task.run();
            } finally {
                resetAll();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        Object[] captured = captureAll();
        return () -> {
            restoreAll(captured);
            try {
                return task.call();
            } finally {
                resetAll();
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

    private Object[] captureAll() {
        Object[] tokens = new Object[snapshots.size()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = snapshots.get(i).capture();
        }
        return tokens;
    }

    private void restoreAll(Object[] tokens) {
        for (int i = 0; i < snapshots.size(); i++) {
            snapshots.get(i).restore(tokens[i]);
        }
    }

    private void resetAll() {
        for (ContextSnapshot snapshot : snapshots) {
            snapshot.reset();
        }
    }
}
