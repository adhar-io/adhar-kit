package com.adhar.kit.eventsourcing;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventSourcingFacade")
class EventSourcingFacadeTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private EventBus eventBus;

    @Mock
    private AggregateRepository aggregateRepository;

    private EventSourcingFacade facade;

    @BeforeEach
    void setUp() {
        facade = new EventSourcingFacade(eventStore, eventBus, aggregateRepository);
    }

    private DomainEvent createEvent(String aggregateId, int version, String eventType) {
        return new DomainEvent(
                "evt-" + version, aggregateId, "TestAggregate",
                version, eventType, "{}", Instant.now()
        );
    }

    @Test
    @DisplayName("saveEvents delegates to EventStore")
    void saveEventsDelegatesToEventStore() {
        List<DomainEvent> events = List.of(
                createEvent("order-1", 1, "OrderCreated")
        );

        facade.saveEvents("order-1", events, 0);

        verify(eventStore).saveEvents("order-1", events, 0);
    }

    @Test
    @DisplayName("getEvents delegates to EventStore")
    void getEventsDelegatesToEventStore() {
        List<DomainEvent> expected = List.of(
                createEvent("order-1", 1, "OrderCreated")
        );
        when(eventStore.getEvents("order-1")).thenReturn(expected);

        List<DomainEvent> result = facade.getEvents("order-1");

        assertThat(result).isEqualTo(expected);
        verify(eventStore).getEvents("order-1");
    }

    @Test
    @DisplayName("publish delegates to EventBus")
    @SuppressWarnings("unchecked")
    void publishDelegatesToEventBus() {
        DomainEvent event = createEvent("order-1", 1, "OrderCreated");

        facade.publish(event);

        verify(eventBus).publish(event);
    }

    @Test
    @DisplayName("subscribe delegates to EventBus")
    @SuppressWarnings("unchecked")
    void subscribeDelegatesToEventBus() {
        Consumer<DomainEvent> handler = e -> {};

        facade.subscribe("OrderCreated", handler);

        verify(eventBus).subscribe("OrderCreated", handler);
    }

    @Test
    @DisplayName("isEnabled returns true when all components present")
    void isEnabledReturnsTrueWhenAllComponentsPresent() {
        assertThat(facade.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled returns false when disabled (null dependencies)")
    void isEnabledReturnsFalseWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);

        assertThat(disabledFacade.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled returns false when eventStore is null")
    void isEnabledReturnsFalseWhenEventStoreIsNull() {
        EventSourcingFacade partialFacade = new EventSourcingFacade(null, eventBus, aggregateRepository);

        assertThat(partialFacade.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEnabled returns false when eventBus is null")
    void isEnabledReturnsFalseWhenEventBusIsNull() {
        EventSourcingFacade partialFacade = new EventSourcingFacade(eventStore, null, aggregateRepository);

        assertThat(partialFacade.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("saveEvents fails loudly when disabled instead of dropping events")
    void saveEventsThrowsWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);
        List<DomainEvent> events = List.of(createEvent("order-1", 1, "OrderCreated"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> disabledFacade.saveEvents("order-1", events, 0));

        verifyNoInteractions(eventStore);
    }

    @Test
    @DisplayName("getEvents returns empty list when disabled")
    void getEventsReturnsEmptyListWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);

        List<DomainEvent> result = disabledFacade.getEvents("order-1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("publish fails loudly when disabled instead of dropping the event")
    void publishThrowsWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);
        DomainEvent event = createEvent("order-1", 1, "OrderCreated");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> disabledFacade.publish(event));

        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("subscribe is no-op when disabled")
    void subscribeIsNoOpWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);

        disabledFacade.subscribe("OrderCreated", e -> {});

        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("health returns correct status when enabled")
    void healthReturnsCorrectStatusWhenEnabled() {
        var health = facade.health();

        assertThat(health).containsEntry("enabled", true);
        assertThat(health.get("eventStoreType")).isNotNull();
        assertThat(health.get("eventBusType")).isNotNull();
    }

    @Test
    @DisplayName("health returns correct status when disabled")
    void healthReturnsCorrectStatusWhenDisabled() {
        EventSourcingFacade disabledFacade = new EventSourcingFacade(null, null, null);

        var health = disabledFacade.health();

        assertThat(health).containsEntry("enabled", false);
        assertThat(health).containsEntry("eventStoreType", "none");
        assertThat(health).containsEntry("eventBusType", "none");
    }
}
