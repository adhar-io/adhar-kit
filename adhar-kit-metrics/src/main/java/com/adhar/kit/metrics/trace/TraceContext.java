package com.adhar.kit.metrics.trace;

import java.util.Optional;

/**
 * Abstraction over the current distributed-tracing context, used to correlate
 * metrics recorded by this module (see {@code HttpMetricsFilter} and
 * {@code EnhancedMetricsAspect}) with the active trace.
 * <p>
 * Implementations read the trace/span identifiers of whatever tracing library is
 * present on the classpath (currently the OpenTelemetry Span API, see
 * {@link OpenTelemetryTraceContext}). The interface itself has no dependency on any
 * optional tracing library, so the core metrics classes can depend on it and remain
 * compilable and functional when no tracer is available.
 * </p>
 */
public interface TraceContext {

    /**
     * MDC key under which the current trace id is published for log correlation.
     */
    String TRACE_ID_KEY = "traceId";

    /**
     * MDC key under which the current span id is published for log correlation.
     */
    String SPAN_ID_KEY = "spanId";

    /**
     * Returns the id of the trace currently in scope, if any.
     *
     * @return the current trace id, or {@link Optional#empty()} when no valid span is active
     */
    Optional<String> currentTraceId();

    /**
     * Returns the id of the span currently in scope, if any.
     *
     * @return the current span id, or {@link Optional#empty()} when no valid span is active
     */
    Optional<String> currentSpanId();

    /**
     * Whether the current span is sampled (i.e. will be exported). Metrics backends can
     * use this to decide whether to attach an exemplar.
     *
     * @return {@code true} when a sampled span is currently active
     */
    boolean isSampled();

    /**
     * Convenience: whether a valid trace is currently active.
     *
     * @return {@code true} when {@link #currentTraceId()} is present
     */
    default boolean isActive() {
        return currentTraceId().isPresent();
    }
}
