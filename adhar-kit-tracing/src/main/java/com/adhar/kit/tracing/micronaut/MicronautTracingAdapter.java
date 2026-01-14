package com.adhar.kit.tracing.micronaut;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * Micronaut implementation of Tracing Service using OpenTelemetry.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautTracingAdapter implements FrameworkAdapter<TracingService>, TracingService {

    private final Tracer tracer;

    public MicronautTracingAdapter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
    }

    @Override
    public TracingService getService() {
        return this;
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new MicronautSpanBuilder(tracer, name);
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

    private static class MicronautSpanBuilder implements SpanBuilder {
        private final io.opentelemetry.api.trace.SpanBuilder builder;

        MicronautSpanBuilder(Tracer tracer, String name) {
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
            return new MicronautSpan(started);
        }
    }

    private static class MicronautSpan implements TracingService.Span {
        private final io.opentelemetry.api.trace.Span span;

        MicronautSpan(io.opentelemetry.api.trace.Span span) {
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
