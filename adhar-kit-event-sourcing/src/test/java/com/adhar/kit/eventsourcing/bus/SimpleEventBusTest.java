package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SimpleEventBus")
class SimpleEventBusTest {

    private SimpleEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
    }

    private DomainEvent createEvent(String eventType, String aggregateId) {
        return new DomainEvent(
                "evt-1", aggregateId, "TestAggregate",
                1, eventType, "{}", Instant.now()
        );
    }

    @Test
    @DisplayName("publish delivers event to subscribers")
    void publishDeliversToSubscribers() {
        List<DomainEvent> received = new ArrayList<>();
        eventBus.subscribe("OrderCreated", received::add);

        DomainEvent event = createEvent("OrderCreated", "order-1");
        eventBus.publish(event);

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().aggregateId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("subscribe registers handler")
    void subscribeRegistersHandler() {
        AtomicInteger counter = new AtomicInteger(0);
        eventBus.subscribe("TestEvent", _ -> counter.incrementAndGet());

        eventBus.publish(createEvent("TestEvent", "agg-1"));
        eventBus.publish(createEvent("TestEvent", "agg-2"));

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiple subscribers receive same event")
    void multipleSubscribersReceiveSameEvent() {
        List<DomainEvent> subscriber1 = new ArrayList<>();
        List<DomainEvent> subscriber2 = new ArrayList<>();

        eventBus.subscribe("OrderCreated", subscriber1::add);
        eventBus.subscribe("OrderCreated", subscriber2::add);

        eventBus.publish(createEvent("OrderCreated", "order-1"));

        assertThat(subscriber1).hasSize(1);
        assertThat(subscriber2).hasSize(1);
    }

    @Test
    @DisplayName("subscribe to specific event type filters correctly")
    void subscribeToSpecificEventTypeFiltersCorrectly() {
        List<DomainEvent> orderEvents = new ArrayList<>();
        List<DomainEvent> paymentEvents = new ArrayList<>();

        eventBus.subscribe("OrderCreated", orderEvents::add);
        eventBus.subscribe("PaymentReceived", paymentEvents::add);

        eventBus.publish(createEvent("OrderCreated", "order-1"));
        eventBus.publish(createEvent("PaymentReceived", "payment-1"));
        eventBus.publish(createEvent("OrderCreated", "order-2"));

        assertThat(orderEvents).hasSize(2);
        assertThat(paymentEvents).hasSize(1);
        assertThat(paymentEvents.getFirst().aggregateId()).isEqualTo("payment-1");
    }

    @Test
    @DisplayName("publish with no subscribers does not throw")
    void publishWithNoSubscribersDoesNotThrow() {
        DomainEvent event = createEvent("UnhandledEvent", "agg-1");

        assertThatCode(() -> eventBus.publish(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("handler exception does not prevent other handlers from executing")
    void handlerExceptionDoesNotPreventOtherHandlers() {
        List<DomainEvent> received = new ArrayList<>();

        eventBus.subscribe("TestEvent", _ -> { throw new RuntimeException("handler error"); });
        eventBus.subscribe("TestEvent", received::add);

        eventBus.publish(createEvent("TestEvent", "agg-1"));

        assertThat(received).hasSize(1);
    }
}
