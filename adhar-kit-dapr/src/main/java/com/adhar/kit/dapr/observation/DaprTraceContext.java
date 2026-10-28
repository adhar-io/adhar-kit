package com.adhar.kit.dapr.observation;

import java.util.Map;

/**
 * Supplies the W3C trace context ({@code traceparent}, {@code tracestate}) of the work the
 * application is doing right now, so every Dapr call joins the caller's trace instead of starting
 * a new one. Implementations read the current span or, failing that, the inbound request.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@FunctionalInterface
public interface DaprTraceContext {

    /** Header carrying the W3C trace parent. */
    String TRACEPARENT = "traceparent";

    /** Header carrying the W3C trace state. */
    String TRACESTATE = "tracestate";

    /** No trace context. */
    DaprTraceContext NONE = Map::of;

    /**
     * The trace headers for an outgoing call.
     *
     * @return {@code traceparent}/{@code tracestate} entries, possibly empty, never {@code null}
     */
    Map<String, String> current();

    /**
     * Uses this context when it yields a {@code traceparent}, the other one otherwise.
     *
     * @param fallback the context to fall back to
     * @return the combined context
     */
    default DaprTraceContext orElse(DaprTraceContext fallback) {
        return () -> {
            Map<String, String> headers = current();
            return headers.containsKey(TRACEPARENT) ? headers : fallback.current();
        };
    }
}
