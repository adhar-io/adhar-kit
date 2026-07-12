package com.adhar.kit.eventsourcing;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.core.TestAggregate;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.store.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventSourcingFacade (extended behavior)")
class EventSourcingFacadeExtendedTest {

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

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + version, aggregateId, "TestAggregate",
                version, type, "{}", Instant.now());
    }

    // ---------- getEventsAfterVersion ----------

    @Test
    @DisplayName("getEventsAfterVersion delegates to store")
    void getEventsAfterVersionDelegates() {
        List<DomainEvent> expected = List.of(event("order-1", 2, "OrderUpdated"));
        when(eventStore.getEventsAfterVersion("order-1", 1)).thenReturn(expected);

        assertThat(facade.getEventsAfterVersion("order-1", 1)).isEqualTo(expected);
    }

    @Test
    @DisplayName("getEventsAfterVersion returns empty list when disabled")
    void getEventsAfterVersionDisabled() {
        EventSourcingFacade disabled = new EventSourcingFacade(null, null, null);
        assertThat(disabled.getEventsAfterVersion("order-1", 1)).isEmpty();
    }

    @Test
    @DisplayName("getEventsAfterVersion rethrows store exception")
    void getEventsAfterVersionRethrows() {
        when(eventStore.getEventsAfterVersion("order-1", 1)).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> facade.getEventsAfterVersion("order-1", 1))
                .isInstanceOf(RuntimeException.class).hasMessage("boom");
    }

    // ---------- error path rethrows ----------

    @Test
    @DisplayName("saveEvents rethrows store exception")
    void saveEventsRethrows() {
        doThrow(new RuntimeException("save-fail"))
                .when(eventStore).saveEvents(anyString(), anyList(), anyInt());

        assertThatThrownBy(() -> facade.saveEvents("order-1", List.of(event("order-1", 1, "X")), 0))
                .isInstanceOf(RuntimeException.class).hasMessage("save-fail");
    }

    @Test
    @DisplayName("getEvents rethrows store exception")
    void getEventsRethrows() {
        when(eventStore.getEvents("order-1")).thenThrow(new RuntimeException("get-fail"));
        assertThatThrownBy(() -> facade.getEvents("order-1"))
                .isInstanceOf(RuntimeException.class).hasMessage("get-fail");
    }

    @Test
    @DisplayName("publish rethrows bus exception")
    void publishRethrows() {
        doThrow(new RuntimeException("pub-fail")).when(eventBus).publish(any());
        assertThatThrownBy(() -> facade.publish(event("order-1", 1, "X")))
                .isInstanceOf(RuntimeException.class).hasMessage("pub-fail");
    }

    @Test
    @DisplayName("subscribe rethrows bus exception")
    void subscribeRethrows() {
        doThrow(new RuntimeException("sub-fail")).when(eventBus).subscribe(anyString(), any());
        assertThatThrownBy(() -> facade.subscribe("X", e -> {}))
                .isInstanceOf(RuntimeException.class).hasMessage("sub-fail");
    }

    // ---------- publishCloudEvent ----------

    @Test
    @DisplayName("publishCloudEvent uses SimpleEventBus.publishAsCloudEvent when bus is SimpleEventBus")
    void publishCloudEventWithSimpleEventBus() {
        SimpleEventBus realBus = new SimpleEventBus();
        List<DomainEvent> received = new ArrayList<>();
        realBus.subscribe("OrderCreated", received::add);
        EventSourcingFacade f = new EventSourcingFacade(eventStore, realBus, aggregateRepository);

        f.publishCloudEvent(event("order-1", 1, "OrderCreated"));

        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("publishCloudEvent falls back to plain publish for non-SimpleEventBus")
    void publishCloudEventFallback() {
        DomainEvent e = event("order-1", 1, "OrderCreated");
        facade.publishCloudEvent(e);
        verify(eventBus).publish(e);
    }

    @Test
    @DisplayName("publishCloudEvent is a no-op when disabled")
    void publishCloudEventDisabled() {
        EventSourcingFacade disabled = new EventSourcingFacade(null, null, null);
        disabled.publishCloudEvent(event("order-1", 1, "OrderCreated"));
        verify(eventBus, never()).publish(any());
    }

    @Test
    @DisplayName("publishCloudEvent rethrows bus exception")
    void publishCloudEventRethrows() {
        doThrow(new RuntimeException("ce-fail")).when(eventBus).publish(any());
        assertThatThrownBy(() -> facade.publishCloudEvent(event("order-1", 1, "X")))
                .isInstanceOf(RuntimeException.class).hasMessage("ce-fail");
    }

    // ---------- loadAggregate ----------

    @Test
    @DisplayName("loadAggregate replays events and commits")
    void loadAggregateSuccess() {
        when(eventStore.getEvents("order-1")).thenReturn(List.of(
                event("order-1", 1, "OrderCreated"),
                event("order-1", 2, "OrderUpdated")
        ));

        TestAggregate aggregate = facade.loadAggregate("order-1", TestAggregate::new);

        assertThat(aggregate.getVersion()).isEqualTo(2);
        assertThat(aggregate.getAppliedEventTypes()).containsExactly("OrderCreated", "OrderUpdated");
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    @DisplayName("loadAggregate throws when disabled")
    void loadAggregateDisabled() {
        EventSourcingFacade disabled = new EventSourcingFacade(null, null, null);
        assertThatThrownBy(() -> disabled.loadAggregate("order-1", TestAggregate::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    @DisplayName("loadAggregate throws when no events exist")
    void loadAggregateNoEvents() {
        when(eventStore.getEvents("order-1")).thenReturn(List.of());
        assertThatThrownBy(() -> facade.loadAggregate("order-1", TestAggregate::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No events found");
    }

    @Test
    @DisplayName("loadAggregate wraps unexpected exceptions in IllegalStateException")
    void loadAggregateWrapsUnexpected() {
        when(eventStore.getEvents("order-1")).thenThrow(new RuntimeException("store down"));
        assertThatThrownBy(() -> facade.loadAggregate("order-1", TestAggregate::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load aggregate")
                .hasRootCauseMessage("store down");
    }

    // ---------- saveAggregate ----------

    @Test
    @DisplayName("saveAggregate persists uncommitted events and publishes them")
    void saveAggregateSuccess() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));
        aggregate.raise(event("order-1", 2, "OrderUpdated"));

        facade.saveAggregate(aggregate);

        verify(eventStore).saveEvents(eq("order-1"), anyList(), eq(0));
        verify(eventBus, times(2)).publish(any(DomainEvent.class));
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    @DisplayName("saveAggregate is a no-op when there are no uncommitted events")
    void saveAggregateNoUncommitted() {
        TestAggregate aggregate = new TestAggregate();

        facade.saveAggregate(aggregate);

        verify(eventStore, never()).saveEvents(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("saveAggregate is a no-op when disabled")
    void saveAggregateDisabled() {
        EventSourcingFacade disabled = new EventSourcingFacade(null, null, null);
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));

        disabled.saveAggregate(aggregate);

        verify(eventStore, never()).saveEvents(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("saveAggregate rethrows store exception")
    void saveAggregateRethrows() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));
        doThrow(new RuntimeException("persist-fail"))
                .when(eventStore).saveEvents(anyString(), anyList(), anyInt());

        assertThatThrownBy(() -> facade.saveAggregate(aggregate))
                .isInstanceOf(RuntimeException.class).hasMessage("persist-fail");
    }

    // ---------- getInstance ----------

    @Test
    @DisplayName("getInstance returns a singleton disabled facade")
    void getInstanceReturnsSingleton() {
        EventSourcingFacade a = EventSourcingFacade.getInstance();
        EventSourcingFacade b = EventSourcingFacade.getInstance();

        assertThat(a).isSameAs(b);
        assertThat(a.isEnabled()).isFalse();
    }
}
