package com.adhar.kit.commons.event;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {

    static class OrderPlacedEvent extends DomainEvent {
        private final String orderId;

        OrderPlacedEvent(String orderId) {
            super();
            this.orderId = orderId;
        }

        OrderPlacedEvent(String orderId, String correlationId) {
            super(correlationId);
            this.orderId = orderId;
        }

        @Override
        public String getEventType() {
            return "com.example.order.placed";
        }

        @Override
        public String getAggregateId() {
            return orderId;
        }
    }

    @Test
    void defaultConstructorPopulatesMetadataFields() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-1");
        assertNotNull(e.getEventId());
        assertNotNull(e.getOccurredOn());
        assertEquals("1.0", e.getVersion());
        assertNotNull(e.getMetadata());
    }

    @Test
    void correlationConstructorSetsCorrelationId() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-1", "corr-1");
        assertEquals("corr-1", e.getCorrelationId());
    }

    @Test
    void aggregateTypeDerivedFromClassName() {
        assertEquals("OrderPlaced", new OrderPlacedEvent("o-1").getAggregateType());
    }

    @Test
    void addAndGetMetadata() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-1");
        assertSame(e, e.addMetadata("k", "v"));
        assertEquals("v", e.getMetadata("k"));
        assertNull(e.getMetadata("absent"));
    }

    @Test
    void addMetadataInitializesMapWhenNull() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-1");
        e.setMetadata(null);
        e.addMetadata("k", "v");
        assertEquals("v", e.getMetadata("k"));
    }

    @Test
    void getMetadataReturnsNullWhenMapNull() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-1");
        e.setMetadata(null);
        assertNull(e.getMetadata("anything"));
    }

    @Test
    void toCloudEventMapsAttributesAndExtensions() {
        OrderPlacedEvent e = new OrderPlacedEvent("o-99", "corr-7");
        e.setCausationId("cause-1");
        e.setTriggeredBy("user-1");

        URI source = URI.create("https://example.com/orders");
        CloudEvent<DomainEvent> ce = e.toCloudEvent(source);

        assertEquals(e.getEventId(), ce.getId());
        assertEquals(source, ce.getSource());
        assertEquals("com.example.order.placed", ce.getType());
        assertEquals("OrderPlaced/o-99", ce.getSubject());
        assertSame(e, ce.getData());
        assertEquals("o-99", ce.getExtensionAttribute("aggregateId"));
        assertEquals("OrderPlaced", ce.getExtensionAttribute("aggregateType"));
        assertEquals("corr-7", ce.getExtensionAttribute("correlationId"));
        assertEquals("cause-1", ce.getExtensionAttribute("causationId"));
        assertEquals("user-1", ce.getExtensionAttribute("triggeredBy"));
    }
}
