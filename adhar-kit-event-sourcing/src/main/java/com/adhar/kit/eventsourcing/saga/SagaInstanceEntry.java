package com.adhar.kit.eventsourcing.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity that maps a {@link SagaInstance} to the {@code saga_instances} database table.
 *
 * <p>The saga's free-form data bag is stored as a JSON string in the {@code data} column; the
 * {@link JpaSagaStateStore} owns (de)serialization.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "saga_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstanceEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "saga_name", nullable = false)
    private String sagaName;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status;

    @Column(name = "awaiting_event_type")
    private String awaitingEventType;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;
}
