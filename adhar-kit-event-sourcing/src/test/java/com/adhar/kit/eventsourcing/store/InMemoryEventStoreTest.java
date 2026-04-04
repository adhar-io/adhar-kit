package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryEventStore")
class InMemoryEventStoreTest {

    private InMemoryEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
    }

    private DomainEvent createEvent(String aggregateId, int version, String eventType) {
        return new DomainEvent(
                "evt-" + version, aggregateId, "OrderAggregate",
                version, eventType, "{}", Instant.now()
        );
    }

    @Test
    @DisplayName("saveEvents stores events successfully")
    void saveEventsStoresEvents() {
        List<DomainEvent> events = List.of(
                createEvent("order-1", 1, "OrderCreated"),
                createEvent("order-1", 2, "OrderUpdated")
        );

        eventStore.saveEvents("order-1", events, 0);

        List<DomainEvent> stored = eventStore.getEvents("order-1");
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).eventType()).isEqualTo("OrderCreated");
        assertThat(stored.get(1).eventType()).isEqualTo("OrderUpdated");
    }

    @Test
    @DisplayName("getEvents returns all events for aggregate")
    void getEventsReturnsAllEventsForAggregate() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated")
        ), 0);
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 2, "OrderUpdated")
        ), 1);

        List<DomainEvent> events = eventStore.getEvents("order-1");

        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("getEvents returns empty list for unknown aggregate")
    void getEventsReturnsEmptyListForUnknownAggregate() {
        List<DomainEvent> events = eventStore.getEvents("nonexistent");

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("getEventsAfterVersion returns filtered events")
    void getEventsAfterVersionReturnsFilteredEvents() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated"),
                createEvent("order-1", 2, "OrderUpdated"),
                createEvent("order-1", 3, "OrderShipped")
        ), 0);

        List<DomainEvent> events = eventStore.getEventsAfterVersion("order-1", 1);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).version()).isEqualTo(2);
        assertThat(events.get(1).version()).isEqualTo(3);
    }

    @Test
    @DisplayName("getEventsAfterVersion returns empty when no events after version")
    void getEventsAfterVersionReturnsEmptyWhenNoEventsAfterVersion() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated")
        ), 0);

        List<DomainEvent> events = eventStore.getEventsAfterVersion("order-1", 1);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("optimistic concurrency check throws ConcurrencyException on wrong expected version")
    void optimisticConcurrencyCheckThrowsOnWrongVersion() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated")
        ), 0);

        List<DomainEvent> newEvents = List.of(
                createEvent("order-1", 2, "OrderUpdated")
        );

        assertThatThrownBy(() -> eventStore.saveEvents("order-1", newEvents, 0))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("order-1")
                .hasMessageContaining("expected version 0")
                .hasMessageContaining("found 1");
    }

    @Test
    @DisplayName("saveEvents with empty list does not change state")
    void saveEventsWithEmptyListDoesNotChangeState() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated")
        ), 0);

        eventStore.saveEvents("order-1", List.of(), 1);

        List<DomainEvent> events = eventStore.getEvents("order-1");
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("events from different aggregates are isolated")
    void eventsFromDifferentAggregatesAreIsolated() {
        eventStore.saveEvents("order-1", List.of(
                createEvent("order-1", 1, "OrderCreated")
        ), 0);
        eventStore.saveEvents("order-2", List.of(
                createEvent("order-2", 1, "OrderCreated"),
                createEvent("order-2", 2, "OrderUpdated")
        ), 0);

        assertThat(eventStore.getEvents("order-1")).hasSize(1);
        assertThat(eventStore.getEvents("order-2")).hasSize(2);
    }
}
