package com.adhar.kit.core.util;

/**
 * Test {@link ContextSnapshot} backed by a {@link ThreadLocal}, used both to
 * verify custom-context propagation through {@link ContextPropagatingExecutor}
 * and (via {@code META-INF/services}) the ServiceLoader discovery path of
 * {@link ContextSnapshotRegistry}.
 */
public class ThreadLocalContextSnapshot implements ContextSnapshot {

    public static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    @Override
    public Object capture() {
        return HOLDER.get();
    }

    @Override
    public void restore(Object captured) {
        if (captured != null) {
            HOLDER.set((String) captured);
        } else {
            HOLDER.remove();
        }
    }

    @Override
    public void reset() {
        HOLDER.remove();
    }

    @Override
    public String name() {
        return "test-threadlocal";
    }
}
