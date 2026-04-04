package com.adhar.kit.persistence.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities.
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds pending outbox events ordered by creation time.
     *
     * @param pageable pagination parameters (use to limit batch size)
     * @return list of pending events
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, Pageable pageable);

    /**
     * Marks an event as processed.
     *
     * @param id          the event ID
     * @param processedAt the processing timestamp
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PROCESSED', e.processedAt = :processedAt WHERE e.id = :id")
    int markAsProcessed(UUID id, Instant processedAt);

    /**
     * Marks an event as failed.
     *
     * @param id the event ID
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'FAILED' WHERE e.id = :id")
    int markAsFailed(UUID id);
}
