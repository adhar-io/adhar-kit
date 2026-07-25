package com.adhar.kit.eventsourcing.projection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryProjectionCheckpointStore")
class InMemoryProjectionCheckpointStoreTest {

    @Test
    @DisplayName("getCheckpoint defaults to zero for an unknown projection")
    void getCheckpointDefaultsToZero() {
        InMemoryProjectionCheckpointStore store = new InMemoryProjectionCheckpointStore();

        assertThat(store.getCheckpoint("orders")).isZero();
    }

    @Test
    @DisplayName("saveCheckpoint then getCheckpoint returns the saved position")
    void saveThenGetReturnsSavedPosition() {
        InMemoryProjectionCheckpointStore store = new InMemoryProjectionCheckpointStore();

        store.saveCheckpoint("orders", 42L);

        assertThat(store.getCheckpoint("orders")).isEqualTo(42L);
    }

    @Test
    @DisplayName("checkpoints for different projections are isolated")
    void checkpointsAreIsolatedPerProjection() {
        InMemoryProjectionCheckpointStore store = new InMemoryProjectionCheckpointStore();

        store.saveCheckpoint("orders", 10L);
        store.saveCheckpoint("shipments", 5L);

        assertThat(store.getCheckpoint("orders")).isEqualTo(10L);
        assertThat(store.getCheckpoint("shipments")).isEqualTo(5L);
    }
}
