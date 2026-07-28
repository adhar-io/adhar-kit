package com.adhar.kit.metrics.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTelemetryTraceContext}, driving the OpenTelemetry Span API without an
 * SDK by making a {@link Span} current via the context propagation API.
 */
class OpenTelemetryTraceContextTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    private final OpenTelemetryTraceContext traceContext = new OpenTelemetryTraceContext();

    @Test
    void withoutActiveSpan_returnsEmpty() {
        assertThat(traceContext.currentTraceId()).isEmpty();
        assertThat(traceContext.currentSpanId()).isEmpty();
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.isActive()).isFalse();
    }

    @Test
    void withSampledSpan_returnsTraceAndSpanIds() {
        SpanContext sampled = SpanContext.create(TRACE_ID, SPAN_ID,
                TraceFlags.getSampled(), TraceState.getDefault());

        try (Scope ignored = Context.current().with(Span.wrap(sampled)).makeCurrent()) {
            assertThat(traceContext.currentTraceId()).contains(TRACE_ID);
            assertThat(traceContext.currentSpanId()).contains(SPAN_ID);
            assertThat(traceContext.isSampled()).isTrue();
            assertThat(traceContext.isActive()).isTrue();
        }

        // Scope closed -> no active span again.
        assertThat(traceContext.currentTraceId()).isEmpty();
    }

    @Test
    void withUnsampledSpan_reportsNotSampledButStillHasIds() {
        SpanContext unsampled = SpanContext.create(TRACE_ID, SPAN_ID,
                TraceFlags.getDefault(), TraceState.getDefault());

        try (Scope ignored = Context.current().with(Span.wrap(unsampled)).makeCurrent()) {
            assertThat(traceContext.currentTraceId()).contains(TRACE_ID);
            assertThat(traceContext.isSampled()).isFalse();
        }
    }
}
