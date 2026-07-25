package com.adhar.kit.eventsourcing.projection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity that stores a {@link Projection}'s current checkpoint position in the
 * {@code projection_checkpoints} database table.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "projection_checkpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionCheckpointEntry {

    @Id
    @Column(name = "projection_name", nullable = false, updatable = false)
    private String projectionName;

    @Column(name = "position", nullable = false)
    private long position;
}
