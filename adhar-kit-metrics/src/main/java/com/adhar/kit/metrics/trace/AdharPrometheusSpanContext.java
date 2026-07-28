package com.adhar.kit.metrics.trace;

import io.prometheus.metrics.tracer.common.SpanContext;

/**
 * Bridges the module's {@link TraceContext} to the Prometheus client's
 * {@link SpanContext} exemplar API.
 * <p>
 * When a bean of this type is present, Spring Boot's Prometheus auto-configuration wires it
 * into the {@code PrometheusMeterRegistry}, causing counters and histograms (including the
 * timers recorded by {@code HttpMetricsFilter} and {@code EnhancedMetricsAspect}) to carry
 * <em>exemplars</em> tagged with the current trace id. Exemplars are the cardinality-safe
 * mechanism for trace correlation: the trace id travels alongside the sample instead of
 * becoming a metric label, so it never inflates series cardinality.
 * </p>
 * <p>
 * This class references {@code io.prometheus.metrics.tracer.common.SpanContext}, an optional
 * dependency; it is only loaded when the Prometheus client is on the classpath (the
 * auto-configuration gates the bean with {@code @ConditionalOnClass}).
 * </p>
 */
public class AdharPrometheusSpanContext implements SpanContext {

    private final TraceContext traceContext;

    /**
     * Creates the bridge.
     *
     * @param traceContext the underlying trace context to read the current span from
     */
    public AdharPrometheusSpanContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public String getCurrentTraceId() {
        return traceContext.currentTraceId().orElse(null);
    }

    @Override
    public String getCurrentSpanId() {
        return traceContext.currentSpanId().orElse(null);
    }

    @Override
    public boolean isCurrentSpanSampled() {
        return traceContext.isSampled();
    }

    @Override
    public void markCurrentSpanAsExemplar() {
        // No-op: the module does not mutate spans, it only reads the current trace/span ids.
    }
}
