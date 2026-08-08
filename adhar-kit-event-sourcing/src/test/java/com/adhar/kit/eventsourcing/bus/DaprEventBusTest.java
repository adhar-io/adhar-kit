package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DaprEventBus} with a mocked {@link DaprFacade}.
 */
class DaprEventBusTest {

    private DaprFacade daprFacade;
    private DomainEventKafkaSerde serde;
    private DaprEventBus bus;

    @BeforeEach
    void setUp() {
        daprFacade = mock(DaprFacade.class);
        serde = new DomainEventKafkaSerde(new ObjectMapper(), new EventTypeRegistry());
        bus = new DaprEventBus(daprFacade, serde, "pubsub", "es-events");
    }

    private DomainEvent event(String aggregateId, int version) {
        return new DomainEvent("e-" + version, aggregateId, "Order", version,
                "order.created", "{\"total\":42}", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void publishSerializesAndKeysByAggregateId() {
        DomainEvent event = event("agg-1", 1);

        bus.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(daprFacade).publishEvent(eq("pubsub"), eq("es-events"), payload.capture(),
                eq(Map.of("partitionKey", "agg-1")));
        assertThat(serde.deserialize(payload.getValue())).isEqualTo(event);
    }

    @Test
    void dispatchInvokesRegisteredHandlers() {
        List<DomainEvent> received = new CopyOnWriteArrayList<>();
        bus.subscribe("order.created", received::add);
        DomainEvent event = event("agg-1", 1);

        bus.dispatch(serde.serialize(event));

        assertThat(received).containsExactly(event);
    }

    @Test
    void dispatchIgnoresEventTypesWithoutHandlers() {
        bus.dispatch(serde.serialize(event("agg-1", 1)));
        // no handler registered - must not throw
        verify(daprFacade, org.mockito.Mockito.never())
                .publishEvent(anyString(), anyString(), anyString(), anyMap());
    }
}
