package com.adhar.kit.commons.event;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class BaseCloudEventTest {

    static class SimpleEvent extends BaseCloudEvent {
        SimpleEvent(URI source, String type) { super(source, type); }
        SimpleEvent(URI source, String type, String subject) { super(source, type, subject); }
        SimpleEvent(URI source, String type, String subject, String contentType, URI schema) {
            super(source, type, subject, contentType, schema);
        }
        void ext(String name, Object value) { addExtension(name, value); }
    }

    private static final URI SOURCE = URI.create("https://adhar.example.com/orders");

    @Test
    void twoArgConstructorDefaults() {
        SimpleEvent e = new SimpleEvent(SOURCE, "com.adhar.order.created");
        assertNotNull(e.getId());
        assertEquals(SOURCE, e.getSource());
        assertEquals("com.adhar.order.created", e.getType());
        assertNull(e.getSubject());
        assertEquals("1.0", e.getSpecVersion());
        assertEquals("application/json", e.getDataContentType());
        assertNull(e.getDataSchema());
        assertNotNull(e.getTime());
        assertTrue(e.getExtensions().isEmpty());
    }

    @Test
    void threeArgConstructorSetsSubject() {
        SimpleEvent e = new SimpleEvent(SOURCE, "type", "order-1");
        assertEquals("order-1", e.getSubject());
    }

    @Test
    void fullConstructorDefaultsContentTypeWhenNull() {
        SimpleEvent e = new SimpleEvent(SOURCE, "type", "sub", null, URI.create("urn:schema"));
        assertEquals("application/json", e.getDataContentType());
        assertEquals(URI.create("urn:schema"), e.getDataSchema());
    }

    @Test
    void fullConstructorKeepsProvidedContentType() {
        SimpleEvent e = new SimpleEvent(SOURCE, "type", "sub", "application/xml", null);
        assertEquals("application/xml", e.getDataContentType());
    }

    @Test
    void addExtensionStoresValueAndIgnoresNulls() {
        SimpleEvent e = new SimpleEvent(SOURCE, "type");
        e.ext("priority", "high");
        e.ext(null, "x");
        e.ext("y", null);
        assertEquals("high", e.getExtension("priority"));
        assertNull(e.getExtension("y"));
        assertEquals(1, e.getExtensions().size());
    }

    @Test
    void getExtensionsReturnsImmutableCopy() {
        SimpleEvent e = new SimpleEvent(SOURCE, "type");
        e.ext("k", "v");
        assertThrows(UnsupportedOperationException.class, () -> e.getExtensions().put("a", "b"));
    }

    @Test
    void toStringContainsKeyFields() {
        SimpleEvent e = new SimpleEvent(SOURCE, "com.adhar.order.created", "order-1");
        String s = e.toString();
        assertTrue(s.contains("com.adhar.order.created"));
        assertTrue(s.contains("order-1"));
    }
}
