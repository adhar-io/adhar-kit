package com.adhar.kit.persistence.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Bridges application/domain events to the transactional outbox: any published
 * {@link OutboxedEvent} is persisted as an {@link OutboxEvent} row just {@code BEFORE_COMMIT} of
 * the surrounding transaction, so the write lands atomically with whatever business change
 * triggered the event and survives a crash between commit and delivery.
 *
 * <p>Registered by {@code PersistenceAutoConfiguration} only when
 * {@code adhar.persistence.outbox.bridge-enabled=true} (in addition to
 * {@code adhar.persistence.outbox.enabled=true}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class DomainEventOutboxBridge {

    private static final Logger log = LoggerFactory.getLogger(DomainEventOutboxBridge.class);

    private final OutboxRepository outboxRepository;

    public DomainEventOutboxBridge(OutboxRepository outboxRepository) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
    }

    /**
     * Persists the given domain event as a new, pending outbox row before the enclosing
     * transaction commits.
     *
     * @param event the domain event to bridge
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(OutboxedEvent event) {
        log.debug("Bridging domain event {} for aggregate {}/{} into the outbox",
                event.eventType(), event.aggregateType(), event.aggregateId());
        OutboxEvent outboxEvent = OutboxEvent.create(
                event.aggregateType(), event.aggregateId(), event.eventType(), event.payload());
        outboxRepository.save(outboxEvent);
    }
}
