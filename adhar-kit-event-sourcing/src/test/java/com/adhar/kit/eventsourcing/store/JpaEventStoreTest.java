package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.upcast.EventUpcaster;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaEventStore")
class JpaEventStoreTest {

    @Mock
    private EventEntryRepository repository;

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + version, aggregateId, "OrderAggregate",
                version, type, "{\"k\":\"v\"}", Instant.parse("2024-01-01T00:00:00Z"));
    }

    private EventEntry entry(String aggregateId, int version, String type) {
        return new EventEntry(UUID.randomUUID(), aggregateId, "OrderAggregate", version,
                type, "{\"k\":\"v\"}", Instant.parse("2024-01-01T00:00:00Z"), Instant.now());
    }

    @Test
    @DisplayName("saveEvents persists mapped entries when version matches (empty stream)")
    void saveEventsPersistsWhenVersionMatches() {
        when(repository.findByAggregateIdOrderByVersionAsc("order-1")).thenReturn(List.of());
        JpaEventStore store = new JpaEventStore(repository);

        store.saveEvents("order-1", List.of(event("order-1", 1, "OrderCreated")), 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<EventEntry> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        EventEntry e = saved.getFirst();
        assertThat(e.getId()).isNull();
        assertThat(e.getAggregateId()).isEqualTo("order-1");
        assertThat(e.getAggregateType()).isEqualTo("OrderAggregate");
        assertThat(e.getVersion()).isEqualTo(1);
        assertThat(e.getEventType()).isEqualTo("OrderCreated");
        assertThat(e.getPayload()).isEqualTo("{\"k\":\"v\"}");
        assertThat(e.getStoredAt()).isNotNull();
    }

    @Test
    @DisplayName("saveEvents uses latest stored version for concurrency check on existing stream")
    void saveEventsWithExistingStream() {
        when(repository.findByAggregateIdOrderByVersionAsc("order-1"))
                .thenReturn(List.of(entry("order-1", 1, "OrderCreated")));
        JpaEventStore store = new JpaEventStore(repository);

        store.saveEvents("order-1", List.of(event("order-1", 2, "OrderUpdated")), 1);

        verify(repository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("saveEvents throws ConcurrencyException on version mismatch and does not persist")
    void saveEventsThrowsOnVersionMismatch() {
        when(repository.findByAggregateIdOrderByVersionAsc("order-1"))
                .thenReturn(List.of(entry("order-1", 1, "OrderCreated")));
        JpaEventStore store = new JpaEventStore(repository);

        assertThatThrownBy(() -> store.saveEvents("order-1", List.of(event("order-1", 2, "OrderUpdated")), 0))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("order-1")
                .hasMessageContaining("expected version 0")
                .hasMessageContaining("found 1");

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("getEvents maps entries to domain events")
    void getEventsMapsEntries() {
        EventEntry e1 = entry("order-1", 1, "OrderCreated");
        EventEntry e2 = entry("order-1", 2, "OrderUpdated");
        when(repository.findByAggregateIdOrderByVersionAsc("order-1")).thenReturn(List.of(e1, e2));
        JpaEventStore store = new JpaEventStore(repository);

        List<DomainEvent> events = store.getEvents("order-1");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).eventId()).isEqualTo(e1.getId().toString());
        assertThat(events.get(0).aggregateId()).isEqualTo("order-1");
        assertThat(events.get(0).aggregateType()).isEqualTo("OrderAggregate");
        assertThat(events.get(0).version()).isEqualTo(1);
        assertThat(events.get(0).eventType()).isEqualTo("OrderCreated");
        assertThat(events.get(0).payload()).isEqualTo("{\"k\":\"v\"}");
        assertThat(events.get(0).occurredAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(events.get(1).version()).isEqualTo(2);
    }

    @Test
    @DisplayName("getEventsAfterVersion maps filtered entries")
    void getEventsAfterVersionMapsEntries() {
        when(repository.findByAggregateIdAndVersionGreaterThanOrderByVersionAsc("order-1", 1))
                .thenReturn(List.of(entry("order-1", 2, "OrderUpdated")));
        JpaEventStore store = new JpaEventStore(repository);

        List<DomainEvent> events = store.getEventsAfterVersion("order-1", 1);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().version()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAllEvents maps entries fetched in stored-at order")
    void getAllEventsMapsEntries() {
        EventEntry e1 = entry("order-1", 1, "OrderCreated");
        EventEntry e2 = entry("order-2", 1, "OrderCreated");
        when(repository.findAllByOrderByStoredAtAsc()).thenReturn(List.of(e1, e2));
        JpaEventStore store = new JpaEventStore(repository);

        List<DomainEvent> events = store.getAllEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).aggregateId()).isEqualTo("order-1");
        assertThat(events.get(1).aggregateId()).isEqualTo("order-2");
    }

    @Test
    @DisplayName("events read via getEvents, getEventsAfterVersion and getAllEvents are migrated through the upcaster chain")
    void eventsAreUpcastOnRead() {
        EventUpcaster upcaster = new EventUpcaster() {
            @Override
            public boolean supports(String eventType, int version) {
                return "OrderCreatedV1".equals(eventType);
            }

            @Override
            public DomainEvent upcast(DomainEvent event) {
                return new DomainEvent(event.eventId(), event.aggregateId(), event.aggregateType(),
                        event.version(), "OrderCreatedV2", "{\"migrated\":true}", event.occurredAt());
            }
        };
        EventEntry legacyEntry = entry("order-1", 1, "OrderCreatedV1");
        when(repository.findByAggregateIdOrderByVersionAsc("order-1")).thenReturn(List.of(legacyEntry));
        when(repository.findByAggregateIdAndVersionGreaterThanOrderByVersionAsc("order-1", 0))
                .thenReturn(List.of(legacyEntry));
        when(repository.findAllByOrderByStoredAtAsc()).thenReturn(List.of(legacyEntry));

        JpaEventStore store = new JpaEventStore(repository, new UpcasterChain(List.of(upcaster)));

        assertThat(store.getEvents("order-1").getFirst().eventType()).isEqualTo("OrderCreatedV2");
        assertThat(store.getEventsAfterVersion("order-1", 0).getFirst().eventType()).isEqualTo("OrderCreatedV2");
        assertThat(store.getAllEvents().getFirst().eventType()).isEqualTo("OrderCreatedV2");
        assertThat(store.getAllEvents().getFirst().payload()).isEqualTo("{\"migrated\":true}");
    }
}
