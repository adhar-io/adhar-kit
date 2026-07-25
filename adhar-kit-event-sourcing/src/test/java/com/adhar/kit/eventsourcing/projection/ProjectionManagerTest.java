package com.adhar.kit.eventsourcing.projection;

import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.store.EventStore;
import com.adhar.kit.eventsourcing.store.InMemoryEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProjectionManager")
class ProjectionManagerTest {

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + aggregateId + "-" + version, aggregateId, "OrderAggregate",
                version, type, "{}", Instant.now());
    }

    private static class RecordingProjection implements Projection {
        private final String name;
        private final Set<String> interestedTypes;
        final List<DomainEvent> handled = new ArrayList<>();

        RecordingProjection(String name, Set<String> interestedTypes) {
            this.name = name;
            this.interestedTypes = interestedTypes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> interestedEventTypes() {
            return interestedTypes;
        }

        @Override
        public void handle(DomainEvent event) {
            handled.add(event);
        }
    }

    private static class FailingProjection implements Projection {
        int callCount = 0;

        @Override
        public String getName() {
            return "failing";
        }

        @Override
        public Set<String> interestedEventTypes() {
            return Set.of("OrderCreated");
        }

        @Override
        public void handle(DomainEvent event) {
            callCount++;
            throw new RuntimeException("boom");
        }
    }

    @Test
    @DisplayName("register subscribes the projection to its interested event types on the bus")
    void registerSubscribesToInterestedTypes() {
        SimpleEventBus bus = new SimpleEventBus();
        ProjectionCheckpointStore checkpointStore = new InMemoryProjectionCheckpointStore();
        ProjectionManager manager = new ProjectionManager(bus, checkpointStore);
        RecordingProjection projection = new RecordingProjection("orders", Set.of("OrderCreated", "OrderShipped"));

        manager.register(projection);
        bus.publish(event("order-1", 1, "OrderCreated"));
        bus.publish(event("order-1", 2, "OrderShipped"));
        bus.publish(event("order-1", 3, "OrderCancelled"));

        assertThat(projection.handled).hasSize(2);
        assertThat(manager.getProjection("orders")).isSameAs(projection);
    }

    @Test
    @DisplayName("live dispatch advances the projection's checkpoint on each handled event")
    void liveDispatchAdvancesCheckpoint() {
        SimpleEventBus bus = new SimpleEventBus();
        ProjectionCheckpointStore checkpointStore = new InMemoryProjectionCheckpointStore();
        ProjectionManager manager = new ProjectionManager(bus, checkpointStore);
        RecordingProjection projection = new RecordingProjection("orders", Set.of("OrderCreated"));
        manager.register(projection);

        bus.publish(event("order-1", 1, "OrderCreated"));
        assertThat(checkpointStore.getCheckpoint("orders")).isEqualTo(1L);

        bus.publish(event("order-1", 2, "OrderCreated"));
        assertThat(checkpointStore.getCheckpoint("orders")).isEqualTo(2L);
    }

    @Test
    @DisplayName("a failing projection handler is isolated: the exception is caught and other projections still run")
    void failingProjectionIsIsolated() {
        SimpleEventBus bus = new SimpleEventBus();
        ProjectionCheckpointStore checkpointStore = new InMemoryProjectionCheckpointStore();
        ProjectionManager manager = new ProjectionManager(bus, checkpointStore);
        FailingProjection failing = new FailingProjection();
        RecordingProjection healthy = new RecordingProjection("healthy", Set.of("OrderCreated"));
        manager.register(failing);
        manager.register(healthy);

        bus.publish(event("order-1", 1, "OrderCreated"));

        assertThat(failing.callCount).isEqualTo(1);
        assertThat(healthy.handled).hasSize(1);
        // checkpoint is not advanced for the failing projection since handle() threw
        assertThat(checkpointStore.getCheckpoint("failing")).isZero();
        assertThat(checkpointStore.getCheckpoint("healthy")).isEqualTo(1L);
    }

    @Test
    @DisplayName("rebuild replays every event from the store, resetting the checkpoint first")
    void rebuildReplaysAllEventsFromZero() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        eventStore.saveEvents("order-1", List.of(event("order-1", 1, "OrderCreated")), 0);
        eventStore.saveEvents("order-2", List.of(event("order-2", 1, "OrderCreated")), 0);
        eventStore.saveEvents("order-1", List.of(event("order-1", 2, "OrderShipped")), 1);

        SimpleEventBus bus = new SimpleEventBus();
        ProjectionCheckpointStore checkpointStore = new InMemoryProjectionCheckpointStore();
        checkpointStore.saveCheckpoint("orders", 99L);
        ProjectionManager manager = new ProjectionManager(bus, checkpointStore);
        RecordingProjection projection = new RecordingProjection("orders", Set.of("OrderCreated"));
        manager.register(projection);

        manager.rebuild("orders", eventStore);

        assertThat(projection.handled).hasSize(2);
        assertThat(checkpointStore.getCheckpoint("orders")).isEqualTo(3L);
    }

    @Test
    @DisplayName("rebuild isolates a failing projection but still advances the checkpoint through all events")
    void rebuildIsolatesFailures() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        eventStore.saveEvents("order-1", List.of(event("order-1", 1, "OrderCreated")), 0);
        eventStore.saveEvents("order-1", List.of(event("order-1", 2, "OrderShipped")), 1);

        SimpleEventBus bus = new SimpleEventBus();
        ProjectionCheckpointStore checkpointStore = new InMemoryProjectionCheckpointStore();
        ProjectionManager manager = new ProjectionManager(bus, checkpointStore);
        FailingProjection failing = new FailingProjection();
        manager.register(failing);

        manager.rebuild("failing", eventStore);

        assertThat(failing.callCount).isEqualTo(1);
        assertThat(checkpointStore.getCheckpoint("failing")).isEqualTo(2L);
    }

    @Test
    @DisplayName("rebuild throws for an unregistered projection name")
    void rebuildThrowsForUnknownProjection() {
        EventStore eventStore = new InMemoryEventStore();
        ProjectionManager manager = new ProjectionManager(new SimpleEventBus(), new InMemoryProjectionCheckpointStore());

        assertThatThrownBy(() -> manager.rebuild("unknown", eventStore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
