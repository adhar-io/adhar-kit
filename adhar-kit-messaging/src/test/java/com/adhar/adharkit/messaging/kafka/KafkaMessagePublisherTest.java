package com.adhar.adharkit.messaging.kafka;

import com.adhar.adharkit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link KafkaMessagePublisher} class.
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessagePublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private AdharMessagingProperties properties;

    @Mock
    private AdharMessagingProperties.KafkaProperties kafkaProperties;

    @Mock
    private AdharMessagingProperties.CommonProperties commonProperties;

    private KafkaMessagePublisher publisher;

    private final String defaultTopic = "default-topic";
    private final String testPayload = "Test payload";

    @BeforeEach
    void setUp() {
        when(properties.getKafka()).thenReturn(kafkaProperties);
        when(properties.getCommon()).thenReturn(commonProperties);
        when(kafkaProperties.getDefaultTopic()).thenReturn(defaultTopic);
        when(commonProperties.isTraceEnabled()).thenReturn(true);

        publisher = new KafkaMessagePublisher(kafkaTemplate, properties);
    }

    @Test
    void testPublishWithPayload() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(kafkaTemplate).send(any(Message.class));
        verify(kafkaProperties).getDefaultTopic();
    }

    @Test
    void testPublishWithDestination() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        boolean result = publisher.publish(destination, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(kafkaTemplate).send(any(Message.class));
        verify(kafkaProperties, never()).getDefaultTopic(); // Should not use default topic
    }

    @Test
    void testPublishWithDestinationAndRoutingKey() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        String routingKey = "test-key";
        boolean result = publisher.publish(destination, routingKey, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishWithDestinationAndHeaders() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        boolean result = publisher.publish(destination, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishWithDestinationRoutingKeyAndHeaders() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        String routingKey = "test-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        boolean result = publisher.publish(destination, routingKey, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishWithInterruptedException() {
        // Mock the KafkaTemplate to throw an InterruptedException
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new InterruptedException("Test exception"));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertFalse(result);
        verify(kafkaTemplate).send(any(Message.class));
        assertTrue(Thread.currentThread().isInterrupted()); // Thread should be interrupted
        
        // Reset interrupted flag
        Thread.interrupted();
    }

    @Test
    void testPublishWithExecutionException() {
        // Mock the KafkaTemplate to throw an ExecutionException
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new ExecutionException(new RuntimeException("Test exception")));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertFalse(result);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishWithRuntimeException() {
        // Mock the KafkaTemplate to throw a RuntimeException
        when(kafkaTemplate.send(any(Message.class))).thenThrow(new RuntimeException("Test exception"));

        // Call the method
        boolean result = publisher.publish(testPayload);

        // Verify the result and interactions
        assertFalse(result);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishAsyncWithPayload() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
        verify(kafkaProperties).getDefaultTopic();
    }

    @Test
    void testPublishAsyncWithDestination() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
        verify(kafkaProperties, never()).getDefaultTopic(); // Should not use default topic
    }

    @Test
    void testPublishAsyncWithDestinationAndRoutingKey() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        String routingKey = "test-key";
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, routingKey, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishAsyncWithDestinationAndHeaders() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishAsyncWithDestinationRoutingKeyAndHeaders() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        String destination = "test-topic";
        String routingKey = "test-key";
        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        CompletableFuture<Boolean> result = publisher.publishAsync(destination, routingKey, headers, testPayload);

        // Verify the result and interactions
        assertTrue(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishAsyncWithException() {
        // Mock the KafkaTemplate to throw an exception
        when(kafkaTemplate.send(any(Message.class))).thenThrow(new RuntimeException("Test exception"));

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the result and interactions
        assertFalse(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void testPublishAsyncWithCompletionException() {
        // Mock the KafkaTemplate response
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Test exception"));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(future);

        // Call the method
        CompletableFuture<Boolean> result = publisher.publishAsync(testPayload);

        // Verify the result and interactions
        assertFalse(result.join()); // Wait for the future to complete and get the result
        verify(kafkaTemplate).send(any(Message.class));
    }
}