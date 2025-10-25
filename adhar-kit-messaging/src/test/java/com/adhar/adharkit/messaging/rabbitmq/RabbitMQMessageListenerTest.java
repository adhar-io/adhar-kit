package com.adhar.adharkit.messaging.rabbitmq;

import com.adhar.adharkit.messaging.core.MessageHandler;
import com.adhar.adharkit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for the {@link RabbitMQMessageListener} class.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQMessageListenerTest {

    @Mock
    private ConnectionFactory connectionFactory;

    @Mock
    private RabbitAdmin rabbitAdmin;

    @Mock
    private MessageConverter messageConverter;

    @Mock
    private AdharMessagingProperties properties;

    @Mock
    private AdharMessagingProperties.RabbitMQProperties rabbitMQProperties;

    @Mock
    private AdharMessagingProperties.RabbitMQProperties.ListenerProperties listenerProperties;

    @Mock
    private AdharMessagingProperties.KafkaProperties kafkaProperties;

    @Mock
    private AdharMessagingProperties.KafkaProperties.ConsumerProperties consumerProperties;

    @Mock
    private SimpleMessageListenerContainer container;

    private RabbitMQMessageListener listener;

    private final String defaultQueue = "default-queue";
    private final String defaultExchange = "default-exchange";
    private final String defaultRoutingKey = "default-routing-key";
    private final String testPayload = "Test payload";

    @BeforeEach
    void setUp() {
        when(properties.getRabbitmq()).thenReturn(rabbitMQProperties);
        when(properties.getKafka()).thenReturn(kafkaProperties);
        when(kafkaProperties.getConsumer()).thenReturn(consumerProperties);
        when(rabbitMQProperties.getListener()).thenReturn(listenerProperties);
        when(rabbitMQProperties.getDefaultQueue()).thenReturn(defaultQueue);
        when(rabbitMQProperties.getDefaultExchange()).thenReturn(defaultExchange);
        when(rabbitMQProperties.getDefaultRoutingKey()).thenReturn(defaultRoutingKey);
        when(consumerProperties.isEnableAutoCommit()).thenReturn(true);
        when(listenerProperties.isAutoDeclare()).thenReturn(true);

        // Mock SimpleMessageListenerContainer creation
        doReturn(container).when(connectionFactory).createConnection();

        listener = new RabbitMQMessageListener(connectionFactory, rabbitAdmin, messageConverter, properties);
    }

    @Test
    void testSubscribeWithPayload() {
        // Set up a consumer
        Consumer<String> consumer = payload -> {
            // Do nothing
        };

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String consumerId = listener.subscribe(defaultExchange, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(defaultQueue));
        verify(rabbitMQProperties).getDefaultQueue();
        verify(container).setQueueNames(defaultQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithGroup() {
        // Set up a consumer
        Consumer<String> consumer = payload -> {
            // Do nothing
        };

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String customQueue = "custom-queue";
        String consumerId = listener.subscribe(defaultExchange, customQueue, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(customQueue));
        verify(rabbitMQProperties, never()).getDefaultQueue(); // Should not use default queue
        verify(container).setQueueNames(customQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeaders() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
            // Do nothing
        };

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String consumerId = listener.subscribeWithHeaders(defaultExchange, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(defaultQueue));
        verify(rabbitMQProperties).getDefaultQueue();
        verify(container).setQueueNames(defaultQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndGroup() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
            // Do nothing
        };

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String customQueue = "custom-queue";
        String consumerId = listener.subscribeWithHeaders(defaultExchange, customQueue, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(customQueue));
        verify(rabbitMQProperties, never()).getDefaultQueue(); // Should not use default queue
        verify(container).setQueueNames(customQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithAck() {
        // Set up a consumer
        Function<String, Boolean> consumer = payload -> true;

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String consumerId = listener.subscribeWithAck(defaultExchange, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(defaultQueue));
        verify(rabbitMQProperties).getDefaultQueue();
        verify(container).setQueueNames(defaultQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithAckAndGroup() {
        // Set up a consumer
        Function<String, Boolean> consumer = payload -> true;

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String customQueue = "custom-queue";
        String consumerId = listener.subscribeWithAck(defaultExchange, customQueue, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(customQueue));
        verify(rabbitMQProperties, never()).getDefaultQueue(); // Should not use default queue
        verify(container).setQueueNames(customQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndAck() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String consumerId = listener.subscribeWithHeadersAndAck(defaultExchange, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(defaultQueue));
        verify(rabbitMQProperties).getDefaultQueue();
        verify(container).setQueueNames(defaultQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testSubscribeWithHeadersAndAckAndGroup() {
        // Set up a consumer
        com.adhar.adharkit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

        // Mock container creation
        mockContainerCreation();

        // Call the method
        String customQueue = "custom-queue";
        String consumerId = listener.subscribeWithHeadersAndAck(defaultExchange, customQueue, String.class, consumer);

        // Verify the result and interactions
        assertNotNull(consumerId);
        assertTrue(consumerId.contains(defaultExchange));
        assertTrue(consumerId.contains(customQueue));
        verify(rabbitMQProperties, never()).getDefaultQueue(); // Should not use default queue
        verify(container).setQueueNames(customQueue);
        verify(container).setMessageListener(any(ChannelAwareMessageListener.class));
        verify(container).start();
    }

    @Test
    void testUnsubscribe() {
        // Set up a mock container
        Map<String, SimpleMessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);

        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = RabbitMQMessageListener.class.getDeclaredField("containers");
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
        Map<String, SimpleMessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);

        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = RabbitMQMessageListener.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            containersField.set(listener, containers);
        } catch (Exception e) {
            fail("Failed to set containers field: " + e.getMessage());
        }

        // Call the method
        boolean result = listener.pause("test-consumer-id");

        // Verify the result and interactions
        assertTrue(result);
        verify(container).stop();
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
        Map<String, SimpleMessageListenerContainer> containers = new HashMap<>();
        containers.put("test-consumer-id", container);

        // Use reflection to set the containers field
        try {
            java.lang.reflect.Field containersField = RabbitMQMessageListener.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            containersField.set(listener, containers);
        } catch (Exception e) {
            fail("Failed to set containers field: " + e.getMessage());
        }

        // Call the method
        boolean result = listener.resume("test-consumer-id");

        // Verify the result and interactions
        assertTrue(result);
        verify(container).start();
    }

    @Test
    void testResumeWithInvalidConsumerId() {
        // Call the method
        boolean result = listener.resume("invalid-consumer-id");

        // Verify the result
        assertFalse(result);
    }

    @Test
    void testDeclareQueueAndExchange() {
        // Call the method using reflection
        try {
            java.lang.reflect.Method declareQueueAndExchangeMethod = RabbitMQMessageListener.class.getDeclaredMethod(
                    "declareQueueAndExchange", String.class, String.class);
            declareQueueAndExchangeMethod.setAccessible(true);
            declareQueueAndExchangeMethod.invoke(listener, defaultExchange, defaultQueue);
        } catch (Exception e) {
            fail("Failed to call declareQueueAndExchange method: " + e.getMessage());
        }

        // Verify the interactions
        verify(rabbitAdmin).declareExchange(any(Exchange.class));
        verify(rabbitAdmin).declareQueue(any(Queue.class));
        verify(rabbitAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void testRabbitMQMessageContext() {
        // Create a RabbitMQMessageContext
        String source = "source";
        String destination = "destination";
        String routingKey = "routingKey";
        long timestamp = System.currentTimeMillis();
        String consumerId = "consumerId";
        String consumerGroup = "consumerGroup";
        String messageId = "messageId";
        Message message = mock(Message.class);
        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // Create the context using reflection
        Object context = null;
        try {
            Class<?> contextClass = Class.forName("com.adhar.adharkit.messaging.rabbitmq.RabbitMQMessageListener$RabbitMQMessageContext");
            java.lang.reflect.Constructor<?> constructor = contextClass.getDeclaredConstructor(
                    String.class, String.class, String.class, long.class, String.class, String.class, String.class, 
                    Message.class, com.rabbitmq.client.Channel.class);
            constructor.setAccessible(true);
            context = constructor.newInstance(source, destination, routingKey, timestamp, consumerId, consumerGroup, messageId, message, channel);
        } catch (Exception e) {
            fail("Failed to create RabbitMQMessageContext: " + e.getMessage());
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
        try {
            messageContext.acknowledge();
            verify(channel).basicAck(anyLong(), eq(false));
        } catch (Exception e) {
            fail("Failed to acknowledge message: " + e.getMessage());
        }

        // Test reject method
        try {
            messageContext.reject(true);
            verify(channel).basicReject(anyLong(), eq(true));
        } catch (Exception e) {
            fail("Failed to reject message: " + e.getMessage());
        }
    }

    /**
     * Helper method to mock the container creation.
     */
    private void mockContainerCreation() {
        // Mock SimpleMessageListenerContainer creation
        doReturn(container).when(connectionFactory).createConnection();

        // Mock the container methods
        doNothing().when(container).setQueueNames(anyString());
        doNothing().when(container).setPrefetchCount(anyInt());
        doNothing().when(container).setConcurrentConsumers(anyInt());
        doNothing().when(container).setMaxConcurrentConsumers(anyInt());
        doNothing().when(container).setAcknowledgeMode(any(AcknowledgeMode.class));
        doNothing().when(container).setMessageListener(any(ChannelAwareMessageListener.class));
        doNothing().when(container).start();
    }

}
