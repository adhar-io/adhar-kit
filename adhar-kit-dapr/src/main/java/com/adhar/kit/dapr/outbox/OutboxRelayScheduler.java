package com.adhar.kit.dapr.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Drives {@link OutboxPublisher#relay()} on a fixed schedule. Wired as a bean only when the
 * outbox is enabled; the interval is configured via
 * {@code adhar.dapr.outbox.relay-interval-ms}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class OutboxRelayScheduler {

    private final OutboxPublisher publisher;

    public OutboxRelayScheduler(OutboxPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    /**
     * Relays pending outbox events, swallowing (and logging) any error so a transient failure
     * never breaks the scheduling loop.
     */
    @Scheduled(fixedDelayString = "${adhar.dapr.outbox.relay-interval-ms:5000}")
    public void relay() {
        try {
            OutboxRelayResult result = publisher.relay();
            if (result.total() > 0) {
                log.debug("Outbox relay pass: published={}, retried={}, dead={}",
                    result.getPublished(), result.getRetried(), result.getDead());
            }
        } catch (Exception e) {
            log.error("Outbox relay pass failed", e);
        }
    }
}
