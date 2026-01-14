package com.adhar.kit.tracing.api;

import java.util.function.Supplier;

/**
 * Framework-agnostic Tracing Service.
 * Works with Spring Boot, Quarkus, and Micronaut.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface TracingService {

    /**
     * Create a new span.
     *
     * @param name span name
     * @return span builder
     */
    SpanBuilder spanBuilder(String name);

    /**
     * Execute with a new span.
     *
     * @param name span name
     * @param operation operation to execute
     * @param <T> return type
     * @return operation result
     */
    <T> T executeInSpan(String name, Supplier<T> operation);

    /**
     * Execute runnable with a new span.
     *
     * @param name span name
     * @param operation operation to execute
     */
    void executeInSpan(String name, Runnable operation);

    /**
     * Get current trace ID.
     *
     * @return trace ID or null
     */
    String getCurrentTraceId();

    /**
     * Get current span ID.
     *
     * @return span ID or null
     */
    String getCurrentSpanId();

    /**
     * Add tag to current span.
     *
     * @param key tag key
     * @param value tag value
     */
    void addTag(String key, String value);

    /**
     * Add event to current span.
     *
     * @param name event name
     */
    void addEvent(String name);

    /**
     * Span builder interface.
     */
    interface SpanBuilder {
        SpanBuilder tag(String key, String value);
        SpanBuilder event(String name);
        Span start();
    }

    /**
     * Span interface.
     */
    interface Span extends AutoCloseable {
        void end();
        void addEvent(String name);
        void setTag(String key, String value);
        void recordException(Throwable throwable);

        @Override
        default void close() {
            end();
        }
    }
}

