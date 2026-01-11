package com.adhar.adharkit.logging.util;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Supplier;

/**
 * AdharLogger - Enterprise Logging Utility for Microservices.
 *
 * <p>This comprehensive logging utility provides enterprise-grade logging capabilities for microservices
 * with support for correlation IDs, distributed tracing, MDC context management, and structured logging.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Correlation ID Management:</b> Automatic generation and propagation of correlation IDs for request tracking</li>
 *   <li><b>MDC Operations:</b> Thread-safe Mapped Diagnostic Context management for contextual logging</li>
 *   <li><b>Distributed Tracing:</b> Seamless integration with OpenTelemetry and Zipkin for trace propagation</li>
 *   <li><b>Structured Logging:</b> JSON serialization support for log aggregation and analysis</li>
 *   <li><b>User Context:</b> User ID and user name tracking across the request lifecycle</li>
 *   <li><b>Scoped Context:</b> Temporary context that automatically cleans up after execution</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> All MDC operations are thread-safe. Each thread maintains its own MDC context
 * which is automatically propagated to child threads when using async operations.</p>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Inject AdharLogger
 * @Autowired
 * private AdharLogger logger;
 *
 * // Simple logging with class reference
 * logger.info(OrderService.class, "Processing order for customer: {}", customerId);
 *
 * // Correlation ID management
 * String correlationId = logger.ensureCorrelationId(null); // Auto-generate if missing
 * logger.setCorrelationId("order-12345"); // Set specific ID
 * String currentId = logger.getCorrelationId(); // Retrieve current ID
 *
 * // MDC context operations
 * logger.put("orderId", "12345");
 * logger.putAll(Map.of("customerId", "67890", "region", "US-WEST"));
 *
 * // Scoped context (automatically cleaned up)
 * logger.withContext(Map.of("requestId", "req-123"), () -> {
 *     logger.info(MyClass.class, "Processing request");
 *     // Context automatically cleared after execution
 * });
 *
 * // Structured JSON logging
 * Map<String, Object> data = Map.of(
 *     "orderId", "12345",
 *     "amount", 100.50,
 *     "currency", "USD"
 * );
 * logger.infoJson(MyClass.class, "Order created", data);
 *
 * // User context tracking
 * logger.setUserId("john.doe@example.com");
 * logger.setUserName("John Doe");
 *
 * // Distributed tracing
 * String traceId = logger.getTraceId(); // Get current trace ID
 * String spanId = logger.getSpanId(); // Get current span ID
 * logger.setTracingInfo(); // Update MDC with current trace context
 * }</pre>
 *
 * <p><b>Configuration:</b> Behavior is controlled by {@link AdharLoggingProperties}:</p>
 * <pre>{@code
 * adhar:
 *   logging:
 *     mdc:
 *       enabled: true
 *       include-correlation-id: true
 *       include-user-info: true
 *     tracing:
 *       enabled: true
 *       include-trace-id: true
 *       include-span-id: true
 * }</pre>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>MDC operations are lightweight and have minimal performance impact</li>
 *   <li>JSON serialization is lazy - only performed when logs are actually written</li>
 *   <li>Context cleanup is automatic - no manual intervention required</li>
 *   <li>Tracer integration has negligible overhead when tracing is disabled</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see AdharLoggingProperties
 * @see org.slf4j.MDC
 * @see io.micrometer.tracing.Tracer
 */
public class AdharLogger {

    /**
     * Default ObjectMapper for JSON serialization.
     * Used when no custom ObjectMapper is provided.
     */
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    /**
     * Logging configuration properties.
     * Controls behavior of MDC, tracing, masking, and other logging features.
     */
    private final AdharLoggingProperties properties;

    /**
     * Distributed tracing tracer instance (optional).
     * Used to extract trace/span IDs from current context.
     * May be null if tracing is not configured.
     */
    private final Tracer tracer;

    /**
     * ObjectMapper for JSON serialization.
     * Used for structured logging with complex objects.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructs AdharLogger with tracing support.
     *
     * <p>This constructor enables distributed tracing integration. If a tracer is provided,
     * trace and span IDs will be automatically extracted and added to the MDC context.</p>
     *
     * @param properties logging configuration properties (required)
     * @param tracer distributed tracing tracer (optional, may be null)
     */
    public AdharLogger(AdharLoggingProperties properties, @Nullable Tracer tracer) {
        this(properties, tracer, DEFAULT_OBJECT_MAPPER);
    }

    /**
     * Constructs AdharLogger with tracing and custom ObjectMapper.
     *
     * <p>This constructor provides full control over JSON serialization behavior.
     * Use a custom ObjectMapper to configure date formats, null handling, field naming, etc.</p>
     *
     * @param properties logging configuration properties (required)
     * @param tracer distributed tracing tracer (optional, may be null)
     * @param objectMapper custom ObjectMapper for JSON serialization (optional, defaults to standard mapper)
     */
    public AdharLogger(AdharLoggingProperties properties, @Nullable Tracer tracer, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "AdharLoggingProperties cannot be null");
        this.tracer = tracer;
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_OBJECT_MAPPER;
    }

    /**
     * Constructs AdharLogger without tracing support.
     *
     * <p>Use this constructor when distributed tracing is not required.
     * Trace/span IDs will not be automatically added to logs.</p>
     *
     * @param properties logging configuration properties (required)
     */
    public AdharLogger(AdharLoggingProperties properties) {
        this(properties, null, DEFAULT_OBJECT_MAPPER);
    }

    // ==================== Correlation ID and User Context ====================

    /**
     * Ensures that a correlation ID exists in the MDC context.
     *
     * <p>If the provided correlation ID is null or blank, a new UUID will be automatically generated.
     * This is useful for ensuring every request has a unique identifier for tracking across services.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // Generate new ID if none provided
     * String id = logger.ensureCorrelationId(null); // Returns UUID
     *
     * // Use existing ID
     * String id = logger.ensureCorrelationId("order-12345"); // Returns "order-12345"
     *
     * // In REST controller
     * @GetMapping("/orders")
     * public Orders getOrders(@RequestHeader(required = false) String correlationId) {
     *     logger.ensureCorrelationId(correlationId); // Auto-generate if missing
     *     return orderService.getOrders();
     * }
     * }</pre>
     *
     * @param correlationId the correlation ID to set (optional, null will trigger generation)
     * @return the correlation ID present after the call (either provided or newly generated)
     */
    public String ensureCorrelationId(@Nullable String correlationId) {
        return setCorrelationId(correlationId);
    }

    /**
     * Sets the correlation ID in the MDC context.
     *
     * <p>The correlation ID is used to track requests across microservices. It's automatically
     * included in all log entries when MDC is enabled. If null or blank, a new UUID is generated.</p>
     *
     * <p><b>Important:</b> The correlation ID should be set at the entry point of your application
     * (e.g., in a filter or controller) and will automatically propagate to all downstream logs.</p>
     *
     * <p><b>Configuration:</b></p>
     * <pre>{@code
     * adhar:
     *   logging:
     *     mdc:
     *       enabled: true
     *       include-correlation-id: true
     *       correlation-id-field: correlationId  # MDC key name
     * }</pre>
     *
     * @param correlationId the correlation ID to set (optional, auto-generated if null/blank)
     * @return the correlation ID that was set (either provided or newly generated)
     * @see #ensureCorrelationId(String)
     * @see #getCorrelationId()
     */
    public String setCorrelationId(@Nullable String correlationId) {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeCorrelationId()) {
            return correlationId;
        }

        String actualCorrelationId = StringUtils.hasText(correlationId) ? correlationId : generateCorrelationId();
        MDC.put(properties.getMdc().getCorrelationIdField(), actualCorrelationId);
        return actualCorrelationId;
    }

    /**
     * Retrieves the current correlation ID from the MDC context.
     *
     * <p>Returns null if:</p>
     * <ul>
     *   <li>MDC is disabled in configuration</li>
     *   <li>Correlation ID tracking is disabled</li>
     *   <li>No correlation ID has been set</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * String correlationId = logger.getCorrelationId();
     * if (correlationId != null) {
     *     // Include in external API calls
     *     httpHeaders.add("X-Correlation-ID", correlationId);
     * }
     * }</pre>
     *
     * @return the current correlation ID, or null if not set or disabled
     * @see #setCorrelationId(String)
     */
    @Nullable
    public String getCorrelationId() {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeCorrelationId()) {
            return null;
        }
        return MDC.get(properties.getMdc().getCorrelationIdField());
    }

    /**
     * Sets the current user ID in the MDC context.
     *
     * <p>The user ID is typically set after authentication and is included in all subsequent
     * log entries. This is useful for security auditing and troubleshooting user-specific issues.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * // After successful authentication
     * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
     * logger.setUserId(auth.getName()); // e.g., "john.doe@example.com"
     *
     * // All logs now include: [userId=john.doe@example.com]
     * logger.info(MyClass.class, "User action performed");
     * }</pre>
     *
     * @param userId the user ID to set (optional, null is ignored)
     * @see #getUserId()
     * @see #setUserName(String)
     */
    public void setUserId(@Nullable String userId) {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeUserInfo() || !StringUtils.hasText(userId)) {
            return;
        }
        MDC.put(properties.getMdc().getUserIdField(), userId);
    }

    /**
     * Retrieves the current user ID from the MDC context.
     *
     * <p>Returns null if:</p>
     * <ul>
     *   <li>MDC is disabled</li>
     *   <li>User info tracking is disabled</li>
     *   <li>No user ID has been set</li>
     * </ul>
     *
     * @return the current user ID, or null if not set or disabled
     * @see #setUserId(String)
     */
    @Nullable
    public String getUserId() {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeUserInfo()) {
            return null;
        }
        return MDC.get(properties.getMdc().getUserIdField());
    }

    // ==================== MDC Operations ====================

    /**
     * Adds a single key-value pair to the MDC (Mapped Diagnostic Context).
     *
     * <p>MDC is a thread-local map that allows you to add contextual information to your logs.
     * The data will be automatically included in all log entries from the current thread.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * logger.put("orderId", "12345");
     * logger.put("customerId", "67890");
     * logger.info(MyClass.class, "Processing order");
     * // Output: [orderId=12345, customerId=67890] Processing order
     *
     * // Don't forget to clean up when done
     * logger.remove("orderId");
     * logger.remove("customerId");
     * }</pre>
     *
     * <p><b>Thread Safety:</b> MDC is thread-local, so changes only affect the current thread.</p>
     *
     * @param key the MDC key (must not be null or empty)
     * @param value the MDC value (must not be null)
     * @see #putAll(Map)
     * @see #remove(String)
     */
    public void put(String key, String value) {
        if (!properties.getMdc().isEnabled() || !StringUtils.hasText(key) || value == null) {
            return;
        }
        MDC.put(key, value);
    }

    /**
     * Alias for {@link #put(String, String)}.
     *
     * <p>Provided for clarity when explicitly working with MDC operations.</p>
     *
     * @param key the MDC key
     * @param value the MDC value
     * @see #put(String, String)
     */
    public void putMdc(String key, String value) {
        put(key, value);
    }

    /**
     * Retrieves a value from the MDC by key.
     *
     * <p>Returns null if:</p>
     * <ul>
     *   <li>MDC is disabled in configuration</li>
     *   <li>The key is null or empty</li>
     *   <li>No value exists for the key</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * logger.put("orderId", "12345");
     * String orderId = logger.get("orderId"); // Returns "12345"
     * String missing = logger.get("nonexistent"); // Returns null
     * }</pre>
     *
     * @param key the MDC key to retrieve
     * @return the MDC value, or null if not found or MDC is disabled
     * @see #put(String, String)
     */
    @Nullable
    public String get(String key) {
        if (!properties.getMdc().isEnabled() || !StringUtils.hasText(key)) {
            return null;
        }
        return MDC.get(key);
    }

    /**
     * Clears the entire MDC context for the current thread.
     *
     * <p><b>Warning:</b> Use with caution! This removes ALL MDC entries including:
     * correlation IDs, user info, trace IDs, and any custom values.</p>
     *
     * <p>Typically you should use {@link #remove(String)} to remove specific keys,
     * or {@link #withContext(Map, Runnable)} for scoped context that auto-cleans.</p>
     *
     * <p><b>Example (not recommended):</b></p>
     * <pre>{@code
     * logger.clear(); // Removes everything from MDC
     * // Now all contextual information is lost
     * }</pre>
     *
     * <p><b>Better approach:</b></p>
     * <pre>{@code
     * logger.withContext(Map.of("key", "value"), () -> {
     *     // Context automatically cleaned up
     * });
     * }</pre>
     *
     * @see #remove(String)
     * @see #withContext(Map, Runnable)
     */
    public void clear() {
        MDC.clear();
    }

    // ==================== Context Management ====================

    /**
     * Executes a runnable with temporary MDC context that is automatically cleaned up.
     *
     * <p>This is the recommended way to add temporary context to your logs. The context
     * is automatically restored to its previous state after execution, even if an exception occurs.</p>
     *
     * <p><b>Key Benefits:</b></p>
     * <ul>
     *   <li>Automatic cleanup - no manual MDC management needed</li>
     *   <li>Exception-safe - cleanup happens in finally block</li>
     *   <li>Previous values preserved - original MDC state is restored</li>
     *   <li>Thread-safe - only affects current thread</li>
     * </ul>
     *
     * <p><b>Example - Simple Context:</b></p>
     * <pre>{@code
     * logger.withContext(Map.of("requestId", "req-123"), () -> {
     *     logger.info(MyClass.class, "Processing request");
     *     // Output: [requestId=req-123] Processing request
     *     processRequest();
     * });
     * // Context automatically removed
     * }</pre>
     *
     * <p><b>Example - Multiple Values:</b></p>
     * <pre>{@code
     * Map<String, String> context = Map.of(
     *     "orderId", "12345",
     *     "customerId", "67890",
     *     "region", "US-WEST"
     * );
     *
     * logger.withContext(context, () -> {
     *     logger.info(OrderService.class, "Validating order");
     *     // All logs include: [orderId=12345, customerId=67890, region=US-WEST]
     *     validateOrder();
     *     processPayment();
     *     shipOrder();
     * });
     * // All context automatically cleaned up
     * }</pre>
     *
     * <p><b>Example - Nested Contexts:</b></p>
     * <pre>{@code
     * logger.withContext(Map.of("operation", "checkout"), () -> {
     *     logger.info(MyClass.class, "Starting checkout");
     *
     *     logger.withContext(Map.of("step", "validate"), () -> {
     *         logger.info(MyClass.class, "Validating cart");
     *         // Output: [operation=checkout, step=validate] Validating cart
     *     });
     *
     *     logger.withContext(Map.of("step", "payment"), () -> {
     *         logger.info(MyClass.class, "Processing payment");
     *         // Output: [operation=checkout, step=payment] Processing payment
     *     });
     * });
     * }</pre>
     *
     * @param mdcEntries the MDC entries to add temporarily (null or empty is allowed)
     * @param runnable the code to execute with the context (required, must not be null)
     * @throws NullPointerException if runnable is null
     * @see #withContextSupplier(Map, Supplier)
     */
    public void withContext(Map<String, String> mdcEntries, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable cannot be null");
        if (mdcEntries == null || mdcEntries.isEmpty()) {
            runnable.run();
            return;
        }

        Map<String, String> previous = snapshot(mdcEntries.keySet());
        try {
            mdcEntries.forEach(MDC::put);
            runnable.run();
        } finally {
            restore(previous, mdcEntries.keySet());
        }
    }

    /**
     * Executes a supplier with temporary MDC context and returns the result.
     *
     * <p>This is similar to {@link #withContext(Map, Runnable)} but for operations that
     * return a value. The context is automatically cleaned up after execution.</p>
     *
     * <p><b>Example - Return Value:</b></p>
     * <pre>{@code
     * Order order = logger.withContextSupplier(
     *     Map.of("operation", "create-order"),
     *     () -> {
     *         logger.info(OrderService.class, "Creating order");
     *         return orderRepository.save(new Order());
     *     }
     * );
     * // Context cleaned up, order returned
     * }</pre>
     *
     * <p><b>Example - Complex Processing:</b></p>
     * <pre>{@code
     * PaymentResult result = logger.withContextSupplier(
     *     Map.of(
     *         "paymentId", paymentId,
     *         "amount", amount.toString(),
     *         "currency", currency
     *     ),
     *     () -> {
     *         logger.info(PaymentService.class, "Authorizing payment");
     *         PaymentResult auth = authorizePayment();
     *
     *         logger.info(PaymentService.class, "Capturing payment");
     *         return capturePayment(auth);
     *     }
     * );
     * }</pre>
     *
     * @param <T> the type of value returned by the supplier
     * @param mdcEntries the MDC entries to add temporarily (null or empty is allowed)
     * @param supplier the code to execute that returns a value (required, must not be null)
     * @return the value returned by the supplier
     * @throws NullPointerException if supplier is null
     * @see #withContext(Map, Runnable)
     */
    public <T> T withContextSupplier(Map<String, String> mdcEntries, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier cannot be null");
        if (mdcEntries == null || mdcEntries.isEmpty()) {
            return supplier.get();
        }

        Map<String, String> previous = snapshot(mdcEntries.keySet());
        try {
            mdcEntries.forEach(MDC::put);
            return supplier.get();
        } finally {
            restore(previous, mdcEntries.keySet());
        }
    }

    // ==================== Tracing Integration ====================

    /**
     * Sets tracing information (trace ID, span ID, parent ID) in MDC from current span.
     */
    public void setTracingInfo() {
        if (tracer != null) {
            setTraceId(null);
            setSpanId(null);
            setParentId(null);
        }
    }

    /**
     * Sets the trace ID in the MDC context.
     */
    public String setTraceId(@Nullable String traceId) {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeTraceId()) {
            return traceId;
        }

        String actualTraceId = traceId;
        if (!StringUtils.hasText(actualTraceId) && tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext context = currentSpan.context();
                actualTraceId = context.traceId();
            }
        }

        if (StringUtils.hasText(actualTraceId)) {
            MDC.put(properties.getTracing().getTraceIdField(), actualTraceId);
        }
        return actualTraceId;
    }

    /**
     * Gets the trace ID from the MDC context.
     */
    @Nullable
    public String getTraceId() {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeTraceId()) {
            return null;
        }
        return MDC.get(properties.getTracing().getTraceIdField());
    }

    /**
     * Sets the span ID in the MDC context.
     */
    public String setSpanId(@Nullable String spanId) {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeSpanId()) {
            return spanId;
        }

        String actualSpanId = spanId;
        if (!StringUtils.hasText(actualSpanId) && tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext context = currentSpan.context();
                actualSpanId = context.spanId();
            }
        }

        if (StringUtils.hasText(actualSpanId)) {
            MDC.put(properties.getTracing().getSpanIdField(), actualSpanId);
        }
        return actualSpanId;
    }

    /**
     * Gets the span ID from the MDC context.
     */
    @Nullable
    public String getSpanId() {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeSpanId()) {
            return null;
        }
        return MDC.get(properties.getTracing().getSpanIdField());
    }

    /**
     * Sets the parent span ID in the MDC context.
     */
    public void setParentId(@Nullable String parentId) {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeParentId()) {
            return;
        }

        String actualParentId = parentId;
        if (!StringUtils.hasText(actualParentId) && tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TraceContext context = currentSpan.context();
                if (context.parentId() != null) {
                    actualParentId = context.parentId();
                }
            }
        }

        if (StringUtils.hasText(actualParentId)) {
            MDC.put(properties.getTracing().getParentIdField(), actualParentId);
        }
    }

    // ==================== Convenience Logging Methods ====================

    /**
     * Get a logger for the specified class.
     */
    public Logger logger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Log at the specified level.
     */
    public void logAtLevel(Logger logger, Level level, String message, Object... args) {
        switch (level) {
            case ERROR -> logger.error(message, args);
            case WARN -> logger.warn(message, args);
            case INFO -> logger.info(message, args);
            case DEBUG -> logger.debug(message, args);
            case TRACE -> logger.trace(message, args);
        }
    }

    public void info(Class<?> source, String message, Object... args) {
        LoggerFactory.getLogger(source).info(message, args);
    }

    public void debug(Class<?> source, String message, Object... args) {
        LoggerFactory.getLogger(source).debug(message, args);
    }

    public void warn(Class<?> source, String message, Object... args) {
        LoggerFactory.getLogger(source).warn(message, args);
    }

    public void error(Class<?> source, String message, Object... args) {
        LoggerFactory.getLogger(source).error(message, args);
    }

    public void error(Class<?> source, String message, Throwable t) {
        LoggerFactory.getLogger(source).error(message, t);
    }

    public void trace(Class<?> source, String message, Object... args) {
        LoggerFactory.getLogger(source).trace(message, args);
    }

    // ==================== JSON Logging Methods ====================

    /**
     * Log structured data as JSON at INFO level.
     */
    public void infoJson(Class<?> source, String message, Object data) {
        logJson(LoggerFactory.getLogger(source), Level.INFO, message, data);
    }

    /**
     * Log structured data as JSON at DEBUG level.
     */
    public void debugJson(Class<?> source, String message, Object data) {
        logJson(LoggerFactory.getLogger(source), Level.DEBUG, message, data);
    }

    /**
     * Log structured data as JSON at WARN level.
     */
    public void warnJson(Class<?> source, String message, Object data) {
        logJson(LoggerFactory.getLogger(source), Level.WARN, message, data);
    }

    /**
     * Log structured data as JSON at ERROR level.
     */
    public void errorJson(Class<?> source, String message, Object data) {
        logJson(LoggerFactory.getLogger(source), Level.ERROR, message, data);
    }

    /**
     * Log structured data as JSON at TRACE level.
     */
    public void traceJson(Class<?> source, String message, Object data) {
        logJson(LoggerFactory.getLogger(source), Level.TRACE, message, data);
    }

    /**
     * Internal method to log structured data as JSON.
     */
    private void logJson(Logger logger, Level level, String message, Object data) {
        if (!isLogLevelEnabled(logger, level)) {
            return;
        }

        try {
            String jsonData = objectMapper.writeValueAsString(data);
            String logMessage = message + " | Data: {}";
            logAtLevel(logger, level, logMessage, jsonData);
        } catch (Exception e) {
            logAtLevel(logger, Level.WARN, "Failed to serialize data to JSON for message: {}. Error: {}", message, e.getMessage());
            logAtLevel(logger, level, message + " | Data: {}", data.toString());
        }
    }

    /**
     * Check if the specified log level is enabled for the logger.
     */
    private boolean isLogLevelEnabled(Logger logger, Level level) {
        return switch (level) {
            case ERROR -> logger.isErrorEnabled();
            case WARN -> logger.isWarnEnabled();
            case INFO -> logger.isInfoEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case TRACE -> logger.isTraceEnabled();
        };
    }

    // ==================== Helper Methods ====================

    /**
     * Generate a new correlation ID using UUID.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Take a snapshot of current MDC values for the given keys.
     */
    private Map<String, String> snapshot(Set<String> keys) {
        Map<String, String> snapshot = new HashMap<>();
        for (String key : keys) {
            String value = MDC.get(key);
            if (value != null) {
                snapshot.put(key, value);
            }
        }
        return snapshot;
    }

    /**
     * Restore MDC values from snapshot, removing keys that weren't present before.
     */
    private void restore(Map<String, String> snapshot, Set<String> keysToRestore) {
        for (String key : keysToRestore) {
            String previousValue = snapshot.get(key);
            if (previousValue != null) {
                MDC.put(key, previousValue);
            } else {
                MDC.remove(key);
            }
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Get current MDC context as a map.
     */
    public Map<String, String> getCurrentMdcContext() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap != null ? contextMap : Collections.emptyMap();
    }

    /**
     * Set multiple MDC entries at once.
     */
    public void putAll(Map<String, String> mdcEntries) {
        if (!properties.getMdc().isEnabled() || mdcEntries == null || mdcEntries.isEmpty()) {
            return;
        }
        mdcEntries.forEach(MDC::put);
    }

    /**
     * Remove a single MDC key.
     */
    public void remove(String key) {
        if (!properties.getMdc().isEnabled() || !StringUtils.hasText(key)) {
            return;
        }
        MDC.remove(key);
    }

    /**
     * Check if MDC is enabled in configuration.
     */
    public boolean isMdcEnabled() {
        return properties.getMdc().isEnabled();
    }

    /**
     * Check if tracing is enabled in configuration.
     */
    public boolean isTracingEnabled() {
        return properties.getTracing().isEnabled();
    }

    /**
     * Check if masking is enabled in configuration.
     */
    public boolean isMaskingEnabled() {
        return properties.getMasking().isEnabled();
    }

    /**
     * Get the configured ObjectMapper instance.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Get the AdharLoggingProperties configuration.
     */
    public AdharLoggingProperties getProperties() {
        return properties;
    }

    /**
     * Clear all MDC values.
     */
    public void clearMdc() {
        MDC.clear();
    }
}
