# Adhar Kit Event Sourcing

> Event sourcing and CQRS patterns with event store, aggregate repository, domain event bus, and CloudEvent support.

## Features

- **EventSourcingFacade** - unified access via `adhar.getEventStore()`
- **Event Store** - JPA-backed or in-memory implementations with optimistic concurrency
- **Aggregate Root** - base class for event-sourced aggregates with uncommitted event tracking
- **Domain Events** - immutable event records with aggregate context and versioning
- **Event Bus** - in-process publish/subscribe with type-based filtering
- **CloudEvents** - domain events wrapped in CloudEvent 1.0 envelope
- **Aggregate Repository** - load by replaying events, save by persisting uncommitted
- **AdharFacade Shortcuts** - `adhar.publishEvent()` and `adhar.onEvent()`

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-event-sourcing</artifactId>
    <version>0.0.1-SNAPSHOT</version>
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
    event-store-type: jpa   # or "memory" for development
```

## API Reference

| Method | Description |
|--------|-------------|
| `saveEvents(id, events, version)` | Store events with concurrency check |
| `getEvents(aggregateId)` | Get all events for an aggregate |
| `getEventsAfterVersion(id, ver)` | Get events after a specific version |
| `publish(event)` | Publish domain event to bus |
| `publishCloudEvent(event)` | Publish as CloudEvent envelope |
| `subscribe(type, handler)` | Subscribe to events by type |
| `loadAggregate(id, factory)` | Replay events onto aggregate |
| `saveAggregate(aggregate)` | Persist uncommitted events |
