package com.adhar.kit.eventsourcing.repository;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.AggregateRoot;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.core.TestAggregate;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AggregateRepository")
class AggregateRepositoryTest {

    @Mock
    private EventStore eventStore;

    @Mock
    private EventBus eventBus;

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + version, aggregateId, "TestAggregate",
                version, type, "{}", Instant.now());
    }

    /** Aggregate without a no-arg constructor to exercise the reflective failure path. */
    public static class NoDefaultCtorAggregate extends AggregateRoot {
        public NoDefaultCtorAggregate(String required) {
            // intentionally no no-arg constructor
        }

        @Override
        protected void apply(DomainEvent event) {
            // no-op
        }
    }

    @Test
    @DisplayName("load replays stored events and reconstitutes aggregate")
    void loadReplaysEvents() {
        when(eventStore.getEvents("order-1")).thenReturn(List.of(
                event("order-1", 1, "OrderCreated"),
                event("order-1", 2, "OrderUpdated")
        ));
        AggregateRepository repository = new AggregateRepository(eventStore, eventBus);

        TestAggregate aggregate = repository.load("order-1", TestAggregate.class);

        assertThat(aggregate.getAggregateId()).isEqualTo("order-1");
        assertThat(aggregate.getVersion()).isEqualTo(2);
        assertThat(aggregate.getAppliedEventTypes()).containsExactly("OrderCreated", "OrderUpdated");
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("load throws when no events exist")
    void loadThrowsWhenNoEvents() {
        when(eventStore.getEvents("missing")).thenReturn(List.of());
        AggregateRepository repository = new AggregateRepository(eventStore, eventBus);

        assertThatThrownBy(() -> repository.load("missing", TestAggregate.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No events found");
    }

    @Test
    @DisplayName("load throws when aggregate cannot be instantiated")
    void loadThrowsOnReflectionFailure() {
        when(eventStore.getEvents("order-1")).thenReturn(List.of(event("order-1", 1, "OrderCreated")));
        AggregateRepository repository = new AggregateRepository(eventStore, eventBus);

        assertThatThrownBy(() -> repository.load("order-1", NoDefaultCtorAggregate.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to instantiate aggregate");
    }

    @Test
    @DisplayName("save persists uncommitted events, publishes them, and clears them")
    void savePersistsAndPublishes() {
        AggregateRepository repository = new AggregateRepository(eventStore, eventBus);
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));
        aggregate.raise(event("order-1", 2, "OrderUpdated"));

        repository.save(aggregate);

        verify(eventStore).saveEvents(eq("order-1"), anyList(), eq(0));
        verify(eventBus, times(2)).publish(any(DomainEvent.class));
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    @DisplayName("save with no uncommitted events is a no-op")
    void saveNoUncommittedIsNoOp() {
        AggregateRepository repository = new AggregateRepository(eventStore, eventBus);
        TestAggregate aggregate = new TestAggregate();

        repository.save(aggregate);

        verifyNoInteractions(eventStore);
        verifyNoInteractions(eventBus);
    }

    @Test
    @DisplayName("end-to-end with real in-memory store and event bus")
    void endToEndWithRealCollaborators() {
        InMemoryEventStore store = new InMemoryEventStore();
        SimpleEventBus bus = new SimpleEventBus();
        List<DomainEvent> published = new ArrayList<>();
        bus.subscribe("OrderCreated", published::add);

        AggregateRepository repository = new AggregateRepository(store, bus);

        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-9", 1, "OrderCreated"));
        repository.save(aggregate);

        assertThat(published).hasSize(1);

        TestAggregate loaded = repository.load("order-9", TestAggregate.class);
        assertThat(loaded.getVersion()).isEqualTo(1);
        assertThat(loaded.getAppliedEventTypes()).containsExactly("OrderCreated");
    }
}
