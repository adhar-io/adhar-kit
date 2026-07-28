package com.adhar.kit.eventsourcing.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SagaInstanceEntry}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Repository
public interface SagaInstanceEntryRepository extends JpaRepository<SagaInstanceEntry, String> {

    List<SagaInstanceEntry> findByStatus(SagaStatus status);
}
