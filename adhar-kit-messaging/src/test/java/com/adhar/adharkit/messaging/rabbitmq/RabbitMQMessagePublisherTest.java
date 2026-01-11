package com.adhar.adharkit.messaging.rabbitmq;

import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.rabbitmq.RabbitMQMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link RabbitMQMessagePublisher} class.
 */
@ExtendWith(MockitoExtension.class)
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
    void testPublishAsyncWithPublisherConfirms() {
        // Enable publisher confirms
        when(publisherProperties.isConfirms()).thenReturn(true);

        // Set up the confirm callback
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCallbackCaptor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        
        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);
        
        // Verify the confirm callback was set
        verify(rabbitTemplate).setConfirmCallback(confirmCallbackCaptor.capture());
        
        // Simulate a successful confirmation
        RabbitTemplate.ConfirmCallback confirmCallback = confirmCallbackCaptor.getValue();
        confirmCallback.confirm(any(CorrelationData.class), true, null);
        
        // Verify the result
        assertTrue(result.join()); // Wait for the future to complete and get the result
    }

    @Test
    void testPublishAsyncWithPublisherConfirmsAndRejection() {
        // Enable publisher confirms
        when(publisherProperties.isConfirms()).thenReturn(true);

        // Set up the confirm callback
        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCallbackCaptor = ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        
        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);
        
        // Verify the confirm callback was set
        verify(rabbitTemplate).setConfirmCallback(confirmCallbackCaptor.capture());
        
        // Simulate a rejection
        RabbitTemplate.ConfirmCallback confirmCallback = confirmCallbackCaptor.getValue();
        confirmCallback.confirm(any(CorrelationData.class), false, "Test rejection");
        
        // Verify the result
        assertFalse(result.join()); // Wait for the future to complete and get the result
    }

    @Test
    void testCreateMessage() {
        // Set up headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        
        // Call the method using reflection
        Message message = null;
        try {
            java.lang.reflect.Method createMessageMethod = RabbitMQMessagePublisher.class.getDeclaredMethod(
                    "createMessage", Map.class, Object.class);
            createMessageMethod.setAccessible(true);
            message = (Message) createMessageMethod.invoke(publisher, headers, testPayload);
        } catch (Exception e) {
            fail("Failed to call createMessage method: " + e.getMessage());
        }
        
        // Verify the message
        assertNotNull(message);
        
        // Verify the message converter was used
        verify(rabbitTemplate.getMessageConverter()).toMessage(eq(testPayload), any());
    }
}