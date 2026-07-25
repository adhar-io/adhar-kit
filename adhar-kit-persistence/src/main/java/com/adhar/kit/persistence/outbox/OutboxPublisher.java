package com.adhar.kit.persistence.outbox;

import com.adhar.kit.persistence.config.PersistenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table and publishes due events via a pluggable {@link OutboxRelay}.
 *
 * <p><b>Retry / backoff / dead-lettering:</b> when {@link OutboxRelay#relay(OutboxEvent)} throws,
 * the event's {@code attempts} counter is incremented and it is rescheduled with exponential
 * backoff ({@code initial-backoff-ms * backoff-multiplier ^ (attempts - 1)}, capped at
 * {@code max-backoff-ms}). Once {@code attempts} reaches {@code max-attempts}, the event is
 * marked {@code DEAD} instead and is no longer picked up by subsequent polls.</p>
 *
 * <p><b>Multi-instance safety:</b> the batch fetch uses {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * (via {@link OutboxRepository#findBatchForProcessing}) so that multiple {@code OutboxPublisher}
 * instances polling the same table concurrently do not process the same event twice. Not every
 * database supports {@code SKIP LOCKED}; if the locked query fails, this class logs a warning
 * once and falls back to {@link OutboxRepository#findBatchForProcessingNoLock} for the remainder
 * of its lifetime (safe for single-instance deployments; concurrent multi-instance publishers may
 * then occasionally double-process an event).</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final OutboxRelay relay;
    private final PersistenceProperties properties;

    private volatile boolean skipLockedSupported = true;

    /**
     * Constructs a new {@code OutboxPublisher}.
     *
     * @param outboxRepository the repository for accessing outbox events
     * @param relay            the relay used to actually deliver due events
     * @param properties       persistence configuration properties
     */
    public OutboxPublisher(OutboxRepository outboxRepository,
                           OutboxRelay relay,
                           PersistenceProperties properties) {
        this.outboxRepository = outboxRepository;
        this.relay = relay;
        this.properties = properties;
        log.info("OutboxPublisher initialized (pollIntervalMs={}, batchSize={}, maxAttempts={})",
                properties.getOutbox().getPollIntervalMs(),
                properties.getOutbox().getBatchSize(),
                properties.getOutbox().getMaxAttempts());
    }

    /**
     * Scheduled poll that processes due outbox events.
     * The fixed-delay value is read from configuration.
     */
    @Scheduled(fixedDelayString = "${adhar.persistence.outbox.poll-interval-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        int batchSize = properties.getOutbox().getBatchSize();
        List<OutboxEvent> due = fetchBatch(batchSize);

        if (due.isEmpty()) {
            return;
        }

        log.debug("Processing {} due outbox events", due.size());

        for (OutboxEvent event : due) {
            try {
                relay.relay(event);
                outboxRepository.markAsProcessed(event.getId(), Instant.now());
                log.debug("Published outbox event: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
    }

    private List<OutboxEvent> fetchBatch(int batchSize) {
        Instant now = Instant.now();
        PageRequest pageable = PageRequest.of(0, batchSize);
        if (skipLockedSupported) {
            try {
                return outboxRepository.findBatchForProcessing(now, pageable);
            } catch (Exception ex) {
                skipLockedSupported = false;
                log.warn("Pessimistic SKIP LOCKED batch fetch failed (likely unsupported by the "
                        + "underlying database); falling back to an unlocked fetch for the "
                        + "remainder of this publisher's lifetime. Concurrent multi-instance "
                        + "publishers may occasionally process the same event more than once. "
                        + "cause={}", ex.getMessage());
            }
        }
        return outboxRepository.findBatchForProcessingNoLock(now, pageable);
    }

    private void handleFailure(OutboxEvent event, Exception ex) {
        int attempts = event.getAttempts() + 1;
        int maxAttempts = properties.getOutbox().getMaxAttempts();
        String errorMessage = truncate(ex.getMessage() != null ? ex.getMessage() : ex.toString());

        if (attempts >= maxAttempts) {
            log.error("Outbox event id={}, type={} failed after {} attempts; marking DEAD",
                    event.getId(), event.getEventType(), attempts, ex);
            outboxRepository.markAsDead(event.getId(), errorMessage);
            return;
        }

        Instant nextAttemptAt = Instant.now().plusMillis(computeBackoffMs(attempts));
        log.warn("Outbox event id={}, type={} failed (attempt {}/{}); retrying at {}",
                event.getId(), event.getEventType(), attempts, maxAttempts, nextAttemptAt, ex);
        outboxRepository.scheduleRetry(event.getId(), nextAttemptAt, errorMessage);
    }

    private long computeBackoffMs(int attempts) {
        PersistenceProperties.Outbox outbox = properties.getOutbox();
        double backoff = outbox.getInitialBackoffMs() * Math.pow(outbox.getBackoffMultiplier(), attempts - 1);
        return Math.min((long) backoff, outbox.getMaxBackoffMs());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
