package com.adhar.kit.eventsourcing.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SnapshotEntry}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Repository
public interface SnapshotEntryRepository extends JpaRepository<SnapshotEntry, UUID> {

    Optional<SnapshotEntry> findTopByAggregateIdOrderByVersionDesc(String aggregateId);
}
