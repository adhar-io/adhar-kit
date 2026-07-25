# Adhar Kit Event Sourcing

> Event sourcing and CQRS patterns with event store, snapshotting, projections, event upcasting, aggregate repository, domain event bus, and CloudEvent support.

## Features

- **EventSourcingFacade** - unified access via `adhar.getEventStore()`
- **Event Store** - JPA-backed or in-memory implementations with optimistic concurrency
- **Aggregate Root** - base class for event-sourced aggregates with uncommitted event tracking
- **Domain Events** - immutable event records with aggregate context and versioning
- **Event Bus** - in-process publish/subscribe with type-based filtering
- **CloudEvents** - domain events wrapped in CloudEvent 1.0 envelope
- **Aggregate Repository** - load by replaying events (from a snapshot when available), save by persisting uncommitted events and capturing snapshots at a configurable interval
- **Snapshotting** - `SnapshotStore` (in-memory/JPA) plus `AggregateRoot.createSnapshotState()`/`restoreFromSnapshot()` hooks; aggregates that don't override them transparently keep working via full replay
- **Projections** - `Projection` + `ProjectionManager` subscribe read models to the event bus, track per-projection checkpoints (`ProjectionCheckpointStore`), and support rebuilding a projection from scratch via `EventStore.getAllEvents()`
- **Event Upcasting** - `EventUpcaster` + `UpcasterChain` transparently migrate older stored payload versions to the current schema on every read path
- **Typed Serialization** - `EventTypeRegistry` + `JacksonEventSerializer` provide an opt-in typed payload on top of `DomainEvent`'s plain `String` payload
- **Retrying Repository** - `RetryingAggregateRepository` reloads and reapplies a command on `ConcurrencyException`, up to a configured number of attempts
- **AdharFacade Shortcuts** - `adhar.publishEvent()` and `adhar.onEvent()`

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-event-sourcing</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class OrderCommandHandler {
    private final AdharFacade adhar;

    public OrderCommandHandler(AdharFacade adhar) { this.adhar = adhar; }

    public void createOrder(String orderId, String customerId) {
        var event = new DomainEvent(
            UUID.randomUUID().toString(), orderId, "Order", 1,
            "OrderCreated", "{\"customerId\":\"" + customerId + "\"}",
            Instant.now()
        );
        adhar.getEventStore().saveEvents(orderId, List.of(event), 0);
        adhar.publishEvent(event);  // shortcut
    }

    public void setup() {
        // Subscribe to order events
        adhar.onEvent("OrderCreated", event ->
            log.info("Order created: {}", event.aggregateId()));
    }
}
```

## Configuration

```yaml
adhar:
  event-sourcing:
    enabled: true
    snapshot-interval: 100
    event-store-type: jpa   # or "in-memory" for development
    retry-max-attempts: 3
```

## API Reference

| Method | Description |
|--------|-------------|
| `saveEvents(id, events, version)` | Store events with concurrency check |
| `getEvents(aggregateId)` | Get all events for an aggregate |
| `getEventsAfterVersion(id, ver)` | Get events after a specific version |
| `getAllEvents()` | Get every event across all aggregates, in persistence order (used for projection rebuilds) |
| `publish(event)` | Publish domain event to bus |
| `publishCloudEvent(event)` | Publish as CloudEvent envelope |
| `subscribe(type, handler)` | Subscribe to events by type |
| `loadAggregate(id, factory)` | Replay events onto aggregate |
| `saveAggregate(aggregate)` | Persist uncommitted events |

## Snapshotting

Override two hooks on `AggregateRoot` to opt an aggregate into snapshotting; aggregates that
don't override them keep working via full event replay:

```java
public class OrderAggregate extends AggregateRoot {
    @Override
    public String createSnapshotState() {
        return objectMapper.writeValueAsString(this.state);
    }

    @Override
    public void restoreFromSnapshot(String state, int version) {
        this.state = objectMapper.readValue(state, OrderState.class);
    }
}
```

`AggregateRepository` automatically restores from the latest snapshot (if any) and only
fetches events after the snapshot's version, then captures a new snapshot every
`snapshot-interval` events on save.

## Projections

```java
ProjectionManager manager = new ProjectionManager(eventBus, checkpointStore);
manager.register(orderSummaryProjection);       // subscribes to its interested event types
manager.rebuild("orderSummary", eventStore);      // replay from position zero
```

A failing projection handler is caught and logged so it never breaks event dispatch for
other projections or halts a rebuild.

## Event Upcasting & Typed Serialization

```java
UpcasterChain chain = new UpcasterChain(List.of(new OrderCreatedV1ToV2Upcaster()));
EventStore eventStore = new JpaEventStore(repository, chain); // or InMemoryEventStore(chain)

EventTypeRegistry registry = new EventTypeRegistry().register("OrderCreated", OrderCreated.class);
JacksonEventSerializer serializer = new JacksonEventSerializer(objectMapper, registry);
OrderCreated payload = serializer.deserialize(event, OrderCreated.class);
```

## Retrying on Concurrency Conflicts

```java
RetryingAggregateRepository retrying = new RetryingAggregateRepository(aggregateRepository, 3);
retrying.executeWithRetry(orderId, OrderAggregate.class,
        order -> order.ship());
```
