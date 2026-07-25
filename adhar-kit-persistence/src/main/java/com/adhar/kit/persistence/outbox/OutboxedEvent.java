package com.adhar.kit.persistence.outbox;

/**
 * Marker interface for domain/application events that should be persisted as an
 * {@link OutboxEvent} row in the same transaction as the business change that produced them.
 *
 * <p>Publish instances through the normal
 * {@link org.springframework.context.ApplicationEventPublisher} inside a
 * {@code @Transactional} method. When
 * {@code adhar.persistence.outbox.bridge-enabled=true}, {@link DomainEventOutboxBridge} listens
 * for these events at {@code BEFORE_COMMIT} and writes them to the outbox table, so they are
 * durable and get delivered even if the process crashes immediately after commit.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public interface OutboxedEvent {

    /** Logical type of the aggregate this event belongs to (e.g. {@code "Order"}). */
    String aggregateType();

    /** Identifier of the aggregate instance this event belongs to. */
    String aggregateId();

    /** Logical type of the event itself (e.g. {@code "OrderCreated"}). */
    String eventType();

    /** Serialized event payload (typically JSON) that will be stored in the outbox row. */
    String payload();
}
