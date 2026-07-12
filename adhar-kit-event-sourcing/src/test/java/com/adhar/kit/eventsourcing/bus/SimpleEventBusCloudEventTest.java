package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SimpleEventBus.publishAsCloudEvent")
class SimpleEventBusCloudEventTest {

    private DomainEvent event(String eventType, String aggregateId) {
        return new DomainEvent("evt-1", aggregateId, "TestAggregate",
                1, eventType, "{}", Instant.now());
    }

    @Test
    @DisplayName("publishAsCloudEvent delivers the underlying domain event to subscribers")
    void publishAsCloudEventDeliversToSubscribers() {
        SimpleEventBus bus = new SimpleEventBus();
        List<DomainEvent> received = new ArrayList<>();
        bus.subscribe("OrderCreated", received::add);

        bus.publishAsCloudEvent(event("OrderCreated", "order-1"));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().aggregateId()).isEqualTo("order-1");
        assertThat(received.getFirst().eventType()).isEqualTo("OrderCreated");
    }

    @Test
    @DisplayName("publishAsCloudEvent with no subscribers does not throw")
    void publishAsCloudEventWithoutSubscribers() {
        SimpleEventBus bus = new SimpleEventBus();

        assertThatCode(() -> bus.publishAsCloudEvent(event("Unhandled", "agg-1")))
                .doesNotThrowAnyException();
    }
}
