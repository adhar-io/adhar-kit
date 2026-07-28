package com.adhar.kit.eventsourcing.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemorySagaStateStore")
class InMemorySagaStateStoreTest {

    private InMemorySagaStateStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySagaStateStore();
    }

    private SagaInstance instance(String id, SagaStatus status) {
        Map<String, Object> data = new HashMap<>();
        data.put("k", "v");
        return new SagaInstance(id, "OrderSaga", "order-1", 0, status, null, data);
    }

    @Test
    @DisplayName("save then findById returns an equal but isolated copy")
    void saveAndFindById() {
        store.save(instance("s1", SagaStatus.RUNNING));

        SagaInstance loaded = store.findById("s1").orElseThrow();
        assertThat(loaded.getId()).isEqualTo("s1");
        assertThat(loaded.getData()).containsEntry("k", "v");

        // Mutating the returned copy must not affect stored state.
        loaded.getData().put("k", "mutated");
        assertThat(store.findById("s1").orElseThrow().getData()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdUnknown() {
        assertThat(store.findById("missing")).isEmpty();
    }

    @Test
    @DisplayName("findByStatus filters by status")
    void findByStatus() {
        store.save(instance("s1", SagaStatus.RUNNING));
        store.save(instance("s2", SagaStatus.RUNNING));
        store.save(instance("s3", SagaStatus.COMPLETED));

        assertThat(store.findByStatus(SagaStatus.RUNNING)).extracting(SagaInstance::getId)
                .containsExactlyInAnyOrder("s1", "s2");
        assertThat(store.findByStatus(SagaStatus.COMPLETED)).extracting(SagaInstance::getId)
                .containsExactly("s3");
    }

    @Test
    @DisplayName("save overwrites an existing instance with the same id")
    void saveOverwrites() {
        store.save(instance("s1", SagaStatus.RUNNING));
        SagaInstance updated = instance("s1", SagaStatus.COMPLETED);
        store.save(updated);

        assertThat(store.findById("s1").orElseThrow().getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(store.findByStatus(SagaStatus.RUNNING)).isEmpty();
    }
}
