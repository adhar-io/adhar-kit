package com.adhar.kit.messaging.outbox;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link OutboxStore}, used when no {@code DataSource} is available
 * (or JDBC is not on the classpath).
 * <p>
 * Entries only survive for the lifetime of the JVM, so this store provides
 * <i>relay-with-retry</i> semantics rather than true transactional-outbox durability;
 * production deployments should use the JDBC-backed store (auto-configured when a
 * {@code DataSource} bean exists) or a custom {@link OutboxStore} bean.
 */
public class InMemoryOutboxStore implements OutboxStore {

    private final Map<String, OutboxEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.getId(), "entry.id");
        entries.put(entry.getId(), entry);
    }

    @Override
    public List<OutboxEntry> fetchPending(int limit) {
        return entries.values().stream()
                .filter(e -> e.getStatus() == OutboxStatus.PENDING || e.getStatus() == OutboxStatus.FAILED)
                .sorted(Comparator.comparing(OutboxEntry::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(OutboxEntry::getId))
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public void update(OutboxEntry entry) {
        save(entry);
    }

    @Override
    public Optional<OutboxEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return entries.values().stream().filter(e -> e.getStatus() == status).count();
    }
}
