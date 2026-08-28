package com.adhar.kit.tracing;

import com.adhar.kit.tracing.api.TracingService;

import java.util.function.Supplier;

/**
 * A tracing implementation that records nothing but still runs the wrapped operation. Used as the
 * singleton {@link TracingFacade} fallback in a Spring context where the real
 * {@code SpringTracingAdapter} (which needs an injected {@code Tracer}) hasn't been wired into the
 * static facade — {@code executeInSpan} must never break the business operation it wraps.
 */
public final class NoOpTracingService implements TracingService {

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new NoOpSpanBuilder();
    }

    @Override
    public <T> T executeInSpan(String name, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public void executeInSpan(String name, Runnable operation) {
        operation.run();
    }

    @Override
    public String getCurrentTraceId() {
        return null;
    }

    @Override
    public String getCurrentSpanId() {
        return null;
    }

    @Override
    public void addTag(String key, String value) {
        // no-op
    }

    @Override
    public void addEvent(String name) {
        // no-op
    }

    private static final class NoOpSpanBuilder implements SpanBuilder {
        @Override public SpanBuilder tag(String key, String value) { return this; }
        @Override public SpanBuilder event(String name) { return this; }
        @Override public Span start() { return new NoOpSpan(); }
    }

    private static final class NoOpSpan implements Span {
        @Override public void end() { }
        @Override public void addEvent(String name) { }
        @Override public void setTag(String key, String value) { }
        @Override public void recordException(Throwable throwable) { }
    }
}
