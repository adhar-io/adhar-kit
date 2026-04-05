package com.adhar.kit.tracing.helidon;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Helidon-specific adapter for distributed tracing using OpenTelemetry.
 *
 * <p>This adapter provides a standalone tracing implementation for Helidon SE and MP
 * applications. Helidon 4.x has native support for OpenTelemetry, and this adapter
 * uses the OpenTelemetry API directly for span creation and context propagation.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>OpenTelemetry Native:</b> Uses the OpenTelemetry API supported by Helidon 4.x</li>
 *   <li><b>Context Propagation:</b> Automatic span context propagation via OpenTelemetry Scope</li>
 *   <li><b>Pluggable Tracer:</b> Accepts any OpenTelemetry Tracer instance</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * OpenTelemetry otel = OpenTelemetrySdk.builder()
 *     .setTracerProvider(tracerProvider)
 *     .build();
 * Tracer tracer = otel.getTracer("my-service");
 * HelidonTracingAdapter tracing = new HelidonTracingAdapter(tracer);
 *
 * // Execute within a span
 * String result = tracing.executeInSpan("process-order", () -> {
 *     tracing.addTag("order.id", orderId);
 *     return processOrder(orderId);
 * });
 * }</pre>
 *
 * <p><b>Usage with Helidon MP:</b></p>
 * <pre>{@code
 * // Tracer can be obtained from Helidon's OpenTelemetry integration
 * HelidonTracingAdapter tracing = new HelidonTracingAdapter(tracer);
 * tracing.executeInSpan("db-query", () -> repository.findAll());
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. The OpenTelemetry Tracer and Span APIs are
 * designed for concurrent use. Span context is propagated via thread-local storage.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see TracingService
 * @see FrameworkAdapter
 * @see Tracer
 */
public class HelidonTracingAdapter implements FrameworkAdapter<TracingService>, TracingService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(HelidonTracingAdapter.class);

    /**
     * OpenTelemetry Tracer used for span creation.
     */
    private final Tracer tracer;

    /**
     * Constructs a HelidonTracingAdapter with the provided OpenTelemetry Tracer.
     *
     * @param tracer the OpenTelemetry Tracer instance
     */
    public HelidonTracingAdapter(Tracer tracer) {
        this.tracer = tracer;
        log.debug("Initialized HelidonTracingAdapter with provided Tracer");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    @Override
    public TracingService getService() {
        return this;
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new HelidonSpanBuilder(tracer, name);
    }

    @Override
    public <T> T executeInSpan(String name, Supplier<T> operation) {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return operation.get();
        } finally {
            span.end();
        }
    }

    @Override
    public void executeInSpan(String name, Runnable operation) {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name).startSpan();
        try (Scope scope = span.makeCurrent()) {
            operation.run();
        } finally {
            span.end();
        }
    }

    @Override
    public String getCurrentTraceId() {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        return currentSpan.getSpanContext().getTraceId();
    }

    @Override
    public String getCurrentSpanId() {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        return currentSpan.getSpanContext().getSpanId();
    }

    @Override
    public void addTag(String key, String value) {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        currentSpan.setAttribute(key, value);
    }

    @Override
    public void addEvent(String name) {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        currentSpan.addEvent(name);
    }

    /**
     * Helidon-specific SpanBuilder implementation wrapping OpenTelemetry SpanBuilder.
     */
    private static class HelidonSpanBuilder implements SpanBuilder {
        private final io.opentelemetry.api.trace.SpanBuilder builder;

        HelidonSpanBuilder(Tracer tracer, String name) {
            this.builder = tracer.spanBuilder(name);
        }

        @Override
        public SpanBuilder tag(String key, String value) {
            builder.setAttribute(key, value);
            return this;
        }

        @Override
        public SpanBuilder event(String name) {
            // Events are added after span starts
            return this;
        }

        @Override
        public TracingService.Span start() {
            io.opentelemetry.api.trace.Span started = builder.startSpan();
            return new HelidonSpan(started);
        }
    }

    /**
     * Helidon-specific Span implementation wrapping OpenTelemetry Span.
     */
    private static class HelidonSpan implements TracingService.Span {
        private final io.opentelemetry.api.trace.Span span;

        HelidonSpan(io.opentelemetry.api.trace.Span span) {
            this.span = span;
        }

        @Override
        public void end() {
            span.end();
        }

        @Override
        public void addEvent(String name) {
            span.addEvent(name);
        }

        @Override
        public void setTag(String key, String value) {
            span.setAttribute(key, value);
        }

        @Override
        public void recordException(Throwable throwable) {
            span.recordException(throwable);
        }
    }
}
