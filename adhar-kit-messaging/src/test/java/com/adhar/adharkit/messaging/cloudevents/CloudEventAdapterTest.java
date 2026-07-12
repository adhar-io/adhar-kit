package com.adhar.adharkit.messaging.cloudevents;

import com.adhar.kit.messaging.cloudevents.CloudEventAdapter;
import com.adhar.kit.messaging.core.Message;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link CloudEventAdapter} class.
 */
class CloudEventAdapterTest {

    @Test
    void testToCloudEvent() {
        // Create a message
        String payload = "Test payload";
        String id = UUID.randomUUID().toString();
        URI source = URI.create("urn:adhar:messaging:test");
        String type = "com.adhar.test.event";
        String subject = "test-subject";
        Map<String, Object> headers = new HashMap<>();
        headers.put("custom-header", "custom-value");

        Message<String> message = Message.<String>builder()
                .payload(payload)
                .destination("test-destination")
                .routingKey("test-routing-key")
                .source(source)
                .type(type)
                .specVersion("1.0")
                .dataContentType("application/json")
                .dataSchema(URI.create("urn:adhar:schema:test"))
                .subject(subject)
                .header("custom-header", "custom-value")
                .build();

        // Create adapter and convert to CloudEvent
        CloudEventAdapter adapter = new CloudEventAdapter();
        CloudEvent cloudEvent = adapter.toCloudEvent(message);

        // Verify CloudEvent properties
        assertEquals(message.getId(), cloudEvent.getId());
        assertEquals(source, cloudEvent.getSource());
        assertEquals(type, cloudEvent.getType());
        assertEquals("1.0", cloudEvent.getSpecVersion().toString());
        assertEquals("application/json", cloudEvent.getDataContentType());
        assertEquals(URI.create("urn:adhar:schema:test"), cloudEvent.getDataSchema());
        assertEquals(subject, cloudEvent.getSubject());
        assertNotNull(cloudEvent.getData());

        // Verify extension (header keys are normalized to CloudEvents-compliant
        // lowercase-alphanumeric extension names)
        assertEquals("custom-value", cloudEvent.getExtension("customheader"));
    }

    @Test
    void testFromCloudEvent() {
        // Create a CloudEvent
        String payload = "Test payload";
        String id = UUID.randomUUID().toString();
        URI source = URI.create("urn:adhar:messaging:test");
        String type = "com.adhar.test.event";
        String subject = "test-subject";
        OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC);

        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(id)
                .withSource(source)
                .withType(type)
                .withSubject(subject)
                .withTime(time)
                .withDataContentType("application/json")
                .withDataSchema(URI.create("urn:adhar:schema:test"))
                .withData(payload.getBytes())
                .withExtension("customextension", "custom-value")
                .build();

        // Create adapter and convert to Message
        CloudEventAdapter adapter = new CloudEventAdapter();
        Message<byte[]> message = adapter.fromCloudEvent(cloudEvent);

        // Verify Message properties
        assertNotNull(message.getId()); // ID will be different
        assertArrayEquals(payload.getBytes(), message.getPayload());
        assertEquals(subject, message.getDestination());
        assertEquals(subject, message.getRoutingKey());

        // Verify headers
        Map<String, Object> headers = message.getHeaders();
        assertEquals(id, headers.get("ce-id"));
        assertEquals(source.toString(), headers.get("ce-source"));
        assertEquals(type, headers.get("ce-type"));
        assertEquals("1.0", headers.get("ce-specversion"));
        assertEquals("application/json", headers.get("ce-datacontenttype"));
        assertEquals(URI.create("urn:adhar:schema:test").toString(), headers.get("ce-dataschema"));
        assertEquals(subject, headers.get("ce-subject"));
        assertNotNull(headers.get("ce-time"));
        assertEquals("custom-value", headers.get("customextension"));
    }

    @Test
    void testRoundTrip() {
        // Create a message
        String payload = "Test payload";
        URI source = URI.create("urn:adhar:messaging:test");
        String type = "com.adhar.test.event";
        String subject = "test-subject";

        Message<String> originalMessage = Message.<String>builder()
                .payload(payload)
                .destination("test-destination")
                .routingKey("test-routing-key")
                .source(source)
                .type(type)
                .specVersion("1.0")
                .dataContentType("application/json")
                .dataSchema(URI.create("urn:adhar:schema:test"))
                .subject(subject)
                .header("custom-header", "custom-value")
                .build();

        // Create adapter and convert to CloudEvent and back to Message
        CloudEventAdapter adapter = new CloudEventAdapter();
        CloudEvent cloudEvent = adapter.toCloudEvent(originalMessage);
        Message<?> convertedMessage = adapter.fromCloudEvent(cloudEvent);

        // Verify the round-trip conversion
        assertEquals(originalMessage.getId(), cloudEvent.getId());
        assertEquals(originalMessage.getSource(), cloudEvent.getSource());
        assertEquals(originalMessage.getType(), cloudEvent.getType());
        assertEquals(originalMessage.getSpecVersion(), cloudEvent.getSpecVersion().toString());
        assertEquals(originalMessage.getDataContentType(), cloudEvent.getDataContentType());
        assertEquals(originalMessage.getDataSchema(), cloudEvent.getDataSchema());
        assertEquals(originalMessage.getSubject(), cloudEvent.getSubject());

        // Verify the headers are preserved (normalized to a CloudEvents-compliant name)
        assertEquals("custom-value", convertedMessage.getHeaders().get("customheader"));
    }
}
