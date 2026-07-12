package com.adhar.adharkit.messaging.kafka;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.kafka.KafkaMessageListener;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        // These stubs are shared across the subscribe-oriented tests; the pause/resume/
        // unsubscribe/context tests legitimately do not exercise them, so they are marked
        // lenient to avoid UnnecessaryStubbingException under Mockito strict stubbing.
        lenient().when(properties.getKafka()).thenReturn(kafkaProperties);
        lenient().when(kafkaProperties.getConsumer()).thenReturn(consumerProperties);
        lenient().when(consumerProperties.getGroupId()).thenReturn(defaultGroupId);
        lenient().when(consumerProperties.isEnableAutoCommit()).thenReturn(true);

        lenient().when(kafkaListenerContainerFactory.createContainer(anyString())).thenReturn(container);
        lenient().when(container.getContainerProperties()).thenReturn(containerProperties);

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
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
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
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
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
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

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
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

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
            Class<?> contextClass = Class.forName("com.adhar.kit.messaging.kafka.KafkaMessageListener$KafkaMessageContext");
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

    @Test
    void testListenerDeliversValueToConsumer() {
        AtomicReference<String> received = new AtomicReference<>();
        Consumer<String> consumer = received::set;

        listener.subscribe(testTopic, String.class, consumer);

        // Drive the captured listener so the registered handler lambda + processRecord run.
        captureKafkaListener().onMessage(buildRecord("value"));

        assertEquals("value", received.get());
    }

    @Test
    void testListenerWithHeadersDeliversValueAndHeaders() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        AtomicReference<Map<String, Object>> headersRef = new AtomicReference<>();
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer =
                (payload, headers) -> {
                    payloadRef.set(payload);
                    headersRef.set(headers);
                };

        listener.subscribeWithHeaders(testTopic, String.class, consumer);

        ConsumerRecord<String, Object> record = buildRecord("hv");
        record.headers().add("h1", "v1".getBytes());
        captureKafkaListener().onMessage(record);

        assertEquals("hv", payloadRef.get());
        assertNotNull(headersRef.get());
        assertEquals("v1", headersRef.get().get("h1"));
    }

    @Test
    void testListenerWithAckProcessesValueOnSuccess() {
        AtomicReference<String> received = new AtomicReference<>();
        Function<String, Boolean> consumer = payload -> {
            received.set(payload);
            return true;
        };

        listener.subscribeWithAck(testTopic, String.class, consumer);
        captureKafkaListener().onMessage(buildRecord("acked"));

        assertEquals("acked", received.get());
    }

    @Test
    void testListenerWithAckHandlesFalse() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Function<String, Boolean> consumer = payload -> {
            invoked.set(true);
            return false; // exercise the reject branch
        };

        listener.subscribeWithAck(testTopic, String.class, consumer);
        captureKafkaListener().onMessage(buildRecord("rejected"));

        assertTrue(invoked.get());
    }

    @Test
    void testListenerWithAckHandlesException() {
        Function<String, Boolean> consumer = payload -> {
            throw new RuntimeException("handler boom");
        };

        listener.subscribeWithAck(testTopic, String.class, consumer);

        // The handler's catch block swallows the error; onMessage must not propagate it.
        assertDoesNotThrow(() -> captureKafkaListener().onMessage(buildRecord("boom")));
    }

    @Test
    void testListenerWithHeadersAndAckProcessesValue() {
        AtomicReference<String> received = new AtomicReference<>();
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer =
                (payload, headers) -> {
                    received.set(payload);
                    return true;
                };

        listener.subscribeWithHeadersAndAck(testTopic, String.class, consumer);
        captureKafkaListener().onMessage(buildRecord("hav"));

        assertEquals("hav", received.get());
    }

    @Test
    void testRegisterHandlerUsesManualAckModeWhenAutoCommitDisabled() {
        when(consumerProperties.isEnableAutoCommit()).thenReturn(false);

        listener.subscribe(testTopic, String.class, payload -> { });

        verify(containerProperties).setAckMode(ContainerProperties.AckMode.MANUAL);
    }

    @Test
    void testProcessRecordHandlesException() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        listener.subscribe(testTopic, String.class, payload -> invoked.set(true));

        // A record that fails during header extraction must be caught inside processRecord.
        @SuppressWarnings("unchecked")
        ConsumerRecord<String, Object> badRecord = mock(ConsumerRecord.class);
        when(badRecord.headers()).thenThrow(new RuntimeException("headers boom"));
        when(badRecord.topic()).thenReturn(testTopic);

        assertDoesNotThrow(() -> captureKafkaListener().onMessage(badRecord));
        assertFalse(invoked.get(), "handler must not run when record processing fails early");
    }

    @Test
    void testKafkaMessageContextAcknowledgmentSuccess() throws Exception {
        Object context = newKafkaContext();
        Acknowledgment ack = mock(Acknowledgment.class);
        setAcknowledgment(context, ack);

        MessageHandler.MessageContext mc = (MessageHandler.MessageContext) context;
        assertTrue(mc.acknowledge());
        verify(ack).acknowledge();

        // When an Acknowledgment is present, acknowledge() succeeds so reject() returns false.
        assertFalse(mc.reject(true));
    }

    @Test
    void testKafkaMessageContextAcknowledgmentFailure() throws Exception {
        Object context = newKafkaContext();
        Acknowledgment ack = mock(Acknowledgment.class);
        doThrow(new RuntimeException("ack boom")).when(ack).acknowledge();
        setAcknowledgment(context, ack);

        MessageHandler.MessageContext mc = (MessageHandler.MessageContext) context;
        // The broker exception is swallowed and reported as a failed acknowledgment.
        assertFalse(mc.acknowledge());
    }

    /**
     * Captures the Spring Kafka {@link MessageListener} registered on the container properties.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private MessageListener<String, Object> captureKafkaListener() {
        ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
        verify(containerProperties).setMessageListener(captor.capture());
        return captor.getValue();
    }

    /**
     * Builds a simple Kafka consumer record with the given value.
     */
    private ConsumerRecord<String, Object> buildRecord(Object value) {
        return new ConsumerRecord<>(testTopic, 0, 0L, "key", value);
    }

    /**
     * Creates a KafkaMessageContext via reflection.
     */
    private Object newKafkaContext() {
        try {
            Class<?> contextClass = Class.forName(
                    "com.adhar.kit.messaging.kafka.KafkaMessageListener$KafkaMessageContext");
            java.lang.reflect.Constructor<?> constructor = contextClass.getDeclaredConstructor(
                    String.class, String.class, String.class, long.class, String.class, String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance("s", "d", "rk", 1L, "cid", "grp", "mid");
        } catch (Exception e) {
            fail("Failed to create KafkaMessageContext: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sets the Acknowledgment on a KafkaMessageContext via reflection.
     */
    private void setAcknowledgment(Object context, Acknowledgment ack) {
        try {
            java.lang.reflect.Method m = context.getClass().getDeclaredMethod("setAcknowledgment", Acknowledgment.class);
            m.setAccessible(true);
            m.invoke(context, ack);
        } catch (Exception e) {
            fail("Failed to set acknowledgment: " + e.getMessage());
        }
    }
}