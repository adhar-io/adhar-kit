package com.adhar.kit.eventsourcing.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaSagaStateStore")
class JpaSagaStateStoreTest {

    @Mock
    private SagaInstanceEntryRepository repository;

    private JpaSagaStateStore store;

    @BeforeEach
    void setUp() {
        store = new JpaSagaStateStore(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("save maps the instance and serializes its data bag to JSON")
    void savePersistsMappedEntry() {
        SagaInstance instance = new SagaInstance("s1", "OrderSaga", "order-1", 2,
                SagaStatus.RUNNING, "PaymentConfirmed", Map.of("amount", 42));

        store.save(instance);

        ArgumentCaptor<SagaInstanceEntry> captor = ArgumentCaptor.forClass(SagaInstanceEntry.class);
        verify(repository).save(captor.capture());
        SagaInstanceEntry entry = captor.getValue();
        assertThat(entry.getId()).isEqualTo("s1");
        assertThat(entry.getSagaName()).isEqualTo("OrderSaga");
        assertThat(entry.getCorrelationId()).isEqualTo("order-1");
        assertThat(entry.getCurrentStepIndex()).isEqualTo(2);
        assertThat(entry.getStatus()).isEqualTo(SagaStatus.RUNNING);
        assertThat(entry.getAwaitingEventType()).isEqualTo("PaymentConfirmed");
        assertThat(entry.getData()).contains("\"amount\":42");
    }

    @Test
    @DisplayName("findById maps the entry back and deserializes its data")
    void findByIdMapsEntry() {
        SagaInstanceEntry entry = new SagaInstanceEntry("s1", "OrderSaga", "order-1", 1,
                SagaStatus.RUNNING, null, "{\"amount\":7}");
        when(repository.findById("s1")).thenReturn(Optional.of(entry));

        SagaInstance loaded = store.findById("s1").orElseThrow();
        assertThat(loaded.getSagaName()).isEqualTo("OrderSaga");
        assertThat(loaded.getCurrentStepIndex()).isEqualTo(1);
        assertThat(loaded.getData()).containsEntry("amount", 7);
    }

    @Test
    @DisplayName("findById tolerates null/blank data by returning an empty bag")
    void findByIdWithBlankData() {
        SagaInstanceEntry entry = new SagaInstanceEntry("s1", "OrderSaga", "order-1", 0,
                SagaStatus.RUNNING, null, null);
        when(repository.findById("s1")).thenReturn(Optional.of(entry));

        assertThat(store.findById("s1").orElseThrow().getData()).isEmpty();
    }

    @Test
    @DisplayName("findByStatus maps all matching entries")
    void findByStatusMapsEntries() {
        SagaInstanceEntry entry = new SagaInstanceEntry("s1", "OrderSaga", "order-1", 0,
                SagaStatus.COMPENSATED, null, "{}");
        when(repository.findByStatus(SagaStatus.COMPENSATED)).thenReturn(List.of(entry));

        List<SagaInstance> result = store.findByStatus(SagaStatus.COMPENSATED);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo(SagaStatus.COMPENSATED);
    }
}
