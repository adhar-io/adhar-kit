package com.adhar.kit.tracing.util;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Consolidated utility class for all tracing operations including spans, context management,
 * baggage handling, and async operations.
 * <p>
 * This class provides convenient methods for creating spans, managing trace context,
 * handling async operations with proper context propagation, and managing baggage
 * for passing context information across service boundaries.
 * </p>
 * <p>
 * Baggage is backed by Micrometer Tracing's real {@link Tracer} baggage API
 * ({@link Tracer#createBaggageInScope(String, String)} / {@link Tracer#getAllBaggage()}), so
 * it is context-scoped and, when the underlying {@code Tracer} is wired with a baggage-aware
 * propagator (as done by the auto-configuration), it participates in cross-process
 * propagation. This class additionally maintains its own W3C {@code baggage} HTTP header
 * (de)serialization so callers can propagate baggage manually across process boundaries
 * without depending on a specific web framework.
 * </p>
 */
@Component
@Slf4j
public class AdharTracing {

    /** Standard W3C Baggage HTTP header name (see https://www.w3.org/TR/baggage/). */
    private static final String W3C_BAGGAGE_HEADER = "baggage";

    private final Tracer tracer;
    private final AdharTracingProperties.BaggageProperties baggageProperties;

    /**
     * Tracks the currently open {@link BaggageInScope} for each baggage key set through this
     * instance, so that {@link #removeBaggage(String)}/{@link #clearBaggage()} and repeated
     * {@link #setBaggage(String, String)} calls can properly close the previous scope instead
     * of leaking it.
     */
    private final Map<String, BaggageInScope> baggageScopes = new ConcurrentHashMap<>();

    /**
     * Create an {@link AdharTracing} instance with default baggage configuration (baggage
     * enabled, no correlation fields, no remote-field allow-list).
     *
     * @param tracer the Micrometer {@link Tracer}
     */
    public AdharTracing(Tracer tracer) {
        this(tracer, new AdharTracingProperties.BaggageProperties());
    }

    /**
     * Create an {@link AdharTracing} instance honoring the given baggage configuration
     * (remote/correlation fields, max entries, etc.).
     *
     * @param tracer the Micrometer {@link Tracer}
     * @param baggageProperties the baggage configuration to honor
     */
    public AdharTracing(Tracer tracer, AdharTracingProperties.BaggageProperties baggageProperties) {
        this.tracer = tracer;
        this.baggageProperties = baggageProperties != null ? baggageProperties : new AdharTracingProperties.BaggageProperties();
    }

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
     * <p>
     * Captures the current span (if any) at wrap-time, and re-attaches it (via
     * {@link Tracer#withSpan(Span)}, opening a new {@link Tracer.SpanInScope}) for the
     * duration of every invocation of the returned function, closing the scope afterward
     * regardless of outcome. This allows the wrapped function to be safely handed off to
     * another thread (e.g. an executor) while still seeing the original trace context as
     * "current".
     * </p>
     *
     * @param function the function to wrap
     * @param <T> input type
     * @param <R> return type
     * @return wrapped function with trace context re-attached on invocation
     */
    public <T, R> Function<T, R> wrapWithTraceContext(Function<T, R> function) {
        Span capturedSpan = getCurrentSpan();
        if (capturedSpan == null) {
            return function;
        }

        return input -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                return function.apply(input);
            }
        };
    }

    /**
     * Wrap a consumer to propagate trace context. See {@link #wrapWithTraceContext(Function)}
     * for the propagation semantics.
     *
     * @param consumer the consumer to wrap
     * @param <T> input type
     * @return wrapped consumer with trace context re-attached on invocation
     */
    public <T> Consumer<T> wrapWithTraceContext(Consumer<T> consumer) {
        Span capturedSpan = getCurrentSpan();
        if (capturedSpan == null) {
            return consumer;
        }

        return input -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                consumer.accept(input);
            }
        };
    }

    /**
     * Wrap a runnable to propagate trace context. See {@link #wrapWithTraceContext(Function)}
     * for the propagation semantics.
     *
     * @param runnable the runnable to wrap
     * @return wrapped runnable with trace context re-attached on invocation
     */
    public Runnable wrapWithTraceContext(Runnable runnable) {
        Span capturedSpan = getCurrentSpan();
        if (capturedSpan == null) {
            return runnable;
        }

        return () -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                runnable.run();
            }
        };
    }

    /**
     * Wrap a supplier to propagate trace context. See {@link #wrapWithTraceContext(Function)}
     * for the propagation semantics.
     *
     * @param supplier the supplier to wrap
     * @param <T> return type
     * @return wrapped supplier with trace context re-attached on invocation
     */
    public <T> Supplier<T> wrapWithTraceContext(Supplier<T> supplier) {
        Span capturedSpan = getCurrentSpan();
        if (capturedSpan == null) {
            return supplier;
        }

        return () -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                return supplier.get();
            }
        };
    }

    /**
     * Wrap a callable to propagate trace context. See {@link #wrapWithTraceContext(Function)}
     * for the propagation semantics.
     *
     * @param callable the callable to wrap
     * @param <T> return type
     * @return wrapped callable with trace context re-attached on invocation
     */
    public <T> Callable<T> wrapWithTraceContext(Callable<T> callable) {
        Span capturedSpan = getCurrentSpan();
        if (capturedSpan == null) {
            return callable;
        }

        return () -> {
            try (Tracer.SpanInScope ignored = tracer.withSpan(capturedSpan)) {
                return callable.call();
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
     * Baggage is key-value data that travels with a trace and can be used to pass context
     * information across service boundaries. This is backed by the real Micrometer
     * {@link Tracer#createBaggageInScope(String, String)} API: the resulting
     * {@link BaggageInScope} is tracked internally and kept open until the key is
     * overwritten, removed ({@link #removeBaggage(String)}), or all baggage is cleared
     * ({@link #clearBaggage()}), so the value is visible to {@link #getBaggage(String)} and
     * propagators for as long as it is "set". If {@code key} is listed in
     * {@link AdharTracingProperties.BaggageProperties#getCorrelationFields()}, the value is
     * also tagged onto the current span (if any) as {@code baggage.<key>} for visibility in
     * the trace backend.
     * </p>
     *
     * @param key the baggage key
     * @param value the baggage value
     */
    public void setBaggage(String key, String value) {
        if (key == null) {
            log.warn("Attempted to set baggage with a null key");
            return;
        }
        if (value == null) {
            log.warn("Attempted to set baggage with a null value for key: {}", key);
            return;
        }
        if (!baggageProperties.isEnabled()) {
            log.debug("Baggage is disabled; ignoring setBaggage({}, {})", key, value);
            return;
        }
        try {
            // Close any previously open scope for this key BEFORE opening the new one, so the
            // underlying context stack is unwound in the correct (LIFO) order rather than
            // leaving a stale scope buried under the new one.
            BaggageInScope previous = baggageScopes.remove(key);
            if (previous != null) {
                previous.close();
            }
            BaggageInScope scope = tracer.createBaggageInScope(key, value);
            baggageScopes.put(key, scope);

            if (isCorrelationField(key)) {
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null) {
                    currentSpan.tag("baggage." + key, value);
                }
            }
            log.debug("Set baggage: {}={}", key, value);
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
        if (key == null) {
            return null;
        }
        try {
            Baggage baggage = tracer.getBaggage(key);
            return baggage != null ? baggage.get() : null;
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
            BaggageInScope scope = baggageScopes.remove(key);
            if (scope != null) {
                scope.close();
            }
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
        try {
            Map<String, String> all = tracer.getAllBaggage();
            return all != null ? Map.copyOf(all) : Map.of();
        } catch (Exception e) {
            log.error("Failed to get all baggage", e);
            return Map.of();
        }
    }

    /**
     * Clear all baggage items from the current trace context.
     */
    public void clearBaggage() {
        try {
            baggageScopes.values().forEach(BaggageInScope::close);
            baggageScopes.clear();
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
        if (baggageItems == null) {
            return;
        }
        baggageItems.forEach(this::setBaggage);
    }

    /**
     * Copy baggage from current context to a new span.
     *
     * @param span the span to copy baggage to
     */
    public void copyBaggageToSpan(Span span) {
        try {
            getAllBaggage().forEach((key, value) -> span.tag("baggage." + key, value));
            log.debug("Copied baggage items to span");
        } catch (Exception e) {
            log.error("Failed to copy baggage to span", e);
        }
    }

    /**
     * Extract baggage from HTTP headers (for incoming requests).
     * <p>
     * Reads the standard W3C {@code baggage} header (see
     * <a href="https://www.w3.org/TR/baggage/">https://www.w3.org/TR/baggage/</a>), a single
     * header whose value is a comma-separated list of {@code key=value} members (each key and
     * value percent-decoded), and calls {@link #setBaggage(String, String)} for each member
     * (up to {@link AdharTracingProperties.BaggageProperties#getMaxEntries()}, truncating
     * values longer than {@link AdharTracingProperties.BaggageProperties#getMaxValueLength()}).
     * The header lookup is case-insensitive to tolerate both HTTP header maps (case-insensitive
     * by convention) and plain {@code Map}s.
     * </p>
     *
     * @param headers map of HTTP headers
     */
    public void extractBaggageFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        try {
            headers.entrySet().stream()
                    .filter(entry -> W3C_BAGGAGE_HEADER.equalsIgnoreCase(entry.getKey()))
                    .findFirst()
                    .ifPresent(entry -> parseBaggageHeader(entry.getValue()));
        } catch (Exception e) {
            log.error("Failed to extract baggage from headers", e);
        }
    }

    /**
     * Inject baggage into HTTP headers (for outgoing requests).
     * <p>
     * Writes the standard W3C {@code baggage} header. If
     * {@link AdharTracingProperties.BaggageProperties#getRemoteFields()} is non-empty, only
     * those keys are propagated; otherwise all current baggage entries are propagated.
     * </p>
     *
     * @param headers mutable map of HTTP headers to inject into
     */
    public void injectBaggageIntoHeaders(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        try {
            Map<String, String> all = getAllBaggage();
            if (all.isEmpty()) {
                return;
            }

            Set<String> remoteFields = remoteFieldsLowerCase();

            String encoded = all.entrySet().stream()
                    .filter(entry -> remoteFields.isEmpty() || remoteFields.contains(entry.getKey().toLowerCase()))
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining(","));

            if (StringUtils.hasText(encoded)) {
                headers.put(W3C_BAGGAGE_HEADER, encoded);
                log.debug("Injected baggage into headers: {}", encoded);
            }
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
        return getAllBaggage().containsKey(key);
    }

    /**
     * Get the number of baggage items.
     *
     * @return the count of baggage items
     */
    public int getBaggageCount() {
        return getAllBaggage().size();
    }

    /**
     * Check if baggage is empty.
     *
     * @return true if no baggage items exist
     */
    public boolean isBaggageEmpty() {
        return getAllBaggage().isEmpty();
    }

    /**
     * Parse a raw W3C {@code baggage} header value and apply each member via
     * {@link #setBaggage(String, String)}.
     */
    private void parseBaggageHeader(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return;
        }

        int maxEntries = baggageProperties.getMaxEntries();
        int maxValueLength = baggageProperties.getMaxValueLength();
        int applied = 0;

        for (String member : headerValue.split(",")) {
            if (maxEntries > 0 && applied >= maxEntries) {
                break;
            }
            String trimmed = member.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Strip any W3C baggage member properties (";key=value" suffix); we only care
            // about the primary key=value pair.
            String keyValue = trimmed.split(";", 2)[0];
            int separatorIndex = keyValue.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = decode(keyValue.substring(0, separatorIndex).trim());
            String value = decode(keyValue.substring(separatorIndex + 1).trim());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (maxValueLength > 0 && value.length() > maxValueLength) {
                value = value.substring(0, maxValueLength);
            }

            setBaggage(key, value);
            applied++;
        }
    }

    /**
     * Whether the given baggage key is configured as a correlation field (and should
     * therefore also be tagged onto the current span for visibility in trace backends).
     */
    private boolean isCorrelationField(String key) {
        String[] correlationFields = baggageProperties.getCorrelationFields();
        if (correlationFields == null || correlationFields.length == 0) {
            return false;
        }
        for (String field : correlationFields) {
            if (field.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> remoteFieldsLowerCase() {
        String[] remoteFields = baggageProperties.getRemoteFields();
        if (remoteFields == null || remoteFields.length == 0) {
            return Set.of();
        }
        return Arrays.stream(remoteFields)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
