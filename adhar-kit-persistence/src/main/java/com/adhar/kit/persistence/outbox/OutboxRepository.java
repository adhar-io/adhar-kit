package com.adhar.kit.persistence.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

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
     * @deprecated superseded by {@link #findBatchForProcessing(Instant, Pageable)}, which also
     * honors retry scheduling ({@code nextAttemptAt}); kept for backward compatibility.
     */
    @Deprecated
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, Pageable pageable);

    /**
     * Fetches a batch of events that are due for (re)publishing -- {@code PENDING} or
     * {@code FAILED} and whose {@code nextAttemptAt} has passed -- locking each selected row with
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} so that multiple {@code OutboxPublisher} instances
     * polling concurrently never process the same event twice.
     *
     * <p>Not every database/dialect supports {@code SKIP LOCKED}; callers should fall back to
     * {@link #findBatchForProcessingNoLock(Instant, Pageable)} if this method throws.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status IN ('PENDING', 'FAILED') AND e.nextAttemptAt <= :now ORDER BY e.createdAt ASC")
    List<OutboxEvent> findBatchForProcessing(Instant now, Pageable pageable);

    /**
     * Same selection as {@link #findBatchForProcessing(Instant, Pageable)} but without any
     * locking hint -- the fallback used when the underlying database does not support
     * {@code SKIP LOCKED}. Safe for single-instance deployments; concurrent multi-instance
     * publishers may occasionally process the same event more than once.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status IN ('PENDING', 'FAILED') AND e.nextAttemptAt <= :now ORDER BY e.createdAt ASC")
    List<OutboxEvent> findBatchForProcessingNoLock(Instant now, Pageable pageable);

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
     * Marks an event as failed (terminal, no further retry). Retained for backward compatibility;
     * new code should prefer {@link #scheduleRetry(UUID, Instant, String)} /
     * {@link #markAsDead(UUID, String)}.
     *
     * @param id the event ID
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'FAILED' WHERE e.id = :id")
    int markAsFailed(UUID id);

    /**
     * Records a failed publish attempt and schedules the next retry.
     *
     * @param id            the event ID
     * @param nextAttemptAt when the event becomes eligible for another attempt
     * @param lastError     message describing the failure
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'FAILED', e.attempts = e.attempts + 1, "
            + "e.nextAttemptAt = :nextAttemptAt, e.lastError = :lastError WHERE e.id = :id")
    int scheduleRetry(UUID id, Instant nextAttemptAt, String lastError);

    /**
     * Marks an event as dead-lettered: attempts have been exhausted and it will not be retried
     * automatically.
     *
     * @param id        the event ID
     * @param lastError message describing the final failure
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'DEAD', e.lastError = :lastError WHERE e.id = :id")
    int markAsDead(UUID id, String lastError);
}
