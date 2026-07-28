package com.adhar.kit.core.util;

/**
 * Service provider interface for propagating an arbitrary piece of thread-bound
 * context across an asynchronous hand-off (e.g. through
 * {@link ContextPropagatingExecutor}).
 *
 * <p>A {@code ContextSnapshot} captures the current thread's context on the
 * submitting thread ({@link #capture()}), the captured token is carried to the
 * worker thread where it is applied before the task runs ({@link #restore(Object)}),
 * and the worker's context is cleaned up afterwards ({@link #reset()}) so pooled
 * threads never leak context between tasks.</p>
 *
 * <p>The built-in {@link MdcContextSnapshot} propagates the SLF4J MDC. Additional
 * providers - for example a {@code TenantContext} living in another module - can
 * be contributed without {@code adhar-kit-core} depending on them:</p>
 * <ul>
 *   <li><b>Programmatically</b> - via
 *       {@link ContextSnapshotRegistry#register(ContextSnapshot)}.</li>
 *   <li><b>Via {@link java.util.ServiceLoader}</b> - declared under
 *       {@code META-INF/services/com.adhar.kit.core.util.ContextSnapshot};
 *       discovered when the default {@link ContextSnapshotRegistry} initialises.</li>
 * </ul>
 *
 * <p>This SPI is intentionally the only coupling point: core defines the
 * contract, external code plugs in implementations. Implementations must be
 * thread-safe (their methods run on many threads concurrently) and should treat
 * the captured token as opaque.</p>
 *
 * <p><b>Example - propagating a tenant id held in a {@code ThreadLocal}:</b></p>
 * <pre>{@code
 * public final class TenantContextSnapshot implements ContextSnapshot {
 *     public Object capture()          { return TenantContext.getTenantId(); }
 *     public void restore(Object token) { TenantContext.setTenantId((String) token); }
 *     public void reset()              { TenantContext.clear(); }
 * }
 * ContextSnapshotRegistry.getDefault().register(new TenantContextSnapshot());
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface ContextSnapshot {

    /**
     * Captures the current thread's context, returning an opaque token that
     * {@link #restore(Object)} understands. Called on the submitting thread at
     * task-submit time. May return {@code null} to represent "no context".
     *
     * @return an opaque token representing the captured context, possibly null
     */
    Object capture();

    /**
     * Applies a previously {@linkplain #capture() captured} token onto the
     * current (worker) thread, immediately before the task runs.
     *
     * @param captured the token returned by {@link #capture()} (possibly null)
     */
    void restore(Object captured);

    /**
     * Clears this snapshot's context from the current (worker) thread after the
     * task completes, so a pooled thread does not leak context into the next
     * task. Defaults to a no-op.
     */
    default void reset() {
    }

    /**
     * A short name for this snapshot provider, used in diagnostics. Defaults to
     * the implementation's simple class name.
     *
     * @return the provider name
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
