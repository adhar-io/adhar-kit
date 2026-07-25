package com.adhar.kit.persistence.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an outbox event for the Transactional Outbox pattern.
 *
 * <p>Events are written to this table within the same transaction as the business
 * operation, then published asynchronously by {@link OutboxPublisher}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Entity
@Table(name = "adhar_outbox_event", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String aggregateType;

    @Column(nullable = false, length = 255)
    private String aggregateId;

    @Column(nullable = false, length = 255)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /** Number of publish attempts made so far (0 before the first attempt). */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    /**
     * Earliest time at which this event is eligible to be (re)published. Set to "now" for a
     * brand-new event, and pushed forward with exponential backoff after each failed attempt.
     */
    @Column(nullable = false)
    private Instant nextAttemptAt;

    /** Message of the most recent publish failure, if any (truncated to fit the column). */
    @Column(length = 2000)
    private String lastError;

    public enum OutboxStatus {
        /** Never attempted, or scheduled for retry and not yet due. */
        PENDING,
        /** Successfully published. */
        PROCESSED,
        /** A publish attempt failed but attempts remain; eligible for retry once due. */
        FAILED,
        /** Exhausted {@code max-attempts}; will not be retried automatically (dead-letter). */
        DEAD
    }

    /**
     * Factory method to create a new pending outbox event, immediately eligible for publishing.
     */
    public static OutboxEvent create(String aggregateType, String aggregateId,
                                     String eventType, String payload) {
        Instant now = Instant.now();
        return OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .createdAt(now)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .build();
    }
}
