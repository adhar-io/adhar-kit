package com.adhar.kit.eventsourcing.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete {@link AggregateRoot} used by tests to verify apply/replay behavior.
 */
public class TestAggregate extends AggregateRoot {

    private final List<String> appliedEventTypes = new ArrayList<>();
    private String lastPayload;

    @Override
    protected void apply(DomainEvent event) {
        if (getAggregateId() == null) {
            setAggregateId(event.aggregateId());
        }
        appliedEventTypes.add(event.eventType());
        lastPayload = event.payload();
    }

    public List<String> getAppliedEventTypes() {
        return appliedEventTypes;
    }

    public String getLastPayload() {
        return lastPayload;
    }

    /** Raises a new event on this aggregate (records it as uncommitted). */
    public void raise(DomainEvent event) {
        applyEvent(event);
    }
}
