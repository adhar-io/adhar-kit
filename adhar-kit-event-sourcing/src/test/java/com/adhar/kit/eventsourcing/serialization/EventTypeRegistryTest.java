package com.adhar.kit.eventsourcing.serialization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventTypeRegistry")
class EventTypeRegistryTest {

    record OrderCreated(String customerId) {
    }

    record OrderShipped(String trackingNumber) {
    }

    @Test
    @DisplayName("resolve returns empty when nothing is registered")
    void resolveReturnsEmptyWhenUnregistered() {
        EventTypeRegistry registry = new EventTypeRegistry();

        assertThat(registry.resolve("OrderCreated")).isEmpty();
        assertThat(registry.isRegistered("OrderCreated")).isFalse();
    }

    @Test
    @DisplayName("register then resolve returns the registered class")
    void registerThenResolve() {
        EventTypeRegistry registry = new EventTypeRegistry();

        registry.register("OrderCreated", OrderCreated.class);

        assertThat(registry.resolve("OrderCreated")).contains(OrderCreated.class);
        assertThat(registry.isRegistered("OrderCreated")).isTrue();
    }

    @Test
    @DisplayName("register is fluent and supports multiple event types")
    void registerIsFluentAndSupportsMultipleTypes() {
        EventTypeRegistry registry = new EventTypeRegistry()
                .register("OrderCreated", OrderCreated.class)
                .register("OrderShipped", OrderShipped.class);

        assertThat(registry.resolve("OrderCreated")).contains(OrderCreated.class);
        assertThat(registry.resolve("OrderShipped")).contains(OrderShipped.class);
    }

    @Test
    @DisplayName("unregister removes a previously registered event type")
    void unregisterRemovesRegistration() {
        EventTypeRegistry registry = new EventTypeRegistry();
        registry.register("OrderCreated", OrderCreated.class);

        registry.unregister("OrderCreated");

        assertThat(registry.resolve("OrderCreated")).isEmpty();
        assertThat(registry.isRegistered("OrderCreated")).isFalse();
    }
}
