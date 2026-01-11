package com.adhar.kit.tracing.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Consolidated utility class for all tracing operations including spans, context management,
 * baggage handling, and async operations.
 * <p>
 * This class provides convenient methods for creating spans, managing trace context,
 * handling async operations with proper context propagation, and managing baggage
 * for passing context information across service boundaries.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdharTracing {

    private final Tracer tracer;
    private final Map<String, String> localBaggage = new ConcurrentHashMap<>();

    // ========== SPAN MANAGEMENT METHODS ==========

    /**
     * Execute a block of code within a new span.
     *
     * @param spanName the name of the span
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T withinSpan(String spanName, Supplier<T> operation) {
        return withinSpanWithTags(spanName, null, operation);
    }

    /**
     * Execute a block of code within a new span with tags.
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    public <T> T withinSpan(String spanName, Map<String, String> tags, Supplier<T> operation) {
        return withinSpanWithTags(spanName, tags, operation);
    }

    /**
     * Execute a block of code within a new span with tags (internal method).
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    private <T> T withinSpanWithTags(String spanName, Map<String, String> tags, Supplier<T> operation) {
        Span span = tracer.nextSpan().name(spanName);

        if (tags != null) {
            tags.forEach(span::tag);
        }

        try {
            span.start();
            T result = operation.get();
            span.tag("success", "true");
            return result;
        } catch (RuntimeException e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            throw e;
        } catch (Exception e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a block of code within a new span (void operation).
     *
     * @param spanName the name of the span
     * @param operation the operation to execute
     */
    public void withinSpan(String spanName, Runnable operation) {
        withinSpanRunnable(spanName, null, operation);
    }

    /**
     * Execute a block of code within a new span with tags (void operation).
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param operation the operation to execute
     */
    public void withinSpan(String spanName, Map<String, String> tags, Runnable operation) {
        withinSpanRunnable(spanName, tags, operation);
    }

    /**
     * Execute a runnable within a new span with tags (internal method).
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param operation the operation to execute
     */
    private void withinSpanRunnable(String spanName, Map<String, String> tags, Runnable operation) {
        Span span = tracer.nextSpan().name(spanName);

        if (tags != null) {
            tags.forEach(span::tag);
        }

        try {
            span.start();
            operation.run();
            span.tag("success", "true");
        } catch (RuntimeException e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            throw e;
        } catch (Exception e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a callable within a new span.
     *
     * @param spanName the name of the span
     * @param callable the callable to execute
     * @param <T> the return type
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    public <T> T withinSpanCallable(String spanName, Callable<T> callable) throws Exception {
        return withinSpanCallable(spanName, null, callable);
    }

    /**
     * Execute a callable within a new span with tags.
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param callable the callable to execute
     * @param <T> the return type
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    public <T> T withinSpanCallable(String spanName, Map<String, String> tags, Callable<T> callable) throws Exception {
        Span span = tracer.nextSpan().name(spanName);

        if (tags != null) {
            tags.forEach(span::tag);
        }

        try {
            T result = callable.call();
            span.tag("success", "true");
            return result;
        } catch (Exception e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Execute an async operation with trace context propagation.
     *
     * @param spanName the name of the span
     * @param asyncOperation the async operation
     * @param <T> the return type
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> withinSpanAsync(String spanName, Supplier<CompletableFuture<T>> asyncOperation) {
        return withinSpanAsync(spanName, null, asyncOperation);
    }

    /**
     * Execute an async operation with trace context propagation and tags.
     *
     * @param spanName the name of the span
     * @param tags the tags to add to the span
     * @param asyncOperation the async operation
     * @param <T> the return type
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> withinSpanAsync(String spanName, Map<String, String> tags, Supplier<CompletableFuture<T>> asyncOperation) {
        Span span = tracer.nextSpan().name(spanName).tag("async", "true");

        if (tags != null) {
            tags.forEach(span::tag);
        }

        try {
            CompletableFuture<T> future = asyncOperation.get();

            return future.whenComplete((result, throwable) -> {
                try {
                    if (throwable != null) {
                        span.tag("success", "false")
                            .tag("error.class", throwable.getClass().getSimpleName())
                            .tag("error.message", throwable.getMessage());
                    } else {
                        span.tag("success", "true");
                    }
                } finally {
                    span.end();
                }
            });
        } catch (Exception e) {
            span.tag("success", "false")
                .tag("error.class", e.getClass().getSimpleName())
                .tag("error.message", e.getMessage());
            span.end();
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the current active span.
     *
     * @return the current span, or null if no span is active
     */
    public Span getCurrentSpan() {
        return tracer.currentSpan();
    }

    /**
     * Get the current trace ID.
     *
     * @return the current trace ID, or null if no span is active
     */
    public String getCurrentTraceId() {
        Span currentSpan = getCurrentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }

    /**
     * Get the current span ID.
     *
     * @return the current span ID, or null if no span is active
     */
    public String getCurrentSpanId() {
        Span currentSpan = getCurrentSpan();
        return currentSpan != null ? currentSpan.context().spanId() : null;
    }

    /**
     * Add a tag to the current span.
     *
     * @param key the tag key
     * @param value the tag value
     */
    public void addTag(String key, String value) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        } else {
            log.warn("Attempted to add tag to span, but no current span exists: {}={}", key, value);
        }
    }

    /**
     * Add multiple tags to the current span.
     *
     * @param tags the tags to add
     */
    public void addTags(Map<String, String> tags) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            tags.forEach(currentSpan::tag);
        } else {
            log.warn("Attempted to add tags to span, but no current span exists: {}", tags);
        }
    }

    /**
     * Add an event to the current span.
     *
     * @param eventName the event name
     */
    public void addEvent(String eventName) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            currentSpan.event(eventName);
        } else {
            log.warn("Attempted to add event to span, but no current span exists: {}", eventName);
        }
    }

    /**
     * Add an event with attributes to the current span.
     *
     * @param eventName the event name
     * @param attributes the event attributes
     */
    public void addEvent(String eventName, Map<String, String> attributes) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            currentSpan.event(eventName);
            attributes.forEach((key, value) -> currentSpan.tag("event." + key, value));
        } else {
            log.warn("Attempted to add event to span, but no current span exists: {} with attributes {}", eventName, attributes);
        }
    }

    /**
     * Record an exception in the current span.
     *
     * @param exception the exception to record
     */
    public void recordException(Throwable exception) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            currentSpan.tag("error", "true")
                     .tag("error.class", exception.getClass().getSimpleName())
                     .tag("error.message", exception.getMessage());
        } else {
            log.warn("Attempted to record exception in span, but no current span exists: {}", exception.getMessage());
        }
    }

    /**
     * Create a new span without making it current.
     *
     * @param spanName the span name
     * @return the new span
     */
    public Span createSpan(String spanName) {
        return tracer.nextSpan().name(spanName);
    }

    /**
     * Create a new span with tags without making it current.
     *
     * @param spanName the span name
     * @param tags the tags to add
     * @return the new span
     */
    public Span createSpan(String spanName, Map<String, String> tags) {
        Span span = tracer.nextSpan().name(spanName);
        if (tags != null) {
            tags.forEach(span::tag);
        }
        return span;
    }

    /**
     * Wrap a function to propagate trace context.
     *
     * @param function the function to wrap
     * @param <T> input type
     * @param <R> return type
     * @return wrapped function with trace context
     */
    public <T, R> Function<T, R> wrapWithTraceContext(Function<T, R> function) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan == null) {
            return function;
        }

        return input -> {
            try {
                return function.apply(input);
            } finally {
                // No-op, span is managed by the tracing context
            }
        };
    }

    /**
     * Wrap a consumer to propagate trace context.
     *
     * @param consumer the consumer to wrap
     * @param <T> input type
     * @return wrapped consumer with trace context
     */
    public <T> Consumer<T> wrapWithTraceContext(Consumer<T> consumer) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan == null) {
            return consumer;
        }

        return input -> {
            try {
                consumer.accept(input);
            } finally {
                // No-op, span is managed by the tracing context
            }
        };
    }

    /**
     * Wrap a runnable to propagate trace context.
     *
     * @param runnable the runnable to wrap
     * @return wrapped runnable with trace context
     */
    public Runnable wrapWithTraceContext(Runnable runnable) {
        Span currentSpan = getCurrentSpan();
        if (currentSpan == null) {
            return runnable;
        }

        return () -> {
            try {
                runnable.run();
            } finally {
                // No-op, span is managed by the tracing context
            }
        };
    }

    /**
     * Check if tracing is currently enabled and a span is active.
     *
     * @return true if a span is currently active
     */
    public boolean isTracingActive() {
        return getCurrentSpan() != null;
    }

    /**
     * Create a database span with semantic attributes.
     *
     * @param operation the database operation
     * @param table the table name
     * @param statement the SQL statement
     * @return the database span
     */
    public Span createDatabaseSpan(String operation, String table, String statement) {
        String spanName = operation + " " + table;
        return tracer.nextSpan()
                .name(spanName)
                .tag("span.kind", "client")
                .tag("db.system", "sql")
                .tag("db.operation", operation)
                .tag("db.sql.table", table)
                .tag("db.statement", statement);
    }

    /**
     * Create an HTTP client span with semantic attributes.
     *
     * @param method the HTTP method
     * @param url the URL
     * @return the HTTP client span
     */
    public Span createHttpClientSpan(String method, String url) {
        String spanName = "HTTP " + method;
        return tracer.nextSpan()
                .name(spanName)
                .tag("span.kind", "client")
                .tag("http.method", method)
                .tag("http.url", url);
    }

    /**
     * Create a messaging span with semantic attributes.
     *
     * @param operation the messaging operation (send, receive)
     * @param destination the destination name
     * @param system the messaging system
     * @return the messaging span
     */
    public Span createMessagingSpan(String operation, String destination, String system) {
        String spanName = destination + " " + operation;
        return tracer.nextSpan()
                .name(spanName)
                .tag("span.kind", "producer")
                .tag("messaging.operation", operation)
                .tag("messaging.destination", destination)
                .tag("messaging.system", system);
    }

    // ========== BAGGAGE MANAGEMENT METHODS ==========

    /**
     * Set a baggage item in the current trace context.
     * <p>
     * Baggage is key-value data that travels with a trace and can be used to
     * pass context information across service boundaries.
     * </p>
     *
     * @param key the baggage key
     * @param value the baggage value
     */
    public void setBaggage(String key, String value) {
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                // Note: Micrometer doesn't directly support baggage
                // This is a simplified implementation using span tags
                currentSpan.tag("baggage." + key, value);
                localBaggage.put(key, value);
                log.debug("Set baggage: {}={}", key, value);
            } else {
                log.warn("Attempted to set baggage but no current span exists: {}={}", key, value);
            }
        } catch (Exception e) {
            log.error("Failed to set baggage: {}={}", key, value, e);
        }
    }

    /**
     * Get a baggage item from the current trace context.
     *
     * @param key the baggage key
     * @return the baggage value, or null if not found
     */
    public String getBaggage(String key) {
        try {
            return localBaggage.get(key);
        } catch (Exception e) {
            log.error("Failed to get baggage: {}", key, e);
            return null;
        }
    }

    /**
     * Remove a baggage item from the current trace context.
     *
     * @param key the baggage key
     */
    public void removeBaggage(String key) {
        try {
            localBaggage.remove(key);
            log.debug("Removed baggage: {}", key);
        } catch (Exception e) {
            log.error("Failed to remove baggage: {}", key, e);
        }
    }

    /**
     * Get all baggage items from the current trace context.
     *
     * @return map of all baggage items
     */
    public Map<String, String> getAllBaggage() {
        return Map.copyOf(localBaggage);
    }

    /**
     * Clear all baggage items from the current trace context.
     */
    public void clearBaggage() {
        try {
            localBaggage.clear();
            log.debug("Cleared all baggage");
        } catch (Exception e) {
            log.error("Failed to clear baggage", e);
        }
    }

    /**
     * Set multiple baggage items at once.
     *
     * @param baggageItems map of baggage items to set
     */
    public void setBaggageItems(Map<String, String> baggageItems) {
        baggageItems.forEach(this::setBaggage);
    }

    /**
     * Copy baggage from current context to a new span.
     *
     * @param span the span to copy baggage to
     */
    public void copyBaggageToSpan(Span span) {
        try {
            localBaggage.forEach((key, value) -> {
                span.tag("baggage." + key, value);
            });
            log.debug("Copied {} baggage items to span", localBaggage.size());
        } catch (Exception e) {
            log.error("Failed to copy baggage to span", e);
        }
    }

    /**
     * Extract baggage from HTTP headers (for incoming requests).
     *
     * @param headers map of HTTP headers
     */
    public void extractBaggageFromHeaders(Map<String, String> headers) {
        try {
            headers.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().startsWith("baggage-"))
                    .forEach(entry -> {
                        String key = entry.getKey().substring("baggage-".length());
                        setBaggage(key, entry.getValue());
                    });
        } catch (Exception e) {
            log.error("Failed to extract baggage from headers", e);
        }
    }

    /**
     * Inject baggage into HTTP headers (for outgoing requests).
     *
     * @param headers mutable map of HTTP headers to inject into
     */
    public void injectBaggageIntoHeaders(Map<String, String> headers) {
        try {
            localBaggage.forEach((key, value) -> {
                headers.put("baggage-" + key, value);
            });
            log.debug("Injected {} baggage items into headers", localBaggage.size());
        } catch (Exception e) {
            log.error("Failed to inject baggage into headers", e);
        }
    }

    /**
     * Check if baggage contains a specific key.
     *
     * @param key the key to check
     * @return true if the key exists in baggage
     */
    public boolean containsBaggageKey(String key) {
        return localBaggage.containsKey(key);
    }

    /**
     * Get the number of baggage items.
     *
     * @return the count of baggage items
     */
    public int getBaggageCount() {
        return localBaggage.size();
    }

    /**
     * Check if baggage is empty.
     *
     * @return true if no baggage items exist
     */
    public boolean isBaggageEmpty() {
        return localBaggage.isEmpty();
    }
}
