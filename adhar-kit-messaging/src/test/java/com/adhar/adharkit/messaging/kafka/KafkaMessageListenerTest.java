package com.adhar.adharkit.messaging.kafka;

import com.adhar.adharkit.messaging.core.MessageHandler;
import com.adhar.adharkit.messaging.properties.AdharMessagingProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link KafkaMessageListener} class.
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

    @Mock
    private ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory;

    @Mock
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Mock
    private AdharMessagingProperties properties;

    @Mock
    private AdharMessagingProperties.KafkaProperties kafkaProperties;

    @Mock
    private AdharMessagingProperties.KafkaProperties.ConsumerProperties consumerProperties;

    @Mock
    private ConcurrentMessageListenerContainer<String, Object> container;

    @Mock
    private ContainerProperties containerProperties;

    private KafkaMessageListener listener;

    private final String defaultGroupId = "default-group";
    private final String testTopic = "test-topic";
    private final String testPayload = "Test payload";

    @BeforeEach
    void setUp() {
        when(properties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getConsumer()).thenReturn(consumerProperties);
        when(consumerProperties.getGroupId()).thenReturn(defaultGroupId);
        when(consumerProperties.isEnableAutoCommit()).thenReturn(true);

        when(kafkaListenerContainerFactory.createContainer(anyString())).thenReturn(container);
        when(container.getContainerProperties()).thenReturn(containerProperties);

        listener = new KafkaMessageListener(kafkaListenerContainerFactory, kafkaListenerEndpointRegistry, properties);
    }

    @Test
    void testSubscribeWithPayload() {
        // Set up a consumer
        Consumer<String> consumer = payload -> {
            // Do nothing
        };

        // Call the method
        String consumerId = listener.subscribe(testTopic, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(defaultGroupId));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(defaultGroupId);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithGroup() {
        // Set up a consumer
        Consumer<String> consumer = payload -> {
            // Do nothing
        };

        // Call the method
        String customGroup = "custom-group";
        String consumerId = listener.subscribe(testTopic, customGroup, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(customGroup));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(customGroup);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeaders() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
            // Do nothing
        };

        // Call the method
        String consumerId = listener.subscribeWithHeaders(testTopic, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(defaultGroupId));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(defaultGroupId);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndGroup() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
            // Do nothing
        };

        // Call the method
        String customGroup = "custom-group";
        String consumerId = listener.subscribeWithHeaders(testTopic, customGroup, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(customGroup));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(customGroup);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithAck() {
        // Set up a consumer
        Function<String, Boolean> consumer = payload -> true;

        // Call the method
        String consumerId = listener.subscribeWithAck(testTopic, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(defaultGroupId));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(defaultGroupId);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithAckAndGroup() {
        // Set up a consumer
        Function<String, Boolean> consumer = payload -> true;

        // Call the method
        String customGroup = "custom-group";
        String consumerId = listener.subscribeWithAck(testTopic, customGroup, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(customGroup));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(customGroup);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndAck() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

        // Call the method
        String consumerId = listener.subscribeWithHeadersAndAck(testTopic, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(defaultGroupId));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(defaultGroupId);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndAckAndGroup() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

        // Call the method
        String customGroup = "custom-group";
        String consumerId = listener.subscribeWithHeadersAndAck(testTopic, customGroup, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(testTopic));
        assertTrue(consumerId.contains(customGroup));
        verify(kafkaListenerContainerFactory).createContainer(testTopic);
        verify(containerProperties).setGroupId(customGroup);
        verify(containerProperties).setMessageListener(any(MessageListener.class));
        verify(container).start();
    }

    @Test
    void testUnsubscribe() {
        // Set up a mock container
        Map<String, MessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);
        
        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = KafkaMessageListener.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            containersField.set(listener, containers);
        } catch (Exception e) {
            fail("Failed to set containers field: " + e.getMessage());
        }

        // Call the method
        boolean result = listener.unsubscribe("test-consumer-id");

        // Verify the result and interactions
        assertTrue(result);
        verify(container).stop();
        assertTrue(containers.isEmpty());
    }

    @Test
    void testUnsubscribeWithInvalidConsumerId() {
        // Call the method
        boolean result = listener.unsubscribe("invalid-consumer-id");

        // Verify the result
        assertFalse(result);
    }

    @Test
    void testPause() {
        // Set up a mock container
        Map<String, MessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);
        
        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = KafkaMessageListener.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            containersField.set(listener, containers);
        } catch (Exception e) {
            fail("Failed to set containers field: " + e.getMessage());
        }

        // Call the method
        boolean result = listener.pause("test-consumer-id");

        // Verify the result and interactions
        assertTrue(result);
        verify(container).pause();
    }

    @Test
    void testPauseWithInvalidConsumerId() {
        // Call the method
        boolean result = listener.pause("invalid-consumer-id");

        // Verify the result
        assertFalse(result);
    }

    @Test
    void testResume() {
        // Set up a mock container
        Map<String, MessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);
        
        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = KafkaMessageListener.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            containersField.set(listener, containers);
        } catch (Exception e) {
            fail("Failed to set containers field: " + e.getMessage());
        }

        // Call the method
        boolean result = listener.resume("test-consumer-id");

        // Verify the result and interactions
        assertTrue(result);
        verify(container).resume();
    }

    @Test
    void testResumeWithInvalidConsumerId() {
        // Call the method
        boolean result = listener.resume("invalid-consumer-id");

        // Verify the result
        assertFalse(result);
    }

    @Test
    void testProcessRecord() {
        // Set up a mock consumer record
        ConsumerRecord<String, String> record = new ConsumerRecord<>(testTopic, 0, 0, "key", testPayload);
        
        // Set up a mock message handler
        @SuppressWarnings("unchecked")
        MessageHandler<String> handler = mock(MessageHandler.class);
        when(handler.handle(any(), any(), any())).thenReturn(true);
        
        // Call the method using reflection
        try {
            java.lang.reflect.Method processRecordMethod = KafkaMessageListener.class.getDeclaredMethod(
                    "processRecord", ConsumerRecord.class, MessageHandler.class, String.class, String.class, String.class);
            processRecordMethod.setAccessible(true);
            processRecordMethod.invoke(listener, record, handler, "test-consumer-id", testTopic, defaultGroupId);
        } catch (Exception e) {
            fail("Failed to call processRecord method: " + e.getMessage());
        }
        
        // Verify the interactions
        verify(handler).handle(eq(testPayload), any(), any());
    }

    @Test
    void testKafkaMessageContext() {
        // Create a KafkaMessageContext
        String source = "source";
        String destination = "destination";
        String routingKey = "routingKey";
        long timestamp = System.currentTimeMillis();
        String consumerId = "consumerId";
        String consumerGroup = "consumerGroup";
        String messageId = "messageId";
        
        // Create the context using reflection
        Object context = null;
        try {
            Class<?> contextClass = Class.forName("com.adhar.adharkit.messaging.kafka.KafkaMessageListener$KafkaMessageContext");
            java.lang.reflect.Constructor<?> constructor = contextClass.getDeclaredConstructor(
                    String.class, String.class, String.class, long.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            context = constructor.newInstance(source, destination, routingKey, timestamp, consumerId, consumerGroup, messageId);
        } catch (Exception e) {
            fail("Failed to create KafkaMessageContext: " + e.getMessage());
        }
        
        // Verify the context properties
        assertNotNull(context);
        assertTrue(context instanceof MessageHandler.MessageContext);
        MessageHandler.MessageContext messageContext = (MessageHandler.MessageContext) context;
        assertEquals(source, messageContext.getSource());
        assertEquals(destination, messageContext.getDestination());
        assertEquals(routingKey, messageContext.getRoutingKey());
        assertEquals(timestamp, messageContext.getTimestamp());
        assertEquals(consumerId, messageContext.getConsumerId());
        assertEquals(consumerGroup, messageContext.getConsumerGroup());
        assertEquals(messageId, messageContext.getMessageId());
        
        // Test attribute methods
        messageContext.setAttribute("key", "value");
        assertEquals("value", messageContext.getAttribute("key"));
        
        // Test acknowledge method
        assertTrue(messageContext.acknowledge());
        
        // Test reject method
        assertFalse(messageContext.reject(true));
    }
}