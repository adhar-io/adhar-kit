package com.adhar.kit.eventsourcing.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaSnapshotStore")
class JpaSnapshotStoreTest {

    @Mock
    private SnapshotEntryRepository repository;

    @Test
    @DisplayName("save persists a mapped entry")
    void savePersistsMappedEntry() {
        JpaSnapshotStore store = new JpaSnapshotStore(repository);
        Instant timestamp = Instant.parse("2024-01-01T00:00:00Z");
        Snapshot snapshot = new Snapshot("order-1", "OrderAggregate", 10, "{\"x\":1}", timestamp);

        store.save(snapshot);

        ArgumentCaptor<SnapshotEntry> captor = ArgumentCaptor.forClass(SnapshotEntry.class);
        verify(repository).save(captor.capture());
        SnapshotEntry entry = captor.getValue();
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getAggregateId()).isEqualTo("order-1");
        assertThat(entry.getAggregateType()).isEqualTo("OrderAggregate");
        assertThat(entry.getVersion()).isEqualTo(10);
        assertThat(entry.getPayload()).isEqualTo("{\"x\":1}");
        assertThat(entry.getCapturedAt()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("findLatest maps the top entry by version descending")
    void findLatestMapsTopEntry() {
        SnapshotEntry entry = new SnapshotEntry(UUID.randomUUID(), "order-1", "OrderAggregate", 20,
                "{\"x\":2}", Instant.parse("2024-01-02T00:00:00Z"));
        when(repository.findTopByAggregateIdOrderByVersionDesc("order-1")).thenReturn(Optional.of(entry));
        JpaSnapshotStore store = new JpaSnapshotStore(repository);

        Optional<Snapshot> result = store.findLatest("order-1");

        assertThat(result).isPresent();
        assertThat(result.get().aggregateId()).isEqualTo("order-1");
        assertThat(result.get().version()).isEqualTo(20);
        assertThat(result.get().payload()).isEqualTo("{\"x\":2}");
    }

    @Test
    @DisplayName("findLatest returns empty when no snapshot exists")
    void findLatestReturnsEmptyWhenNoneExists() {
        when(repository.findTopByAggregateIdOrderByVersionDesc("order-1")).thenReturn(Optional.empty());
        JpaSnapshotStore store = new JpaSnapshotStore(repository);

        assertThat(store.findLatest("order-1")).isEmpty();
    }
}
