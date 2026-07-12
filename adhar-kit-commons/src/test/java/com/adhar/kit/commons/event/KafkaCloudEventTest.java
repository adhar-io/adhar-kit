package com.adhar.kit.commons.event;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class KafkaCloudEventTest {

    private CloudEvent<String> baseEvent() {
        return CloudEvent.<String>builder()
                .type("com.adhar.order.created")
                .source(URI.create("urn:src"))
                .data("data")
                .build();
    }

    @Test
    void constructorWiresFieldsAndCloudEventExtensions() {
        CloudEvent<String> ce = baseEvent();
        KafkaCloudEvent<String> kce = new KafkaCloudEvent<>(ce, "order-events", "cust-1");

        assertSame(ce, kce.getEvent());
        assertEquals("order-events", kce.getTopic());
        assertEquals("cust-1", kce.getPartitionKey());
        assertNotNull(kce.getHeaders());
        assertTrue(kce.getHeaders().isEmpty());
        assertEquals("cust-1", ce.getExtensionAttribute("partitionkey"));
        assertEquals("order-events", ce.getExtensionAttribute("kafkatopic"));
    }

    @Test
    void addHeaderAndGetHeader() {
        KafkaCloudEvent<String> kce = new KafkaCloudEvent<>(baseEvent(), "t", "k");
        assertSame(kce, kce.addHeader("tenant-id", "t1"));
        assertEquals("t1", kce.getHeader("tenant-id"));
        assertNull(kce.getHeader("missing"));
    }

    @Test
    void setPublishingMetadataUpdatesFieldsAndExtensions() {
        CloudEvent<String> ce = baseEvent();
        KafkaCloudEvent<String> kce = new KafkaCloudEvent<>(ce, "t", "k");
        kce.setPublishingMetadata(42L, 3, 999L);

        assertEquals(42L, kce.getOffset());
        assertEquals(3, kce.getPartition());
        assertEquals(999L, kce.getKafkaTimestamp());
        assertEquals(42L, ce.getExtensionAttribute("kafkaoffset"));
        assertEquals(3, ce.getExtensionAttribute("kafkapartition"));
        assertEquals(999L, ce.getExtensionAttribute("kafkatimestamp"));
    }
}
