package com.adhar.kit.eventsourcing.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AggregateRoot")
class AggregateRootTest {

    private DomainEvent event(String aggregateId, int version, String type) {
        return new DomainEvent("evt-" + version, aggregateId, "TestAggregate",
                version, type, "{\"v\":" + version + "}", Instant.now());
    }

    @Test
    @DisplayName("applyEvent mutates state, records uncommitted, and updates version")
    void applyEventUpdatesState() {
        TestAggregate aggregate = new TestAggregate();

        aggregate.raise(event("order-1", 1, "OrderCreated"));
        aggregate.raise(event("order-1", 2, "OrderUpdated"));

        assertThat(aggregate.getAggregateId()).isEqualTo("order-1");
        assertThat(aggregate.getVersion()).isEqualTo(2);
        assertThat(aggregate.getAppliedEventTypes()).containsExactly("OrderCreated", "OrderUpdated");
        assertThat(aggregate.getLastPayload()).isEqualTo("{\"v\":2}");
        assertThat(aggregate.getUncommittedEvents()).hasSize(2);
    }

    @Test
    @DisplayName("new aggregate starts at version 0 with no uncommitted events")
    void newAggregateDefaults() {
        TestAggregate aggregate = new TestAggregate();

        assertThat(aggregate.getVersion()).isZero();
        assertThat(aggregate.getAggregateId()).isNull();
        assertThat(aggregate.getUncommittedEvents()).isEmpty();
    }

    @Test
    @DisplayName("getUncommittedEvents returns an unmodifiable view")
    void uncommittedEventsAreUnmodifiable() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));

        List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();

        assertThatThrownBy(() -> uncommitted.add(event("order-1", 2, "X")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("markEventsAsCommitted clears uncommitted events but keeps version")
    void markEventsAsCommittedClears() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 1, "OrderCreated"));
        aggregate.raise(event("order-1", 2, "OrderUpdated"));

        aggregate.markEventsAsCommitted();

        assertThat(aggregate.getUncommittedEvents()).isEmpty();
        assertThat(aggregate.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("setVersion is reflected by getVersion")
    void setVersionWorks() {
        TestAggregate aggregate = new TestAggregate();
        aggregate.raise(event("order-1", 5, "OrderCreated"));

        assertThat(aggregate.getVersion()).isEqualTo(5);
    }
}
