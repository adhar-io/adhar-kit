package com.adhar.kit.core.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Built-in {@link ContextSnapshot} that propagates the SLF4J
 * {@linkplain MDC Mapped Diagnostic Context} from the submitting thread to the
 * worker thread.
 *
 * <p>This is the default snapshot provider registered in the
 * {@link ContextSnapshotRegistry}, preserving the historical behaviour of
 * {@link ContextPropagatingExecutor} (which propagated only the MDC).</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MdcContextSnapshot implements ContextSnapshot {

    @Override
    public Object capture() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(Object captured) {
        if (captured != null) {
            MDC.setContextMap((Map<String, String>) captured);
        } else {
            MDC.clear();
        }
    }

    @Override
    public void reset() {
        MDC.clear();
    }

    @Override
    public String name() {
        return "mdc";
    }
}
