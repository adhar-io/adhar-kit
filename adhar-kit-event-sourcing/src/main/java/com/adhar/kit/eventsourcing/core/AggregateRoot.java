package com.adhar.kit.eventsourcing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for event-sourced aggregates.
 *
 * <p>Maintains uncommitted domain events and tracks the current version.
 * Subclasses implement {@link #apply(DomainEvent)} to mutate internal state
 * when events are replayed or newly applied.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public abstract class AggregateRoot {

    private String aggregateId;
    private int version = 0;
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    /**
     * Returns the unique identifier of this aggregate.
     *
     * @return the aggregate identifier, or {@code null} if not yet set
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Sets the aggregate identifier.
     *
     * @param aggregateId unique identifier
     */
    protected void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * Returns the current version (number of events applied).
     *
     * @return the current aggregate version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets the current version.
     *
     * @param version the version number
     */
    protected void setVersion(int version) {
        this.version = version;
    }

    /**
     * Applies a new domain event: records it as uncommitted and delegates
     * to the subclass {@link #apply(DomainEvent)} method to update state.
     *
     * @param event the domain event to apply
     */
    public void applyEvent(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
        version = event.version();
    }

    /**
     * Returns an unmodifiable view of events that have not yet been persisted.
     *
     * @return unmodifiable list of uncommitted domain events
     */
    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    /**
     * Clears the list of uncommitted events after they have been persisted.
     */
    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Applies the given event to update the aggregate's internal state.
     *
     * <p>Subclasses must implement this to handle each event type and mutate
     * their fields accordingly. This method is invoked both for new events
     * and when replaying historical events from the event store.</p>
     *
     * @param event the domain event to apply
     */
    protected abstract void apply(DomainEvent event);
}
