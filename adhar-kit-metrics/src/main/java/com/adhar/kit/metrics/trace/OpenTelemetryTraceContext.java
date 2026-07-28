package com.adhar.kit.metrics.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

import java.util.Optional;

/**
 * {@link TraceContext} implementation backed by the OpenTelemetry {@link Span} API.
 * <p>
 * This class references {@code io.opentelemetry.api.trace.*}, which is an <em>optional</em>
 * dependency of this module. It is therefore only instantiated when the OpenTelemetry API
 * is present on the classpath (the auto-configuration gates the bean with
 * {@code @ConditionalOnClass}). The rest of the module never references this class directly,
 * so the module compiles and runs without OpenTelemetry.
 * </p>
 */
public class OpenTelemetryTraceContext implements TraceContext {

    @Override
    public Optional<String> currentTraceId() {
        SpanContext context = currentValidContext();
        return context == null ? Optional.empty() : Optional.of(context.getTraceId());
    }

    @Override
    public Optional<String> currentSpanId() {
        SpanContext context = currentValidContext();
        return context == null ? Optional.empty() : Optional.of(context.getSpanId());
    }

    @Override
    public boolean isSampled() {
        SpanContext context = currentValidContext();
        return context != null && context.isSampled();
    }

    private SpanContext currentValidContext() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context : null;
    }
}
