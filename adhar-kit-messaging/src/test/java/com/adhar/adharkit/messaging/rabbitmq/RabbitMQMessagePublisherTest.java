package com.adhar.adharkit.messaging.rabbitmq;

import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.rabbitmq.RabbitMQMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link RabbitMQMessagePublisher} class.
 */
// The shared @BeforeEach stubs collaborators used by most (but not all) tests,
// so use lenient strictness rather than failing individual tests that exercise
// only a subset of those collaborators.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitMQMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AdharMessagingProperties properties;

    @Mock
    private AdharMessagingProperties.RabbitMQProperties rabbitMQProperties;

    @Mock
    private AdharMessagingProperties.RabbitMQProperties.PublisherProperties publisherProperties;

    @Mock
    private AdharMessagingProperties.CommonProperties commonProperties;

    private RabbitMQMessagePublisher publisher;

    private final String defaultExchange = "default-exchange";
    private final String defaultRoutingKey = "default-routing-key";
    private final String testPayload = "Test payload";

    @BeforeEach
    void setUp() {
        when(properties.getRabbitmq()).thenReturn(rabbitMQProperties);
        when(properties.getCommon()).thenReturn(commonProperties);
        when(rabbitMQProperties.getDefaultExchange()).thenReturn(defaultExchange);
        when(rabbitMQProperties.getDefaultRoutingKey()).thenReturn(defaultRoutingKey);
        when(rabbitMQProperties.getPublisher()).thenReturn(publisherProperties);
        when(commonProperties.isTraceEnabled()).thenReturn(true);

        // Mock the message converter
        when(rabbitTemplate.getMessageConverter()).thenReturn(mock(org.springframework.amqp.support.converter.MessageConverter.class));
        when(rabbitTemplate.getMessageConverter().toMessage(any(), any())).thenReturn(mock(Message.class));

        publisher = new RabbitMQMessagePublisher(rabbitTemplate, properties);
    }

    @Test
    void testPublishWithPayload() {
        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties).getDefaultExchange();
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishWithDestination() {
        // Call the method
        String destination = "test-exchange";
        boolean result = publisher.publish(destination, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(rabbitTemplate).send(eq(destination), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishWithDestinationAndRoutingKey() {
        // Call the method
        String destination = "test-exchange";
        String routingKey = "test-routing-key";
        boolean result = publisher.publish(destination, routingKey, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(rabbitTemplate).send(eq(destination), eq(routingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties, never()).getDefaultRoutingKey(); // Should not use default routing key
    }

    @Test
    void testPublishWithDestinationAndHeaders() {
        // Call the method
        String destination = "test-exchange";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        boolean result = publisher.publish(destination, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(rabbitTemplate).send(eq(destination), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishWithDestinationRoutingKeyAndHeaders() {
        // Call the method
        String destination = "test-exchange";
        String routingKey = "test-routing-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        boolean result = publisher.publish(destination, routingKey, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(rabbitTemplate).send(eq(destination), eq(routingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties, never()).getDefaultRoutingKey(); // Should not use default routing key
    }

    @Test
    void testPublishWithException() {
        // Mock the RabbitTemplate to throw an exception
        doThrow(new RuntimeException("Test exception")).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertFalse(result);
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void testPublishAsyncWithPayload() {
        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties).getDefaultExchange();
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishAsyncWithDestination() {
        // Call the method
        String destination = "test-exchange";
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(destination), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishAsyncWithDestinationAndRoutingKey() {
        // Call the method
        String destination = "test-exchange";
        String routingKey = "test-routing-key";
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, routingKey, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(destination), eq(routingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties, never()).getDefaultRoutingKey(); // Should not use default routing key
    }

    @Test
    void testPublishAsyncWithDestinationAndHeaders() {
        // Call the method
        String destination = "test-exchange";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(destination), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties).getDefaultRoutingKey();
    }

    @Test
    void testPublishAsyncWithDestinationRoutingKeyAndHeaders() {
        // Call the method
        String destination = "test-exchange";
        String routingKey = "test-routing-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, routingKey, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(destination), eq(routingKey), any(Message.class), any(CorrelationData.class));
        verify(rabbitMQProperties, never()).getDefaultExchange(); // Should not use default exchange
        verify(rabbitMQProperties, never()).getDefaultRoutingKey(); // Should not use default routing key
    }

    @Test
    void testPublishAsyncWithException() {
        // Mock the RabbitTemplate to throw an exception
        doThrow(new RuntimeException("Test exception")).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the result and interactions
        assertFalse(result.join()); // Wait for the future to complete and get the result
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void testPublishAsyncWithPublisherConfirms() throws Exception {
        // Enable publisher confirms
        when(publisherProperties.isConfirms()).thenReturn(true);

        // Capture both the confirm callback and the CorrelationData the publisher
        // generates, so the simulated confirm carries the matching correlation id.
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCallbackCaptor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the confirm callback was set and capture the correlation data sent
        verify(rabbitTemplate).setConfirmCallback(confirmCallbackCaptor.capture());
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), correlationCaptor.capture());

        // Simulate a successful confirmation with the real correlation data, so the
        // publisher's id check matches and the future completes (a null/mismatched
        // CorrelationData would leave the future pending and hang the build).
        RabbitTemplate.ConfirmCallback confirmCallback = confirmCallbackCaptor.getValue();
        confirmCallback.confirm(correlationCaptor.getValue(), true, null);

        // Verify the result (bounded wait so a regression can never hang indefinitely)
        assertTrue(result.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testPublishAsyncWithPublisherConfirmsAndRejection() throws Exception {
        // Enable publisher confirms
        when(publisherProperties.isConfirms()).thenReturn(true);

        // Capture both the confirm callback and the CorrelationData the publisher generates
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCallbackCaptor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the confirm callback was set and capture the correlation data sent
        verify(rabbitTemplate).setConfirmCallback(confirmCallbackCaptor.capture());
        verify(rabbitTemplate).send(eq(defaultExchange), eq(defaultRoutingKey), any(Message.class), correlationCaptor.capture());

        // Simulate a rejection with the real correlation data so the future completes
        RabbitTemplate.ConfirmCallback confirmCallback = confirmCallbackCaptor.getValue();
        confirmCallback.confirm(correlationCaptor.getValue(), false, "Test rejection");

        // Verify the result (bounded wait so a regression can never hang indefinitely)
        assertFalse(result.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testCreateMessage() {
        // Set up headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");

        // Use a non-String, non-byte[] payload so createMessage routes through the
        // RabbitTemplate message converter (String/byte[] payloads are serialized
        // directly and intentionally bypass the converter).
        Object objectPayload = new java.util.ArrayList<>(java.util.List.of("a", "b"));
        Message converted = new Message("converted-body".getBytes(), new MessageProperties());
        when(rabbitTemplate.getMessageConverter().toMessage(eq(objectPayload), any())).thenReturn(converted);

        // Call the method using reflection
        Message message = null;
        try {
            java.lang.reflect.Method createMessageMethod = RabbitMQMessagePublisher.class.getDeclaredMethod(
                    "createMessage", Map.class, Object.class);
            createMessageMethod.setAccessible(true);
            message = (Message) createMessageMethod.invoke(publisher, headers, objectPayload);
        } catch (Exception e) {
            fail("Failed to call createMessage method: " + e.getMessage());
        }

        // Verify the message
        assertNotNull(message);

        // Verify the message converter was used for the object payload
        verify(rabbitTemplate.getMessageConverter()).toMessage(eq(objectPayload), any());
    }
}