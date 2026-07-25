package com.adhar.kit.eventsourcing.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete {@link AggregateRoot} that overrides the snapshot hooks, used by tests to
 * verify snapshot round-tripping.
 */
public class TestSnapshottableAggregate extends AggregateRoot {

    private final List<String> appliedEventTypes = new ArrayList<>();
    private int counter = 0;

    @Override
    protected void apply(DomainEvent event) {
        if (getAggregateId() == null) {
            setAggregateId(event.aggregateId());
        }
        appliedEventTypes.add(event.eventType());
        counter++;
    }

    public List<String> getAppliedEventTypes() {
        return appliedEventTypes;
    }

    public int getCounter() {
        return counter;
    }

    /** Raises a new event on this aggregate (records it as uncommitted). */
    public void raise(DomainEvent event) {
        applyEvent(event);
    }

    @Override
    public String createSnapshotState() {
        return "{\"counter\":" + counter + "}";
    }

    @Override
    public void restoreFromSnapshot(String state, int version) {
        // simplistic hand-rolled parse to avoid a Jackson test dependency here
        String digits = state.replaceAll("[^0-9]", "");
        this.counter = digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
