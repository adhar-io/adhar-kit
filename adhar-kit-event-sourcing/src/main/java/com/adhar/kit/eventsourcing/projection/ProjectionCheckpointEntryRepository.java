package com.adhar.kit.eventsourcing.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ProjectionCheckpointEntry}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Repository
public interface ProjectionCheckpointEntryRepository extends JpaRepository<ProjectionCheckpointEntry, String> {
}
