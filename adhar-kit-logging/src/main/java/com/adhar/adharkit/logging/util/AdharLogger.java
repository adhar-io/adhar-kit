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
 * AdharLogger provides comprehensive logging utilities for microservices.
 *
 * This consolidated logger combines all logging functionality including:
 * <ul>
 *   <li>Correlation ID and user context management</li>
 *   <li>MDC (Mapped Diagnostic Context) operations</li>
 *   <li>Distributed tracing integration</li>
 *   <li>Structured logging with JSON serialization</li>
 *   <li>Sensitive data masking utilities</li>
 *   <li>Convenient logging methods with context</li>
 * </ul>
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Simple logging with context
 * adharLogger.info(MyService.class, "Processing request for user: {}", userId);
 *
 * // Logging with temporary MDC context
 * adharLogger.withContext(Map.of("requestId", "123"), () -> {
 *     adharLogger.info(MyService.class, "Processing started");
 * });
 *
 * // Ensure correlation ID exists
 * String correlationId = adharLogger.ensureCorrelationId(null);
 *
 * // JSON logging for structured data
 * Map&lt;String, Object&gt; data = Map.of("user", "john", "action", "login");
 * adharLogger.infoJson(MyService.class, "User action", data);
 * </pre>
 */
public class AdharLogger {

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    private final AdharLoggingProperties properties;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with tracer support.
     */
    public AdharLogger(AdharLoggingProperties properties, @Nullable Tracer tracer) {
        this(properties, tracer, DEFAULT_OBJECT_MAPPER);
    }

    /**
     * Constructor with tracer and custom ObjectMapper.
     */
    public AdharLogger(AdharLoggingProperties properties, @Nullable Tracer tracer, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "AdharLoggingProperties cannot be null");
        this.tracer = tracer;
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_OBJECT_MAPPER;
    }

    /**
     * Constructor without tracer.
     */
    public AdharLogger(AdharLoggingProperties properties) {
        this(properties, null, DEFAULT_OBJECT_MAPPER);
    }

    // ==================== Correlation ID and User Context ====================

    /**
     * Ensure that a correlationId exists in MDC. If correlationId is null/blank, a new one will be generated.
     * @param correlationId the correlation ID to set (optional)
     * @return the correlationId present after the call
     */
    public String ensureCorrelationId(@Nullable String correlationId) {
        return setCorrelationId(correlationId);
    }

    /**
     * Sets the correlation ID in the MDC context.
     * @param correlationId the correlation ID to set (optional)
     * @return the correlation ID that was set (either the provided one or a newly generated one)
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
     * Get the current correlationId from MDC, or null if absent.
     */
    @Nullable
    public String getCorrelationId() {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeCorrelationId()) {
            return null;
        }
        return MDC.get(properties.getMdc().getCorrelationIdField());
    }

    /**
     * Set current userId in MDC.
     */
    public void setUserId(@Nullable String userId) {
        if (!properties.getMdc().isEnabled() || !properties.getMdc().isIncludeUserInfo() || !StringUtils.hasText(userId)) {
            return;
        }
        MDC.put(properties.getMdc().getUserIdField(), userId);
    }

    /**
     * Get current userId from MDC, or null.
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
     * Put a single MDC key/value if enabled.
     */
    public void put(String key, String value) {
        if (!properties.getMdc().isEnabled() || !StringUtils.hasText(key) || value == null) {
            return;
        }
        MDC.put(key, value);
    }

    /**
     * Get a single MDC value by key.
     */
    @Nullable
    public String get(String key) {
        if (!properties.getMdc().isEnabled() || !StringUtils.hasText(key)) {
            return null;
        }
        return MDC.get(key);
    }

    /**
     * Clear entire MDC. Use with caution as this affects the entire thread context.
     */
    public void clear() {
        MDC.clear();
    }

    // ==================== Context Management ====================

    /**
     * Temporarily adds the given MDC entries for the scope of the runnable, then restores the previous values.
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
     * Same as withContext but for Supplier that returns a value.
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
    public void setTraceId(@Nullable String traceId) {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeTraceId()) {
            return;
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
    public void setSpanId(@Nullable String spanId) {
        if (!properties.getTracing().isEnabled() || !properties.getTracing().isIncludeSpanId()) {
            return;
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
}
