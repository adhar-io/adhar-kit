package com.adhar.kit.eventsourcing.subscription;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CatchUpSubscription")
class CatchUpSubscriptionTest {

    private static final String TYPE = "OrderCreated";

    private InMemoryEventStore eventStore;
    private SimpleEventBus eventBus;
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        eventBus = new SimpleEventBus();
        seq.set(0);
    }

    private DomainEvent newEvent(String aggregateId, int version) {
        return new DomainEvent("evt-" + seq.incrementAndGet(), aggregateId, "OrderAggregate",
                version, TYPE, "{}", Instant.now());
    }

    private void persist(String aggregateId, int version) {
        eventStore.saveEvents(aggregateId, List.of(newEvent(aggregateId, version)), version - 1);
    }

    @Test
    @DisplayName("replays all historical events from the beginning and becomes LIVE")
    void replaysHistoryThenGoesLive() {
        persist("order-1", 1);
        persist("order-2", 1);
        List<DomainEvent> handled = new ArrayList<>();

        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        assertThat(handled).hasSize(2);
        assertThat(subscription.getPosition()).isEqualTo(2);
        assertThat(subscription.getState()).isEqualTo(CatchUpSubscription.State.LIVE);
    }

    @Test
    @DisplayName("resuming from a checkpoint skips already-processed events")
    void resumesFromCheckpoint() {
        persist("order-1", 1);
        persist("order-2", 1);
        persist("order-3", 1);
        List<DomainEvent> handled = new ArrayList<>();

        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), handled::add);
        subscription.start(2);

        assertThat(handled).hasSize(1);
        assertThat(handled.getFirst().aggregateId()).isEqualTo("order-3");
        assertThat(subscription.getPosition()).isEqualTo(3);
    }

    @Test
    @DisplayName("live events published after catch-up are dispatched directly and advance the position")
    void dispatchesLiveEventsAfterCatchUp() {
        persist("order-1", 1);
        List<DomainEvent> handled = new ArrayList<>();
        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        eventBus.publish(newEvent("order-2", 1));

        assertThat(handled).hasSize(2);
        assertThat(handled.getLast().aggregateId()).isEqualTo("order-2");
        assertThat(subscription.getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("live events arriving during replay are buffered and drained in order with no gap")
    void buffersLiveEventsDuringReplay() {
        persist("order-1", 1);
        persist("order-2", 1);
        DomainEvent live = newEvent("order-3", 1);
        List<DomainEvent> handled = new ArrayList<>();

        // Store that fires a live event onto the bus at the moment replay reads history, simulating
        // an event arriving mid-catch-up. Because the subscription subscribes before replaying, the
        // event is buffered and drained after history.
        EventStore racingStore = new DelegatingEventStore(eventStore) {
            @Override
            public List<DomainEvent> getAllEvents() {
                eventBus.publish(live);
                return super.getAllEvents();
            }
        };

        CatchUpSubscription subscription = new CatchUpSubscription(racingStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        assertThat(handled).extracting(DomainEvent::aggregateId)
                .containsExactly("order-1", "order-2", "order-3");
        assertThat(subscription.getPosition()).isEqualTo(3);
        assertThat(subscription.getState()).isEqualTo(CatchUpSubscription.State.LIVE);
    }

    @Test
    @DisplayName("an event both persisted and delivered live during replay is handled exactly once")
    void deduplicatesReplayedAndLiveEvent() {
        persist("order-1", 1);
        DomainEvent duplicate = eventStore.getAllEvents().getFirst();
        List<DomainEvent> handled = new ArrayList<>();

        EventStore racingStore = new DelegatingEventStore(eventStore) {
            @Override
            public List<DomainEvent> getAllEvents() {
                eventBus.publish(duplicate);
                return super.getAllEvents();
            }
        };

        CatchUpSubscription subscription = new CatchUpSubscription(racingStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        assertThat(handled).hasSize(1);
        assertThat(subscription.getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("only interested event types are replayed and dispatched")
    void filtersByEventType() {
        eventStore.saveEvents("order-1", List.of(
                new DomainEvent("e1", "order-1", "OrderAggregate", 1, TYPE, "{}", Instant.now())), 0);
        eventStore.saveEvents("pay-1", List.of(
                new DomainEvent("e2", "pay-1", "PaymentAggregate", 1, "PaymentReceived", "{}", Instant.now())), 0);
        List<DomainEvent> handled = new ArrayList<>();

        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        assertThat(handled).hasSize(1);
        assertThat(handled.getFirst().eventType()).isEqualTo(TYPE);
    }

    @Test
    @DisplayName("stop halts dispatch of subsequent live events")
    void stopHaltsDispatch() {
        persist("order-1", 1);
        List<DomainEvent> handled = new ArrayList<>();
        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), handled::add);
        subscription.start();

        subscription.stop();
        eventBus.publish(newEvent("order-2", 1));

        assertThat(handled).hasSize(1);
        assertThat(subscription.getState()).isEqualTo(CatchUpSubscription.State.STOPPED);
    }

    @Test
    @DisplayName("starting twice is rejected")
    void startingTwiceThrows() {
        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), e -> { });
        subscription.start();

        assertThatThrownBy(subscription::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a handler that throws does not abort the subscription")
    void handlerExceptionDoesNotAbort() {
        persist("order-1", 1);
        persist("order-2", 1);
        List<DomainEvent> handled = new ArrayList<>();
        CatchUpSubscription subscription = new CatchUpSubscription(eventStore, eventBus, List.of(TYPE), e -> {
            if (e.aggregateId().equals("order-1")) {
                throw new RuntimeException("boom");
            }
            handled.add(e);
        });

        subscription.start();

        assertThat(handled).extracting(DomainEvent::aggregateId).containsExactly("order-2");
        assertThat(subscription.getPosition()).isEqualTo(2);
    }

    /**
     * Minimal delegating event store so individual tests can override a single method.
     */
    private static class DelegatingEventStore implements EventStore {
        private final EventStore delegate;

        DelegatingEventStore(EventStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
            delegate.saveEvents(aggregateId, events, expectedVersion);
        }

        @Override
        public List<DomainEvent> getEvents(String aggregateId) {
            return delegate.getEvents(aggregateId);
        }

        @Override
        public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
            return delegate.getEventsAfterVersion(aggregateId, version);
        }

        @Override
        public List<DomainEvent> getAllEvents() {
            return delegate.getAllEvents();
        }
    }
}
