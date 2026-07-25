package com.adhar.kit.eventsourcing.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EventEntry}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Repository
public interface EventEntryRepository extends JpaRepository<EventEntry, UUID> {

    List<EventEntry> findByAggregateIdOrderByVersionAsc(String aggregateId);

    List<EventEntry> findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(String aggregateId, int version);

    List<EventEntry> findAllByOrderByStoredAtAsc();
}
