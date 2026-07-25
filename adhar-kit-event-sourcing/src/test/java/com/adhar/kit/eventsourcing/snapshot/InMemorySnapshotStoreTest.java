package com.adhar.kit.eventsourcing.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemorySnapshotStore")
class InMemorySnapshotStoreTest {

    @Test
    @DisplayName("findLatest returns empty when no snapshot has been saved")
    void findLatestReturnsEmptyWhenNoneSaved() {
        InMemorySnapshotStore store = new InMemorySnapshotStore();

        assertThat(store.findLatest("order-1")).isEmpty();
    }

    @Test
    @DisplayName("save then findLatest returns the saved snapshot")
    void saveThenFindLatestRoundTrips() {
        InMemorySnapshotStore store = new InMemorySnapshotStore();
        Snapshot snapshot = new Snapshot("order-1", "OrderAggregate", 10, "{\"x\":1}", Instant.now());

        store.save(snapshot);

        assertThat(store.findLatest("order-1")).contains(snapshot);
    }

    @Test
    @DisplayName("saving a newer snapshot overwrites the previous one for the same aggregate")
    void saveOverwritesPreviousSnapshot() {
        InMemorySnapshotStore store = new InMemorySnapshotStore();
        store.save(new Snapshot("order-1", "OrderAggregate", 10, "{\"x\":1}", Instant.now()));
        Snapshot newer = new Snapshot("order-1", "OrderAggregate", 20, "{\"x\":2}", Instant.now());

        store.save(newer);

        assertThat(store.findLatest("order-1")).contains(newer);
    }

    @Test
    @DisplayName("snapshots for different aggregates are isolated")
    void snapshotsAreIsolatedPerAggregate() {
        InMemorySnapshotStore store = new InMemorySnapshotStore();
        Snapshot s1 = new Snapshot("order-1", "OrderAggregate", 10, "{}", Instant.now());
        Snapshot s2 = new Snapshot("order-2", "OrderAggregate", 5, "{}", Instant.now());

        store.save(s1);
        store.save(s2);

        assertThat(store.findLatest("order-1")).contains(s1);
        assertThat(store.findLatest("order-2")).contains(s2);
    }
}
