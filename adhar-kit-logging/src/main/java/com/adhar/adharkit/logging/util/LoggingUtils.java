package com.adhar.adharkit.logging.util;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class for logging operations.
 * Provides helper methods for MDC management, correlation IDs, and logging context.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class LoggingUtils {

    private final AdharLoggingProperties properties;
    private final Tracer tracer;

    /**
     * Constructor with properties only.
     *
     * @param properties Logging properties
     */
    public LoggingUtils(AdharLoggingProperties properties) {
        this(properties, null);
    }

    /**
     * Constructor with properties and tracer.
     *
     * @param properties Logging properties
     * @param tracer     Optional tracer for distributed tracing
     */
    public LoggingUtils(AdharLoggingProperties properties, Tracer tracer) {
        this.properties = properties;
        this.tracer = tracer;
    }

    /**
     * Set correlation ID in MDC.
     *
     * @param correlationId The correlation ID to set (null to generate new)
     * @return The correlation ID that was set
     */
    public String setCorrelationId(String correlationId) {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return correlationId;
        }

        String id = correlationId != null ? correlationId : generateCorrelationId();
        String field = properties.getMdc().getCorrelationIdField();
        if (field != null && properties.getMdc().isIncludeCorrelationId()) {
            MDC.put(field, id);
        }
        return id;
    }

    /**
     * Get correlation ID from MDC.
     *
     * @return The correlation ID, or null if not set
     */
    public String getCorrelationId() {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return null;
        }
        String field = properties.getMdc().getCorrelationIdField();
        return field != null ? MDC.get(field) : null;
    }

    /**
     * Set user ID in MDC.
     *
     * @param userId The user ID to set
     * @return The user ID that was set
     */
    public String setUserId(String userId) {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled() || userId == null) {
            return userId;
        }

        String field = properties.getMdc().getUserIdField();
        if (field != null && properties.getMdc().isIncludeUserInfo()) {
            MDC.put(field, userId);
        }
        return userId;
    }

    /**
     * Get user ID from MDC.
     *
     * @return The user ID, or null if not set
     */
    public String getUserId() {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return null;
        }
        String field = properties.getMdc().getUserIdField();
        return field != null ? MDC.get(field) : null;
    }

    /**
     * Set tenant ID in MDC.
     *
     * @param tenantId The tenant ID to set
     * @return The tenant ID that was set
     */
    public String setTenantId(String tenantId) {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled() || tenantId == null) {
            return tenantId;
        }

        String field = properties.getMdc().getTenantIdField();
        if (field != null && properties.getMdc().isIncludeTenantId()) {
            MDC.put(field, tenantId);
        }
        return tenantId;
    }

    /**
     * Get tenant ID from MDC.
     *
     * @return The tenant ID, or null if not set
     */
    public String getTenantId() {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return null;
        }
        String field = properties.getMdc().getTenantIdField();
        return field != null ? MDC.get(field) : null;
    }

    /**
     * Set session ID in MDC.
     *
     * @param sessionId The session ID to set
     * @return The session ID that was set
     */
    public String setSessionId(String sessionId) {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled() || sessionId == null) {
            return sessionId;
        }

        String field = properties.getMdc().getSessionIdField();
        if (field != null && properties.getMdc().isIncludeSessionId()) {
            MDC.put(field, sessionId);
        }
        return sessionId;
    }

    /**
     * Get session ID from MDC.
     *
     * @return The session ID, or null if not set
     */
    public String getSessionId() {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return null;
        }
        String field = properties.getMdc().getSessionIdField();
        return field != null ? MDC.get(field) : null;
    }

    /**
     * Set request ID in MDC.
     *
     * @param requestId The request ID to set
     * @return The request ID that was set
     */
    public String setRequestId(String requestId) {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled() || requestId == null) {
            return requestId;
        }

        String field = properties.getMdc().getRequestIdField();
        if (field != null && properties.getMdc().isIncludeRequestId()) {
            MDC.put(field, requestId);
        }
        return requestId;
    }

    /**
     * Get request ID from MDC.
     *
     * @return The request ID, or null if not set
     */
    public String getRequestId() {
        if (properties.getMdc() == null || !properties.getMdc().isEnabled()) {
            return null;
        }
        String field = properties.getMdc().getRequestIdField();
        return field != null ? MDC.get(field) : null;
    }

    /**
     * Put a value in MDC.
     *
     * @param key   The MDC key
     * @param value The value to set
     */
    public void putMdc(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * Get a value from MDC.
     *
     * @param key The MDC key
     * @return The value, or null if not set
     */
    public String getMdc(String key) {
        return key != null ? MDC.get(key) : null;
    }

    /**
     * Remove a value from MDC.
     *
     * @param key The MDC key to remove
     */
    public void removeMdc(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    /**
     * Clear all MDC values.
     */
    public void clearMdc() {
        MDC.clear();
    }

    /**
     * Get all MDC values as a map.
     *
     * @return A copy of the MDC context map
     */
    public Map<String, String> getMdcContext() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Set tracing information from current span.
     */
    public void setTracingInfo() {
        if (tracer == null || properties.getTracing() == null || !properties.getTracing().isEnabled()) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null && currentSpan.context() != null) {
            if (properties.getTracing().isIncludeTraceId()) {
                String traceId = currentSpan.context().traceId();
                String field = properties.getTracing().getTraceIdField();
                if (traceId != null && field != null) {
                    MDC.put(field, traceId);
                }
            }

            if (properties.getTracing().isIncludeSpanId()) {
                String spanId = currentSpan.context().spanId();
                String field = properties.getTracing().getSpanIdField();
                if (spanId != null && field != null) {
                    MDC.put(field, spanId);
                }
            }

            if (properties.getTracing().isIncludeParentId()) {
                String parentId = currentSpan.context().parentId();
                String field = properties.getTracing().getParentIdField();
                if (parentId != null && field != null) {
                    MDC.put(field, parentId);
                }
            }

            if (properties.getTracing().isIncludeSampled()) {
                Boolean sampled = currentSpan.context().sampled();
                String field = properties.getTracing().getSampledField();
                if (sampled != null && field != null) {
                    MDC.put(field, String.valueOf(sampled));
                }
            }
        }
    }

    /**
     * Set trace ID in MDC.
     *
     * @param traceId The trace ID to set
     * @return The trace ID that was set
     */
    public String setTraceId(String traceId) {
        if (properties.getTracing() == null || !properties.getTracing().isEnabled()) {
            return traceId;
        }

        // Fall back to the current span's trace ID when none is provided.
        if (traceId == null && tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                traceId = currentSpan.context().traceId();
            }
        }
        if (traceId == null) {
            return null;
        }

        String field = properties.getTracing().getTraceIdField();
        if (field != null && properties.getTracing().isIncludeTraceId()) {
            MDC.put(field, traceId);
        }
        return traceId;
    }

    /**
     * Set span ID in MDC.
     *
     * @param spanId The span ID to set
     * @return The span ID that was set
     */
    public String setSpanId(String spanId) {
        if (properties.getTracing() == null || !properties.getTracing().isEnabled()) {
            return spanId;
        }

        // Fall back to the current span's ID when none is provided.
        if (spanId == null && tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                spanId = currentSpan.context().spanId();
            }
        }
        if (spanId == null) {
            return null;
        }

        String field = properties.getTracing().getSpanIdField();
        if (field != null && properties.getTracing().isIncludeSpanId()) {
            MDC.put(field, spanId);
        }
        return spanId;
    }

    /**
     * Generate a new correlation ID.
     *
     * @return A unique correlation ID
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}

