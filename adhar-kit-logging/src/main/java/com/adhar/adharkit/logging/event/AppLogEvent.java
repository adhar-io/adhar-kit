package com.adhar.adharkit.logging.event;

import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, structured application log event.
 *
 * <p>{@code AppLogEvent} is the single event model used for every kind of tracked activity in a
 * running application: business milestones, technical operations, REST API exchanges, batch job
 * progress, performance measurements, audit trail entries and security events
 * (see {@link AppLogEventType}).</p>
 *
 * <p>Events are created through the {@link Builder} and published via
 * {@link AppLogEventPublisher}, which enriches them with MDC context (correlation ID, trace ID,
 * user, tenant), applies sensitive-data masking and dispatches them to all registered
 * {@link AppLogEventSink}s.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * publisher.publish(AppLogEvent.builder()
 *         .type(AppLogEventType.BUSINESS)
 *         .category("order")
 *         .name("ORDER_PLACED")
 *         .message("Order placed successfully")
 *         .outcome(AppLogEventOutcome.SUCCESS)
 *         .metadata("orderId", "ORD-1042")
 *         .metadata("amount", 100.50)
 *         .build());
 * }</pre>
 */
public final class AppLogEvent {

    private final String eventId;
    private final Instant timestamp;
    private final AppLogEventType type;
    private final String category;
    private final String name;
    private final String message;
    private final AppLogEventOutcome outcome;
    private final Level severity;
    private final String source;
    private final Long durationMs;
    private final String correlationId;
    private final String traceId;
    private final String spanId;
    private final String userId;
    private final String tenantId;
    private final String errorType;
    private final String errorMessage;
    private final Map<String, Object> metadata;
    private final List<String> tags;

    private AppLogEvent(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.type = builder.type != null ? builder.type : AppLogEventType.OPERATION;
        this.category = builder.category;
        this.name = builder.name;
        this.message = builder.message;
        this.outcome = builder.outcome != null ? builder.outcome : AppLogEventOutcome.SUCCESS;
        this.severity = builder.severity != null ? builder.severity : Level.INFO;
        this.source = builder.source;
        this.durationMs = builder.durationMs;
        this.correlationId = builder.correlationId;
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.userId = builder.userId;
        this.tenantId = builder.tenantId;
        this.errorType = builder.errorType;
        this.errorMessage = builder.errorMessage;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.tags = List.copyOf(builder.tags);
    }

    /**
     * Creates a new event builder.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated with this event's values (used for enrichment).
     *
     * @return a builder copy of this event
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.eventId = eventId;
        builder.timestamp = timestamp;
        builder.type = type;
        builder.category = category;
        builder.name = name;
        builder.message = message;
        builder.outcome = outcome;
        builder.severity = severity;
        builder.source = source;
        builder.durationMs = durationMs;
        builder.correlationId = correlationId;
        builder.traceId = traceId;
        builder.spanId = spanId;
        builder.userId = userId;
        builder.tenantId = tenantId;
        builder.errorType = errorType;
        builder.errorMessage = errorMessage;
        builder.metadata.putAll(metadata);
        builder.tags.addAll(tags);
        return builder;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public AppLogEventType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    public AppLogEventOutcome getOutcome() {
        return outcome;
    }

    public Level getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<String> getTags() {
        return tags;
    }

    /**
     * Renders the event as an ordered map, skipping null/empty fields. This is the canonical
     * serialization form used by sinks.
     *
     * @return an ordered map of the event's non-empty fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId);
        map.put("timestamp", timestamp.toString());
        map.put("type", type.name());
        putIfPresent(map, "category", category);
        putIfPresent(map, "name", name);
        putIfPresent(map, "message", message);
        map.put("outcome", outcome.name());
        putIfPresent(map, "source", source);
        if (durationMs != null) {
            map.put("durationMs", durationMs);
        }
        putIfPresent(map, "correlationId", correlationId);
        putIfPresent(map, "traceId", traceId);
        putIfPresent(map, "spanId", spanId);
        putIfPresent(map, "userId", userId);
        putIfPresent(map, "tenantId", tenantId);
        putIfPresent(map, "errorType", errorType);
        putIfPresent(map, "errorMessage", errorMessage);
        if (!metadata.isEmpty()) {
            map.put("metadata", metadata);
        }
        if (!tags.isEmpty()) {
            map.put("tags", tags);
        }
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "AppLogEvent" + toMap();
    }

    /**
     * Builder for {@link AppLogEvent}.
     */
    public static final class Builder {
        private String eventId;
        private Instant timestamp;
        private AppLogEventType type;
        private String category;
        private String name;
        private String message;
        private AppLogEventOutcome outcome;
        private Level severity;
        private String source;
        private Long durationMs;
        private String correlationId;
        private String traceId;
        private String spanId;
        private String userId;
        private String tenantId;
        private String errorType;
        private String errorMessage;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final List<String> tags = new ArrayList<>();

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder type(AppLogEventType type) {
            this.type = type;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder outcome(AppLogEventOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder severity(Level severity) {
            this.severity = severity;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder source(Class<?> source) {
            this.source = source != null ? source.getName() : null;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Records error details, deriving the type and message from the given throwable.
         *
         * @param throwable the error (ignored when null)
         * @return this builder
         */
        public Builder error(Throwable throwable) {
            if (throwable != null) {
                this.errorType = throwable.getClass().getName();
                this.errorMessage = throwable.getMessage();
            }
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Adds a single metadata entry (null keys are ignored).
         *
         * @param key   metadata key
         * @param value metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            if (key != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        /**
         * Adds all entries of the given map as metadata (null map is ignored).
         *
         * @param metadata metadata entries
         * @return this builder
         */
        public Builder metadata(Map<String, ?> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /**
         * Replaces the metadata map entirely (used by the publisher after masking).
         *
         * @param metadata new metadata content (null clears)
         * @return this builder
         */
        public Builder replaceMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /**
         * Adds categorization tags (nulls ignored).
         *
         * @param tags tags to add
         * @return this builder
         */
        public Builder tags(String... tags) {
            if (tags != null) {
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        this.tags.add(tag);
                    }
                }
            }
            return this;
        }

        /**
         * Builds the immutable event, applying defaults for eventId, timestamp, type, outcome
         * and severity.
         *
         * @return the built event
         */
        public AppLogEvent build() {
            return new AppLogEvent(this);
        }
    }
}
