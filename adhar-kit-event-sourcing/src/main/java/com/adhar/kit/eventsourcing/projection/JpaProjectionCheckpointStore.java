package com.adhar.kit.eventsourcing.projection;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * JPA-backed {@link ProjectionCheckpointStore} implementation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@ConditionalOnClass(EntityManager.class)
public class JpaProjectionCheckpointStore implements ProjectionCheckpointStore {

    private final ProjectionCheckpointEntryRepository repository;

    public JpaProjectionCheckpointStore(ProjectionCheckpointEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public long getCheckpoint(String projectionName) {
        return repository.findById(projectionName)
                .map(ProjectionCheckpointEntry::getPosition)
                .orElse(0L);
    }

    @Override
    public void saveCheckpoint(String projectionName, long position) {
        ProjectionCheckpointEntry entry = repository.findById(projectionName)
                .orElseGet(() -> new ProjectionCheckpointEntry(projectionName, 0L));
        entry.setPosition(position);
        repository.save(entry);
    }
}
