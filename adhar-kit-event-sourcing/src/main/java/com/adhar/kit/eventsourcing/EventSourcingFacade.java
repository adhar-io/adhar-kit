package com.adhar.kit.eventsourcing;

import com.adhar.kit.commons.event.AdharCloudEvent;
import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.bus.SimpleEventBus;
import com.adhar.kit.eventsourcing.core.AggregateRoot;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.repository.AggregateRepository;
import com.adhar.kit.eventsourcing.store.EventStore;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unified facade for event sourcing operations.
 *
 * <p>Provides a simplified, single-entry-point API for persisting and retrieving
 * domain events, publishing and subscribing via the event bus, and loading and
 * saving event-sourced aggregates.</p>
 *
 * <p><b>Example - Saving and publishing events:</b></p>
 * <pre>{@code
 * EventSourcingFacade facade = EventSourcingFacade.getInstance();
 *
 * facade.saveEvents("order-123", events, 0);
 * facade.publish(event);
 * }</pre>
 *
 * <p><b>Example - Loading an aggregate:</b></p>
 * <pre>{@code
 * OrderAggregate order = facade.loadAggregate("order-123", OrderAggregate::new);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class EventSourcingFacade {

    /**
     * Singleton instance (thread-safe lazy initialization).
     */
    private static volatile EventSourcingFacade instance;

    /**
     * Event persistence store.
     */
    private final EventStore eventStore;

    /**
     * Event publication and subscription bus.
     */
    private final EventBus eventBus;

    /**
     * Repository for aggregate lifecycle management.
     */
    private final AggregateRepository aggregateRepository;

    /**
     * Indicates if event sourcing is available and configured.
     */
    private volatile boolean enabled = false;

    /**
     * Constructs the facade with optional dependencies.
     *
     * <p>If all three dependencies are provided and non-null, the facade is
     * marked as enabled. Pass {@code null} for any dependency that is not
     * available; the facade will remain disabled.</p>
     *
     * @param eventStore          the event store implementation, or {@code null}
     * @param eventBus            the event bus implementation, or {@code null}
     * @param aggregateRepository the aggregate repository, or {@code null}
     */
    public EventSourcingFacade(EventStore eventStore, EventBus eventBus, AggregateRepository aggregateRepository) {
        log.info("Initializing Event Sourcing Facade v1.0.0");
        this.eventStore = eventStore;
        this.eventBus = eventBus;
        this.aggregateRepository = aggregateRepository;

        if (eventStore != null && eventBus != null && aggregateRepository != null) {
            this.enabled = true;
            log.info("Event Sourcing Facade initialized successfully");
        } else {
            log.warn("Event Sourcing Facade initialized in disabled state - missing dependencies");
        }
    }

    /**
     * Gets the singleton instance.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * <p>The singleton is created in a disabled state with no dependencies.
     * Use the parameterized constructor for a fully configured instance.</p>
     *
     * @return the EventSourcingFacade singleton instance
     */
    public static EventSourcingFacade getInstance() {
        if (instance == null) {
            synchronized (EventSourcingFacade.class) {
                if (instance == null) {
                    instance = new EventSourcingFacade(null, null, null);
                }
            }
        }
        return instance;
    }

    // ==================== Event Store Operations ====================

    /**
     * Persists a batch of events for the given aggregate.
     *
     * <p>Delegates to the configured {@link EventStore}. Optimistic concurrency
     * is enforced via the {@code expectedVersion} parameter.</p>
     *
     * @param aggregateId     the aggregate identifier
     * @param events          events to persist
     * @param expectedVersion the version the aggregate was at before these events
     */
    public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, skipping saveEvents for aggregate: {}", aggregateId);
            return;
        }

        try {
            eventStore.saveEvents(aggregateId, events, expectedVersion);
            log.debug("Saved {} events for aggregate '{}'", events.size(), aggregateId);
        } catch (Exception e) {
            log.error("Failed to save events for aggregate: {}", aggregateId, e);
            throw e;
        }
    }

    /**
     * Retrieves all events for the given aggregate, ordered by version.
     *
     * @param aggregateId the aggregate identifier
     * @return ordered list of domain events, or an empty list if not enabled
     */
    public List<DomainEvent> getEvents(String aggregateId) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, returning empty list for aggregate: {}", aggregateId);
            return List.of();
        }

        try {
            return eventStore.getEvents(aggregateId);
        } catch (Exception e) {
            log.error("Failed to get events for aggregate: {}", aggregateId, e);
            throw e;
        }
    }

    /**
     * Retrieves events for the given aggregate that occurred after the specified version.
     *
     * @param aggregateId the aggregate identifier
     * @param version     only events with a version greater than this value are returned
     * @return ordered list of domain events after the given version, or an empty list if not enabled
     */
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, returning empty list for aggregate: {}", aggregateId);
            return List.of();
        }

        try {
            return eventStore.getEventsAfterVersion(aggregateId, version);
        } catch (Exception e) {
            log.error("Failed to get events after version {} for aggregate: {}", version, aggregateId, e);
            throw e;
        }
    }

    // ==================== Event Bus Operations ====================

    /**
     * Publishes a domain event to all registered handlers via the event bus.
     *
     * @param event the domain event to publish
     */
    public void publish(DomainEvent event) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, skipping publish for event: {}", event.eventType());
            return;
        }

        try {
            eventBus.publish(event);
            log.debug("Published event '{}' for aggregate '{}'", event.eventType(), event.aggregateId());
        } catch (Exception e) {
            log.error("Failed to publish event: {}", event.eventType(), e);
            throw e;
        }
    }

    /**
     * Subscribes a handler for a specific event type via the event bus.
     *
     * @param eventType the event type to subscribe to
     * @param handler   the handler that processes matching events
     */
    public void subscribe(String eventType, Consumer<DomainEvent> handler) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, skipping subscribe for event type: {}", eventType);
            return;
        }

        try {
            eventBus.subscribe(eventType, handler);
            log.debug("Subscribed handler for event type '{}'", eventType);
        } catch (Exception e) {
            log.error("Failed to subscribe to event type: {}", eventType, e);
            throw e;
        }
    }

    /**
     * Wraps a domain event in a CloudEvent envelope and publishes it.
     *
     * <p>This convenience method delegates to
     * {@link SimpleEventBus#publishAsCloudEvent(DomainEvent)} when the underlying
     * event bus is a {@link SimpleEventBus}. Otherwise, the event is published
     * normally via {@link #publish(DomainEvent)}.</p>
     *
     * @param event the domain event to wrap in a CloudEvent and publish
     */
    public void publishCloudEvent(DomainEvent event) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, skipping publishCloudEvent for event: {}", event.eventType());
            return;
        }

        try {
            if (eventBus instanceof SimpleEventBus simpleEventBus) {
                simpleEventBus.publishAsCloudEvent(event);
            } else {
                // Fallback: create the CloudEvent envelope and publish the underlying event
                String cloudEventType = "com.adhar.eventsourcing." + event.eventType();
                AdharCloudEvent<DomainEvent> cloudEvent = AdharCloudEvent.of(
                        "adhar-kit/event-sourcing",
                        cloudEventType,
                        event.aggregateId(),
                        event
                );
                log.debug("Publishing CloudEvent [type={}, subject={}] for domain event '{}'",
                        cloudEvent.type(), cloudEvent.subject(), event.eventType());
                eventBus.publish(event);
            }
            log.debug("Published CloudEvent for event '{}' on aggregate '{}'",
                    event.eventType(), event.aggregateId());
        } catch (Exception e) {
            log.error("Failed to publish CloudEvent for event: {}", event.eventType(), e);
            throw e;
        }
    }

    // ==================== Aggregate Operations ====================

    /**
     * Loads an aggregate by replaying all stored events.
     *
     * <p>A new aggregate instance is created using the provided factory and
     * its events are replayed from the event store to reconstitute state.</p>
     *
     * @param aggregateId the aggregate identifier
     * @param factory     supplier that creates a new empty aggregate instance
     * @param <T>         aggregate type
     * @return the reconstituted aggregate
     * @throws IllegalStateException if event sourcing is not enabled or no events exist
     */
    public <T extends AggregateRoot> T loadAggregate(String aggregateId, Supplier<T> factory) {
        if (!enabled) {
            throw new IllegalStateException("Event sourcing not enabled, cannot load aggregate: " + aggregateId);
        }

        try {
            List<DomainEvent> events = eventStore.getEvents(aggregateId);
            if (events.isEmpty()) {
                throw new IllegalStateException("No events found for aggregate: " + aggregateId);
            }

            T aggregate = factory.get();
            for (DomainEvent event : events) {
                aggregate.applyEvent(event);
            }
            aggregate.markEventsAsCommitted();

            log.debug("Loaded aggregate '{}' with {} events at version {}",
                    aggregateId, events.size(), aggregate.getVersion());
            return aggregate;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load aggregate: {}", aggregateId, e);
            throw new IllegalStateException("Failed to load aggregate: " + aggregateId, e);
        }
    }

    /**
     * Saves all uncommitted events from the aggregate and publishes them via the event bus.
     *
     * @param aggregate the aggregate whose uncommitted events should be saved
     * @param <T>       aggregate type
     */
    public <T extends AggregateRoot> void saveAggregate(T aggregate) {
        if (!enabled) {
            log.warn("Event sourcing not enabled, skipping save for aggregate: {}", aggregate.getAggregateId());
            return;
        }

        try {
            List<DomainEvent> uncommitted = aggregate.getUncommittedEvents();
            if (uncommitted.isEmpty()) {
                log.debug("No uncommitted events for aggregate '{}'", aggregate.getAggregateId());
                return;
            }

            int expectedVersion = aggregate.getVersion() - uncommitted.size();
            eventStore.saveEvents(aggregate.getAggregateId(), uncommitted, expectedVersion);

            for (DomainEvent event : uncommitted) {
                eventBus.publish(event);
            }

            aggregate.markEventsAsCommitted();
            log.debug("Saved {} uncommitted events for aggregate '{}'",
                    uncommitted.size(), aggregate.getAggregateId());
        } catch (Exception e) {
            log.error("Failed to save aggregate: {}", aggregate.getAggregateId(), e);
            throw e;
        }
    }

    // ==================== Status and Health ====================

    /**
     * Checks if event sourcing is enabled and fully configured.
     *
     * @return true if the facade has all required dependencies and is operational
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns health information about the event sourcing subsystem.
     *
     * @return a map containing the event store type and event count diagnostics
     */
    public Map<String, Object> health() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("enabled", enabled);

        if (enabled) {
            healthInfo.put("eventStoreType", eventStore.getClass().getSimpleName());
            healthInfo.put("eventBusType", eventBus.getClass().getSimpleName());
        } else {
            healthInfo.put("eventStoreType", "none");
            healthInfo.put("eventBusType", "none");
        }

        return healthInfo;
    }
}
