package com.adhar.kit.messaging.outbox;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting transactional-outbox entries.
 * <p>
 * Two implementations ship with the module: {@link InMemoryOutboxStore} (default;
 * survives only for the lifetime of the JVM) and {@link JdbcOutboxStore} (backed by a
 * relational table via a plain {@code JdbcTemplate}, auto-configured when a
 * {@code DataSource} bean is present). Applications may plug in their own store by
 * registering an {@code OutboxStore} bean.
 */
public interface OutboxStore {

    /**
     * Persists a new outbox entry.
     *
     * @param entry the entry to persist
     */
    void save(OutboxEntry entry);

    /**
     * Fetches entries eligible for relay ({@link OutboxStatus#PENDING} or
     * {@link OutboxStatus#FAILED}), oldest first.
     *
     * @param limit maximum number of entries to return
     * @return the eligible entries, oldest first (never {@code null})
     */
    List<OutboxEntry> fetchPending(int limit);

    /**
     * Persists the mutated state (status, attempts, last error/attempt time) of an
     * entry previously returned by {@link #fetchPending(int)}.
     *
     * @param entry the entry to update
     */
    void update(OutboxEntry entry);

    /**
     * Looks up an entry by id.
     *
     * @param id the entry id
     * @return the entry, or empty if unknown
     */
    Optional<OutboxEntry> findById(String id);

    /**
     * Counts entries currently in the given status.
     *
     * @param status the status to count
     * @return the number of entries in that status
     */
    long countByStatus(OutboxStatus status);
}
