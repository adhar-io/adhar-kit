package com.adhar.adharkit.messaging.rabbitmq;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.rabbitmq.RabbitMQMessageListener;
import com.rabbitmq.client.Channel;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private AdharMessagingProperties.RabbitMQProperties.PublisherProperties publisherProperties;

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
        // These stubs are shared across the subscribe-oriented tests; the pause/resume/
        // unsubscribe/declare/context tests legitimately do not exercise all of them, so they
        // are marked lenient to avoid UnnecessaryStubbingException under strict stubbing.
        lenient().when(properties.getRabbitmq()).thenReturn(rabbitMQProperties);
        lenient().when(properties.getKafka()).thenReturn(kafkaProperties);
        lenient().when(kafkaProperties.getConsumer()).thenReturn(consumerProperties);
        lenient().when(rabbitMQProperties.getListener()).thenReturn(listenerProperties);
        lenient().when(rabbitMQProperties.getPublisher()).thenReturn(publisherProperties);
        lenient().when(rabbitMQProperties.getDefaultQueue()).thenReturn(defaultQueue);
        lenient().when(rabbitMQProperties.getDefaultExchange()).thenReturn(defaultExchange);
        lenient().when(rabbitMQProperties.getDefaultRoutingKey()).thenReturn(defaultRoutingKey);
        lenient().when(consumerProperties.isEnableAutoCommit()).thenReturn(true);
        lenient().when(listenerProperties.isAutoDeclare()).thenReturn(true);

        // Spy the listener so container creation can be intercepted via the createListenerContainer() seam.
        listener = spy(new RabbitMQMessageListener(connectionFactory, rabbitAdmin, messageConverter, properties));
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
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
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
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer = (payload, headers) -> {
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
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

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
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer = (payload, headers) -> true;

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
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(42L);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // Create the context using reflection
        Object context = null;
        try {
            Class<?> contextClass = Class.forName("com.adhar.kit.messaging.rabbitmq.RabbitMQMessageListener$RabbitMQMessageContext");
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

    @Test
    void testListenerDeliversPayloadToConsumer() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        Consumer<String> consumer = received::set;
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("hello");

        listener.subscribe(defaultExchange, String.class, consumer);

        // Drive the captured listener so the registered handler lambda + processMessage run.
        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(7L), channel);

        assertEquals("hello", received.get());
    }

    @Test
    void testListenerWithHeadersDeliversPayloadAndHeaders() throws Exception {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        AtomicReference<Map<String, Object>> headersRef = new AtomicReference<>();
        com.adhar.kit.messaging.core.MessageListener.BiConsumer<String, Map<String, Object>> consumer =
                (payload, headers) -> {
                    payloadRef.set(payload);
                    headersRef.set(headers);
                };
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("with-headers");

        listener.subscribeWithHeaders(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Message message = buildMessage(8L);
        message.getMessageProperties().setHeader("h1", "v1");
        cal.onMessage(message, mock(Channel.class));

        assertEquals("with-headers", payloadRef.get());
        assertNotNull(headersRef.get());
        assertEquals("v1", headersRef.get().get("h1"));
    }

    @Test
    void testListenerWithAckAcknowledgesOnSuccess() throws Exception {
        Function<String, Boolean> consumer = payload -> true;
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("ack-me");

        listener.subscribeWithAck(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(9L), channel);

        // A successful handler must acknowledge the delivery.
        verify(channel).basicAck(eq(9L), eq(false));
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void testListenerWithAckRejectsOnFalse() throws Exception {
        Function<String, Boolean> consumer = payload -> false;
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("reject-me");

        listener.subscribeWithAck(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(10L), channel);

        // A handler returning false must requeue the delivery.
        verify(channel).basicReject(eq(10L), eq(true));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void testListenerWithAckRejectsOnException() throws Exception {
        Function<String, Boolean> consumer = payload -> {
            throw new RuntimeException("handler boom");
        };
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("boom");

        listener.subscribeWithAck(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(11L), channel);

        // A throwing handler must requeue the delivery via the catch block.
        verify(channel).basicReject(eq(11L), eq(true));
    }

    @Test
    void testListenerWithHeadersAndAckAcknowledgesOnSuccess() throws Exception {
        com.adhar.kit.messaging.core.MessageListener.BiFunction<String, Map<String, Object>, Boolean> consumer =
                (payload, headers) -> true;
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class))).thenReturn("ok");

        listener.subscribeWithHeadersAndAck(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(12L), channel);

        verify(channel).basicAck(eq(12L), eq(false));
    }

    @Test
    void testProcessMessageRethrowsOnConversionErrorWhenAutoCommit() throws Exception {
        // enableAutoCommit is true (default in setUp), so a conversion error must propagate
        // to let the container handle it.
        Consumer<String> consumer = payload -> { /* never reached */ };
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class)))
                .thenThrow(new RuntimeException("convert boom"));

        listener.subscribe(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        Message message = buildMessage(13L);
        assertThrows(Exception.class, () -> cal.onMessage(message, channel));
    }

    @Test
    void testProcessMessageRejectsOnConversionErrorWhenManualAck() throws Exception {
        // With manual acknowledgment a conversion error must reject (requeue) the delivery
        // rather than propagate.
        when(consumerProperties.isEnableAutoCommit()).thenReturn(false);
        Consumer<String> consumer = payload -> { /* never reached */ };
        mockContainerCreation();
        when(messageConverter.fromMessage(any(Message.class)))
                .thenThrow(new RuntimeException("convert boom"));

        listener.subscribe(defaultExchange, String.class, consumer);

        ChannelAwareMessageListener cal = captureMessageListener();
        Channel channel = mock(Channel.class);
        cal.onMessage(buildMessage(14L), channel);

        verify(channel).basicReject(eq(14L), eq(true));
    }

    @Test
    void testSubscribeWithoutAutoDeclareSkipsDeclaration() {
        when(listenerProperties.isAutoDeclare()).thenReturn(false);
        mockContainerCreation();

        listener.subscribe(defaultExchange, String.class, payload -> { });

        verify(rabbitAdmin, never()).declareExchange(any(Exchange.class));
        verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
        verify(rabbitAdmin, never()).declareBinding(any(Binding.class));
    }

    @Test
    void testDeclareQueueAndExchangeDurableBranch() {
        // Exercise the durable=true branches for both exchange and queue.
        when(publisherProperties.isExchangeDurable()).thenReturn(true);
        when(listenerProperties.isQueueDurable()).thenReturn(true);

        invokeDeclare(defaultExchange, defaultQueue);

        verify(rabbitAdmin).declareExchange(any(Exchange.class));
        verify(rabbitAdmin).declareQueue(any(Queue.class));
        verify(rabbitAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void testDeclareQueueAndExchangeFallsBackToDefaultsWhenBlank() {
        // Blank source/group must fall back to the configured default exchange/queue.
        invokeDeclare("", "");

        verify(rabbitMQProperties).getDefaultExchange();
        verify(rabbitMQProperties).getDefaultQueue();
        verify(rabbitAdmin).declareExchange(any(Exchange.class));
        verify(rabbitAdmin).declareQueue(any(Queue.class));
        verify(rabbitAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void testRabbitMQMessageContextAckAndRejectFailureReturnsFalse() throws Exception {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(99L);
        Message message = mock(Message.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        Channel channel = mock(Channel.class);
        doThrow(new RuntimeException("ack boom")).when(channel).basicAck(anyLong(), anyBoolean());
        doThrow(new RuntimeException("reject boom")).when(channel).basicReject(anyLong(), anyBoolean());

        MessageHandler.MessageContext context = newContext("s", "d", "rk", 1L,
                "cid", "grp", "mid", message, channel);

        // Both operations must swallow the broker exception and report failure.
        assertFalse(context.acknowledge());
        assertFalse(context.reject(true));
    }

    /**
     * Captures the {@link ChannelAwareMessageListener} the listener registers on the container.
     */
    private ChannelAwareMessageListener captureMessageListener() {
        ArgumentCaptor<ChannelAwareMessageListener> captor =
                ArgumentCaptor.forClass(ChannelAwareMessageListener.class);
        verify(container).setMessageListener(captor.capture());
        return captor.getValue();
    }

    /**
     * Builds a simple AMQP message with the given delivery tag.
     */
    private Message buildMessage(long deliveryTag) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        return new Message("body".getBytes(), props);
    }

    /**
     * Invokes the private declareQueueAndExchange method via reflection.
     */
    private void invokeDeclare(String source, String group) {
        try {
            java.lang.reflect.Method m = RabbitMQMessageListener.class.getDeclaredMethod(
                    "declareQueueAndExchange", String.class, String.class);
            m.setAccessible(true);
            m.invoke(listener, source, group);
        } catch (Exception e) {
            fail("Failed to call declareQueueAndExchange: " + e.getMessage());
        }
    }

    /**
     * Creates a RabbitMQMessageContext via reflection.
     */
    private MessageHandler.MessageContext newContext(String source, String destination, String routingKey,
                                                     long timestamp, String consumerId, String consumerGroup,
                                                     String messageId, Message message, Channel channel) {
        try {
            Class<?> contextClass = Class.forName(
                    "com.adhar.kit.messaging.rabbitmq.RabbitMQMessageListener$RabbitMQMessageContext");
            java.lang.reflect.Constructor<?> constructor = contextClass.getDeclaredConstructor(
                    String.class, String.class, String.class, long.class, String.class, String.class,
                    String.class, Message.class, Channel.class);
            constructor.setAccessible(true);
            return (MessageHandler.MessageContext) constructor.newInstance(
                    source, destination, routingKey, timestamp, consumerId, consumerGroup, messageId, message, channel);
        } catch (Exception e) {
            fail("Failed to create RabbitMQMessageContext: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to mock the container creation.
     */
    private void mockContainerCreation() {
        // Intercept container creation so the product uses the mock container.
        doReturn(container).when(listener).createListenerContainer();
    }

}
