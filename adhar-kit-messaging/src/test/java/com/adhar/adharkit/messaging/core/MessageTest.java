package com.adhar.adharkit.messaging.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link Message} class.
 */
class MessageTest {

    @Test
    void testCreateMessageWithPayload() {
        // Create a message with just a payload
        String payload = "Test payload";
        Message<String> message = new Message<>(payload);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertNull(message.getDestination());
        assertNull(message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getHeaders().isEmpty());
        assertEquals(URI.create("urn:adhar:messaging"), message.getSource());
        assertEquals("String", message.getType());
        assertEquals("1.0", message.getSpecVersion());
        assertEquals("application/json", message.getDataContentType());
        assertNull(message.getDataSchema());
        assertNull(message.getSubject());
    }

    @Test
    void testCreateMessageWithPayloadAndDestination() {
        // Create a message with payload and destination
        String payload = "Test payload";
        String destination = "test-destination";
        Message<String> message = new Message<>(payload, destination);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertNull(message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getHeaders().isEmpty());
        assertEquals(URI.create("urn:adhar:messaging"), message.getSource());
        assertEquals("String", message.getType());
        assertEquals("1.0", message.getSpecVersion());
        assertEquals("application/json", message.getDataContentType());
        assertNull(message.getDataSchema());
        assertNull(message.getSubject());
    }

    @Test
    void testCreateMessageWithPayloadDestinationAndRoutingKey() {
        // Create a message with payload, destination, and routing key
        String payload = "Test payload";
        String destination = "test-destination";
        String routingKey = "test-routing-key";
        Message<String> message = new Message<>(payload, destination, routingKey);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertEquals(routingKey, message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getHeaders().isEmpty());
        assertEquals(URI.create("urn:adhar:messaging"), message.getSource());
        assertEquals("String", message.getType());
        assertEquals("1.0", message.getSpecVersion());
        assertEquals("application/json", message.getDataContentType());
        assertNull(message.getDataSchema());
        assertEquals(routingKey, message.getSubject());
    }

    @Test
    void testCreateMessageWithPayloadDestinationAndHeaders() {
        // Create a message with payload, destination, and headers
        String payload = "Test payload";
        String destination = "test-destination";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        Message<String> message = new Message<>(payload, destination, headers);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertNull(message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertEquals(2, message.getHeaders().size());
        assertEquals("value1", message.getHeaders().get("header1"));
        assertEquals("value2", message.getHeaders().get("header2"));
        assertEquals(URI.create("urn:adhar:messaging"), message.getSource());
        assertEquals("String", message.getType());
        assertEquals("1.0", message.getSpecVersion());
        assertEquals("application/json", message.getDataContentType());
        assertNull(message.getDataSchema());
        assertNull(message.getSubject());
    }

    @Test
    void testCreateMessageWithPayloadDestinationRoutingKeyAndHeaders() {
        // Create a message with payload, destination, routing key, and headers
        String payload = "Test payload";
        String destination = "test-destination";
        String routingKey = "test-routing-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        Message<String> message = new Message<>(payload, destination, routingKey, headers);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertEquals(routingKey, message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertEquals(2, message.getHeaders().size());
        assertEquals("value1", message.getHeaders().get("header1"));
        assertEquals("value2", message.getHeaders().get("header2"));
        assertEquals(URI.create("urn:adhar:messaging"), message.getSource());
        assertEquals("String", message.getType());
        assertEquals("1.0", message.getSpecVersion());
        assertEquals("application/json", message.getDataContentType());
        assertNull(message.getDataSchema());
        assertEquals(routingKey, message.getSubject());
    }

    @Test
    void testCreateMessageWithAllProperties() {
        // Create a message with all properties
        String payload = "Test payload";
        String destination = "test-destination";
        String routingKey = "test-routing-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        URI source = URI.create("urn:adhar:messaging:test");
        String type = "com.adhar.test.event";
        String specVersion = "1.0";
        String dataContentType = "application/json";
        URI dataSchema = URI.create("urn:adhar:schema:test");
        String subject = "test-subject";

        Message<String> message = new Message<>(payload, destination, routingKey, headers,
                source, type, specVersion, dataContentType, dataSchema, subject);

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertEquals(routingKey, message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertEquals(2, message.getHeaders().size());
        assertEquals("value1", message.getHeaders().get("header1"));
        assertEquals("value2", message.getHeaders().get("header2"));
        assertEquals(source, message.getSource());
        assertEquals(type, message.getType());
        assertEquals(specVersion, message.getSpecVersion());
        assertEquals(dataContentType, message.getDataContentType());
        assertEquals(dataSchema, message.getDataSchema());
        assertEquals(subject, message.getSubject());
    }

    @Test
    void testCreateMessageWithBuilder() {
        // Create a message with the builder
        String payload = "Test payload";
        String destination = "test-destination";
        String routingKey = "test-routing-key";
        URI source = URI.create("urn:adhar:messaging:test");
        String type = "com.adhar.test.event";
        String specVersion = "1.0";
        String dataContentType = "application/json";
        URI dataSchema = URI.create("urn:adhar:schema:test");
        String subject = "test-subject";

        Message<String> message = Message.<String>builder()
                .payload(payload)
                .destination(destination)
                .routingKey(routingKey)
                .header("header1", "value1")
                .header("header2", "value2")
                .source(source)
                .type(type)
                .specVersion(specVersion)
                .dataContentType(dataContentType)
                .dataSchema(dataSchema)
                .subject(subject)
                .build();

        // Verify the message properties
        assertNotNull(message.getId());
        assertEquals(payload, message.getPayload());
        assertEquals(destination, message.getDestination());
        assertEquals(routingKey, message.getRoutingKey());
        assertNotNull(message.getTimestamp());
        assertEquals(2, message.getHeaders().size());
        assertEquals("value1", message.getHeaders().get("header1"));
        assertEquals("value2", message.getHeaders().get("header2"));
        assertEquals(source, message.getSource());
        assertEquals(type, message.getType());
        assertEquals(specVersion, message.getSpecVersion());
        assertEquals(dataContentType, message.getDataContentType());
        assertEquals(dataSchema, message.getDataSchema());
        assertEquals(subject, message.getSubject());
    }

    @Test
    void testCreateMessageWithBuilderAndStringSource() {
        // Create a message with the builder using a string source
        String payload = "Test payload";
        String source = "urn:adhar:messaging:test";

        Message<String> message = Message.<String>builder()
                .payload(payload)
                .source(source)
                .build();

        // Verify the source property
        assertEquals(URI.create(source), message.getSource());
    }

    @Test
    void testCreateMessageWithBuilderAndStringDataSchema() {
        // Create a message with the builder using a string data schema
        String payload = "Test payload";
        String dataSchema = "urn:adhar:schema:test";

        Message<String> message = Message.<String>builder()
                .payload(payload)
                .dataSchema(dataSchema)
                .build();

        // Verify the data schema property
        assertEquals(URI.create(dataSchema), message.getDataSchema());
    }

    @Test
    void testCreateMessageWithBuilderAndHeaders() {
        // Create a message with the builder using headers map
        String payload = "Test payload";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");

        Message<String> message = Message.<String>builder()
                .payload(payload)
                .headers(headers)
                .build();

        // Verify the headers
        assertEquals(2, message.getHeaders().size());
        assertEquals("value1", message.getHeaders().get("header1"));
        assertEquals("value2", message.getHeaders().get("header2"));
    }

    @Test
    void testBuilderWithNullPayload() {
        // Verify that building a message with a null payload throws an exception
        assertThrows(IllegalStateException.class, () -> {
            Message.builder().build();
        });
    }

    @Test
    void testToString() {
        // Create a message
        String payload = "Test payload";
        Message<String> message = new Message<>(payload);

        // Verify that toString() returns a non-null, non-empty string
        String toString = message.toString();
        assertNotNull(toString);
        assertFalse(toString.isEmpty());
        assertTrue(toString.contains(payload));
        assertTrue(toString.contains(message.getId()));
    }
}