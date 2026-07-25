package com.adhar.kit.eventsourcing.projection;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.store.EventStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers {@link Projection}s for live updates via the {@link EventBus} and tracks their
 * progress via a {@link ProjectionCheckpointStore}.
 *
 * <p>Each registered projection is subscribed to every event type it declares interest in.
 * Exceptions thrown while handling an event are caught and logged so that a single failing
 * projection (or a single bad event) cannot break event dispatch for other subscribers.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ProjectionManager {

    private final EventBus eventBus;
    private final ProjectionCheckpointStore checkpointStore;
    private final Map<String, Projection> projections = new ConcurrentHashMap<>();

    public ProjectionManager(EventBus eventBus, ProjectionCheckpointStore checkpointStore) {
        this.eventBus = eventBus;
        this.checkpointStore = checkpointStore;
    }

    /**
     * Registers a projection, subscribing it to the event bus for each of its interested
     * event types.
     *
     * @param projection the projection to register
     */
    public void register(Projection projection) {
        projections.put(projection.getName(), projection);
        for (String eventType : projection.interestedEventTypes()) {
            eventBus.subscribe(eventType, event -> dispatch(projection, event));
        }
        log.debug("Registered projection '{}' for event types {}", projection.getName(),
                projection.interestedEventTypes());
    }

    /**
     * Returns the projection registered under the given name, if any.
     *
     * @param name the projection name
     * @return the registered projection, or {@code null} if none is registered
     */
    public Projection getProjection(String name) {
        return projections.get(name);
    }

    private void dispatch(Projection projection, DomainEvent event) {
        try {
            projection.handle(event);
            long next = checkpointStore.getCheckpoint(projection.getName()) + 1;
            checkpointStore.saveCheckpoint(projection.getName(), next);
        } catch (Exception ex) {
            log.error("Projection '{}' failed to handle event '{}' for aggregate '{}': {}",
                    projection.getName(), event.eventType(), event.aggregateId(), ex.getMessage(), ex);
        }
    }

    /**
     * Rebuilds a projection from scratch by replaying every event in the given
     * {@link EventStore}, starting from position zero. The projection's checkpoint is
     * reset before replay begins and advanced as events are processed, regardless of
     * whether an individual event was of interest or its handling failed.
     *
     * @param projectionName the name of a previously {@link #register(Projection) registered} projection
     * @param eventStore     the event store to replay events from
     * @throws IllegalArgumentException if no projection is registered under {@code projectionName}
     */
    public void rebuild(String projectionName, EventStore eventStore) {
        Projection projection = projections.get(projectionName);
        if (projection == null) {
            throw new IllegalArgumentException("No projection registered with name: " + projectionName);
        }

        checkpointStore.saveCheckpoint(projectionName, 0L);
        List<DomainEvent> allEvents = eventStore.getAllEvents();

        long position = 0;
        for (DomainEvent event : allEvents) {
            if (projection.interestedEventTypes().contains(event.eventType())) {
                try {
                    projection.handle(event);
                } catch (Exception ex) {
                    log.error("Projection '{}' failed during rebuild handling event '{}' for aggregate '{}': {}",
                            projectionName, event.eventType(), event.aggregateId(), ex.getMessage(), ex);
                }
            }
            position++;
            checkpointStore.saveCheckpoint(projectionName, position);
        }

        log.debug("Rebuilt projection '{}' from {} events", projectionName, allEvents.size());
    }
}
