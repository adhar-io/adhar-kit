package com.adhar.kit.tracing.spring;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Spring Boot implementation of Tracing Service.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@RequiredArgsConstructor
public class SpringTracingAdapter implements FrameworkAdapter<TracingService>, TracingService {

    private final Tracer tracer;

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public TracingService getService() {
        return this;
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new SpringSpanBuilder(tracer, name);
    }

    @Override
    public <T> T executeInSpan(String name, Supplier<T> operation) {
        io.micrometer.tracing.Span span = tracer.nextSpan().name(name).start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            return operation.get();
        } finally {
            span.end();
        }
    }

    @Override
    public void executeInSpan(String name, Runnable operation) {
        io.micrometer.tracing.Span span = tracer.nextSpan().name(name).start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            operation.run();
        } finally {
            span.end();
        }
    }

    @Override
    public String getCurrentTraceId() {
        io.micrometer.tracing.Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }

    @Override
    public String getCurrentSpanId() {
        io.micrometer.tracing.Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : null;
    }

    @Override
    public void addTag(String key, String value) {
        io.micrometer.tracing.Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    @Override
    public void addEvent(String name) {
        io.micrometer.tracing.Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event(name);
        }
    }

    private static class SpringSpanBuilder implements SpanBuilder {
        private final io.micrometer.tracing.Span.Builder builder;

        SpringSpanBuilder(Tracer tracer, String name) {
            this.builder = tracer.spanBuilder().name(name);
        }

        @Override
        public SpanBuilder tag(String key, String value) {
            builder.tag(key, value);
            return this;
        }

        @Override
        public SpanBuilder event(String name) {
            builder.event(name);
            return this;
        }

        @Override
        public TracingService.Span start() {
            io.micrometer.tracing.Span started = builder.start();
            return new SpringSpan(started);
        }
    }

    private static class SpringSpan implements TracingService.Span {
        private final io.micrometer.tracing.Span span;

        SpringSpan(io.micrometer.tracing.Span span) {
            this.span = span;
        }

        @Override
        public void end() {
            span.end();
        }

        @Override
        public void addEvent(String name) {
            span.event(name);
        }

        @Override
        public void setTag(String key, String value) {
            span.tag(key, value);
        }

        @Override
        public void recordException(Throwable throwable) {
            span.error(throwable);
        }
    }
}
