package com.adhar.kit.commons.event;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Base class for domain events following CloudEvents specification.
 *
 * <p>Domain events represent something that happened in the domain
 * that domain experts care about.</p>
 *
 * <p><b>Example - Order Event:</b></p>
 * <pre>{@code
 * public class OrderPlacedEvent extends DomainEvent {
 *     private String orderId;
 *     private String customerId;
 *     private BigDecimal totalAmount;
 *
 *     public OrderPlacedEvent(String orderId, String customerId, BigDecimal totalAmount) {
 *         super();
 *         this.orderId = orderId;
 *         this.customerId = customerId;
 *         this.totalAmount = totalAmount;
 *     }
 *
 *     @Override
 *     public String getEventType() {
 *         return "com.example.order.placed";
 *     }
 *
 *     @Override
 *     public String getAggregateId() {
 *         return orderId;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public abstract class DomainEvent {

    /**
     * Unique event identifier.
     */
    private final String eventId;

    /**
     * Event timestamp.
     */
    private final OffsetDateTime occurredOn;

    /**
     * Event version for schema evolution.
     */
    private final String version;

    /**
     * Correlation ID for tracing across services.
     */
    private String correlationId;

    /**
     * Causation ID (ID of the event/command that caused this event).
     */
    private String causationId;

    /**
     * User who triggered the event.
     */
    private String triggeredBy;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Default constructor.
     */
    protected DomainEvent() {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.occurredOn = OffsetDateTime.now();
        this.version = "1.0";
        this.metadata = new java.util.HashMap<>();
    }

    /**
     * Constructor with correlation ID.
     */
    protected DomainEvent(String correlationId) {
        this();
        this.correlationId = correlationId;
    }

    /**
     * Gets the event type (reverse-DNS format).
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>com.example.order.created</li>
     *   <li>com.example.payment.processed</li>
     *   <li>com.example.user.updated</li>
     * </ul>
     *
     * @return event type
     */
    public abstract String getEventType();

    /**
     * Gets the aggregate/entity ID this event relates to.
     *
     * @return aggregate ID
     */
    public abstract String getAggregateId();

    /**
     * Gets the aggregate type.
     *
     * @return aggregate type (e.g., "Order", "Payment", "User")
     */
    public String getAggregateType() {
        return this.getClass().getSimpleName().replace("Event", "");
    }

    /**
     * Adds metadata.
     *
     * @param key metadata key
     * @param value metadata value
     * @return this event
     */
    public DomainEvent addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Gets metadata value.
     *
     * @param key metadata key
     * @return metadata value or null
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Converts to CloudEvent.
     *
     * @param source event source URI
     * @return CloudEvent instance
     */
    public CloudEvent<DomainEvent> toCloudEvent(java.net.URI source) {
        CloudEvent.CloudEventBuilder<DomainEvent> builder = CloudEvent.<DomainEvent>builder()
            .id(this.eventId)
            .source(source)
            .type(this.getEventType())
            .subject(this.getAggregateType() + "/" + this.getAggregateId())
            .time(this.occurredOn.toInstant())
            .data(this);

        CloudEvent<DomainEvent> event = builder.build();
        event.extensionAttribute("aggregateId", this.getAggregateId());
        event.extensionAttribute("aggregateType", this.getAggregateType());
        event.extensionAttribute("correlationId", this.correlationId);
        event.extensionAttribute("causationId", this.causationId);
        event.extensionAttribute("triggeredBy", this.triggeredBy);

        return event;
    }
}

