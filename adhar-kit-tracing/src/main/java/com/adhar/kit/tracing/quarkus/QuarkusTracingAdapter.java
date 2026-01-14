package com.adhar.kit.tracing.quarkus;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;

/**
 * Quarkus implementation of Tracing Service using OpenTelemetry.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@ApplicationScoped
public class QuarkusTracingAdapter implements FrameworkAdapter<TracingService>, TracingService {

    @Inject
    Tracer tracer;

    @Override
    public Framework getSupportedFramework() {
        return Framework.QUARKUS;
    }

    @Override
    public TracingService getService() {
        return this;
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new QuarkusSpanBuilder(tracer, name);
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

    private static class QuarkusSpanBuilder implements SpanBuilder {
        private final io.opentelemetry.api.trace.SpanBuilder builder;

        QuarkusSpanBuilder(Tracer tracer, String name) {
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
            return new QuarkusSpan(started);
        }
    }

    private static class QuarkusSpan implements TracingService.Span {
        private final io.opentelemetry.api.trace.Span span;

        QuarkusSpan(io.opentelemetry.api.trace.Span span) {
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
