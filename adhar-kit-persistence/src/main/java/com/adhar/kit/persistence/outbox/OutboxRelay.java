package com.adhar.kit.persistence.outbox;

/**
 * Strategy for handing a due {@link OutboxEvent} off to whatever actually delivers it
 * (an in-process event bus, a message broker producer, a webhook call, ...).
 *
 * <p>{@link OutboxPublisher} depends only on this SPI, not on any specific transport. The default
 * binding -- {@link ApplicationEventOutboxRelay} -- simply republishes the event through Spring's
 * {@link org.springframework.context.ApplicationEventPublisher}, preserving the module's original
 * behavior. Consumers that want to relay outbox events onto Kafka/RabbitMQ/etc. instead can
 * provide their own {@code OutboxRelay} bean; {@code PersistenceAutoConfiguration} only supplies
 * the default when no other implementation is present.</p>
 *
 * <p>{@link #relay(OutboxEvent)} should throw if delivery fails -- {@link OutboxPublisher}
 * catches the exception and applies the configured retry/backoff/dead-letter policy.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public interface OutboxRelay {

    /**
     * Delivers the given outbox event. Implementations should throw a {@link RuntimeException} on
     * failure so that {@link OutboxPublisher} can schedule a retry.
     *
     * @param event the due outbox event
     */
    void relay(OutboxEvent event);
}
