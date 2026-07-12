package com.adhar.kit.commons.event;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CloudEventTest {

    @Test
    void builderDefaultsAreApplied() {
        CloudEvent<String> e = CloudEvent.<String>builder()
                .type("com.adhar.test")
                .source(URI.create("urn:src"))
                .data("payload")
                .build();
        assertEquals("1.0", e.getSpecVersion());
        assertNotNull(e.getId());
        assertNotNull(e.getTime());
        assertEquals("application/json", e.getDataContentType());
        assertNotNull(e.getExtensions());
        assertEquals("payload", e.getData());
    }

    @Test
    void builderAcceptsExplicitValues() {
        Instant t = Instant.parse("2020-01-01T00:00:00Z");
        CloudEvent<Integer> e = CloudEvent.<Integer>builder()
                .id("fixed-id")
                .specVersion("1.0")
                .time(t)
                .subject("sub")
                .dataSchema(URI.create("urn:schema"))
                .data(7)
                .build();
        assertEquals("fixed-id", e.getId());
        assertEquals(t, e.getTime());
        assertEquals("sub", e.getSubject());
        assertEquals(URI.create("urn:schema"), e.getDataSchema());
        assertEquals(7, e.getData());
    }

    @Test
    void extensionAttributeStoresAndRetrieves() {
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        assertSame(e, e.extensionAttribute("k", "v"));
        assertEquals("v", e.getExtensionAttribute("k"));
        assertNull(e.getExtensionAttribute("absent"));
    }

    @Test
    void extensionAttributeInitializesNullMap() {
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        e.setExtensions(null);
        e.extensionAttribute("k", "v");
        assertEquals("v", e.getExtensionAttribute("k"));
    }

    @Test
    void getExtensionAttributeReturnsNullWhenMapNull() {
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        e.setExtensions(null);
        assertNull(e.getExtensionAttribute("k"));
    }
}
