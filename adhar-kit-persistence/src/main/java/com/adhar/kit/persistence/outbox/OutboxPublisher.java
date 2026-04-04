package com.adhar.kit.persistence.outbox;

import com.adhar.kit.persistence.config.PersistenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table and publishes pending events via Spring's
 * {@link ApplicationEventPublisher}.
 *
 * <p>Runs on a configurable schedule (default 5 000 ms). Events that are
 * successfully published are marked as {@code PROCESSED}; failures are
 * marked as {@code FAILED} so they can be retried or dead-lettered.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PersistenceProperties properties;

    /**
     * Constructs a new {@code OutboxPublisher}.
     *
     * @param outboxRepository the repository for accessing outbox events
     * @param eventPublisher   the Spring event publisher for dispatching events
     * @param properties       persistence configuration properties
     */
    public OutboxPublisher(OutboxRepository outboxRepository,
                           ApplicationEventPublisher eventPublisher,
                           PersistenceProperties properties) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        log.info("OutboxPublisher initialized (pollIntervalMs={}, batchSize={})",
                properties.getOutbox().getPollIntervalMs(),
                properties.getOutbox().getBatchSize());
    }

    /**
     * Scheduled poll that processes pending outbox events.
     * The fixed-delay value is read from configuration.
     */
    @Scheduled(fixedDelayString = "${adhar.persistence.outbox.poll-interval-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        int batchSize = properties.getOutbox().getBatchSize();
        List<OutboxEvent> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                eventPublisher.publishEvent(event);
                outboxRepository.markAsProcessed(event.getId(), Instant.now());
                log.debug("Published outbox event: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                log.error("Failed to publish outbox event: id={}, type={}", event.getId(), event.getEventType(), ex);
                outboxRepository.markAsFailed(event.getId());
            }
        }
    }
}
