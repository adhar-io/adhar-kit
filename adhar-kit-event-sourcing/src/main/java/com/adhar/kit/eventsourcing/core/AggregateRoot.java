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

    /**
     * Creates a serialized representation of this aggregate's internal state so it can
     * be persisted as a {@link com.adhar.kit.eventsourcing.snapshot.Snapshot}.
     *
     * <p>The default implementation is unsupported. Aggregates that want to participate
     * in snapshotting must override this method (typically by serializing their state to
     * JSON). When unsupported, {@code AggregateRepository} transparently falls back to
     * full event replay, so existing aggregates keep working unchanged.</p>
     *
     * @return a serialized (e.g. JSON) representation of this aggregate's internal state
     * @throws UnsupportedOperationException if this aggregate does not support snapshotting
     */
    public String createSnapshotState() {
        throw new UnsupportedOperationException(
                "Snapshotting is not supported by " + getClass().getName()
                        + "; override createSnapshotState() to enable it");
    }

    /**
     * Restores this aggregate's internal state from a previously captured snapshot.
     *
     * <p>Implementations should only restore internal fields here; the aggregate
     * identifier and version are managed separately by {@link #applySnapshot(String, String, int)}.
     * The default implementation is unsupported, causing callers to fall back to full replay.</p>
     *
     * @param state   the serialized state previously produced by {@link #createSnapshotState()}
     * @param version the aggregate version the snapshot was captured at
     * @throws UnsupportedOperationException if this aggregate does not support snapshotting
     */
    public void restoreFromSnapshot(String state, int version) {
        throw new UnsupportedOperationException(
                "Snapshotting is not supported by " + getClass().getName()
                        + "; override restoreFromSnapshot() to enable it");
    }

    /**
     * Applies a snapshot to this aggregate: restores internal state via
     * {@link #restoreFromSnapshot(String, int)}, then sets the aggregate identifier and
     * version to match the snapshot. Callers (e.g. {@code AggregateRepository}) should
     * catch {@link UnsupportedOperationException} and fall back to full event replay.
     *
     * @param aggregateId the aggregate identifier
     * @param state       the serialized snapshot state
     * @param version     the version the snapshot was captured at
     * @throws UnsupportedOperationException if this aggregate does not support snapshotting
     */
    public final void applySnapshot(String aggregateId, String state, int version) {
        restoreFromSnapshot(state, version);
        setAggregateId(aggregateId);
        setVersion(version);
    }
}
