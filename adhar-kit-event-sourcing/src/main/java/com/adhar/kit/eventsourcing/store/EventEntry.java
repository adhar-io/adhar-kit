package com.adhar.kit.eventsourcing.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity that maps a {@link com.adhar.kit.eventsourcing.core.DomainEvent}
 * to the {@code domain_events} database table.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "domain_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "stored_at", nullable = false)
    private Instant storedAt;
}
