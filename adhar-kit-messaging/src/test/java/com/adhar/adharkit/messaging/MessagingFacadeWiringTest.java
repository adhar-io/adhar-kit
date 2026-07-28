package com.adhar.adharkit.messaging;

import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the broker-wired {@link MessagingFacade} constructor - i.e. the facade as it
 * is assembled by {@code MessagingAutoConfiguration}, exercised here with mocked
 * {@link MessagePublisher}/{@link MessageListener} collaborators so no broker or Docker
 * is required. This complements {@link MessagingFacadeTest}, which covers the legacy
 * no-arg stub singleton.
 */
class MessagingFacadeWiringTest {

    private MessagePublisher messagePublisher;
    private MessageListener messageListener;
    private AdharMessagingProperties properties;

    @BeforeEach
    void setUp() {
        messagePublisher = mock(MessagePublisher.class);
        messageListener = mock(MessageListener.class);
        properties = new AdharMessagingProperties();
        // Keep retry backoff effectively instantaneous for tests that exercise it.
        properties.getCommon().getRetry().setInitialDelayMs(1);
        properties.getCommon().getRetry().setMaxDelayMs(1);
    }

    @Test
    void publishDelegatesToMessagePublisher() {
        when(messagePublisher.publish("order-events", "payload")).thenReturn(true);
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        facade.publish("order-events", "payload");

        verify(messagePublisher).publish("order-events", "payload");
    }

    @Test
    void publishWithKeyDelegatesToMessagePublisher() {
        when(messagePublisher.publish("order-events", "customer-1", "payload")).thenReturn(true);
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        facade.publish("order-events", "customer-1", "payload");

        verify(messagePublisher).publish("order-events", "customer-1", "payload");
    }

    @Test
    void publishWithoutPublisherLogsAndDoesNotThrow() {
        MessagingFacade facade = new MessagingFacade(null, messageListener, properties, null);

        assertDoesNotThrow(() -> facade.publish("order-events", "payload"));
        assertDoesNotThrow(() -> facade.publish("order-events", "key", "payload"));
    }

    @Test
    void publishRecordsMetricsOnSuccessAndFailure() {
        when(messagePublisher.publish("ok-topic", "payload")).thenReturn(true);
        when(messagePublisher.publish("bad-topic", "payload")).thenReturn(false);
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, metrics);

        facade.publish("ok-topic", "payload");
        facade.publish("bad-topic", "payload");

        verify(metrics).recordPublish("ok-topic");
        verify(metrics).recordPublishFailure("bad-topic");
    }

    @Test
    void subscribeWithoutListenerStoresHandlerOnly() {
        MessagingFacade facade = new MessagingFacade(messagePublisher, null, properties, null);
        int before = facade.getSubscriptionCount();

        String id = facade.subscribe("order-events", String.class, payload -> { });

        assertNotNull(id);
        assertEquals(before + 1, facade.getSubscriptionCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeWiresThroughToMessageListenerAndDeliversPayload() {
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), any()))
                .thenReturn("delegate-consumer-1");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        AtomicReference<String> received = new AtomicReference<>();
        String subscriptionId = facade.subscribe("order-events", "order-group", String.class, received::set);

        assertNotNull(subscriptionId);
        MessageListener.BiFunction<String, Map<String, Object>, Boolean> delivered = captureBiFunction();

        Boolean result = delivered.apply("hello", Map.of());

        assertTrue(result);
        assertEquals("hello", received.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsubscribeDelegatesToMessageListener() {
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), any()))
                .thenReturn("delegate-consumer-1");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        String subscriptionId = facade.subscribe("order-events", "order-group", String.class, payload -> { });
        facade.unsubscribe(subscriptionId);

        verify(messageListener).unsubscribe("delegate-consumer-1");
    }

    @Test
    void unsubscribeUnknownIdIsNoOpEvenWithListenerWired() {
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        assertDoesNotThrow(() -> facade.unsubscribe("does-not-exist"));
        verify(messageListener, never()).unsubscribe(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryIsAppliedAndSucceedsBeforeExhaustingAttempts() {
        properties.getCommon().getRetry().setMaxAttempts(3);
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), any()))
                .thenReturn("delegate-consumer-1");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        AtomicInteger calls = new AtomicInteger();
        facade.subscribe("order-events", "order-group", String.class, payload -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("transient failure");
            }
        });

        MessageListener.BiFunction<String, Map<String, Object>, Boolean> delivered = captureBiFunction();
        Boolean result = delivered.apply("payload", Map.of());

        assertTrue(result);
        assertEquals(2, calls.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exhaustedRetriesRouteToDeadLetterQueue() {
        properties.getCommon().getRetry().setMaxAttempts(2);
        properties.getCommon().getDlq().setEnabled(true);
        when(messagePublisher.publish(eq("order-events.dlq"), anyMap(), any())).thenReturn(true);
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), any()))
                .thenReturn("delegate-consumer-1");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        facade.subscribe("order-events", "order-group", String.class, payload -> {
            throw new RuntimeException("always fails");
        });

        MessageListener.BiFunction<String, Map<String, Object>, Boolean> delivered = captureBiFunction();
        Boolean result = delivered.apply("payload", Map.of());

        assertFalse(result);
        verify(messagePublisher).publish(eq("order-events.dlq"), anyMap(), eq("payload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deduplicationSkipsSecondDeliveryOfSameMessageId() {
        properties.getCommon().getDedup().setEnabled(true);
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), any()))
                .thenReturn("delegate-consumer-1");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        AtomicInteger calls = new AtomicInteger();
        facade.subscribe("order-events", "order-group", String.class, payload -> calls.incrementAndGet());

        MessageListener.BiFunction<String, Map<String, Object>, Boolean> delivered = captureBiFunction();
        Map<String, Object> headers = new HashMap<>();
        headers.put("ce-id", "event-1");

        assertTrue(delivered.apply("payload", headers));
        assertTrue(delivered.apply("payload", headers));

        assertEquals(1, calls.get(), "the duplicate delivery must not invoke the handler again");
    }

    @Test
    void sendAndReceiveWithoutRequestReplyClientThrows() {
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        assertThrows(com.adhar.kit.messaging.exception.MessagingException.class,
                () -> facade.sendAndReceive("order-queries", "request", String.class));
    }

    @Test
    void sendAndReceiveDelegatesToRequestReplyClient() {
        com.adhar.kit.messaging.requestreply.RequestReplyClient client =
                mock(com.adhar.kit.messaging.requestreply.RequestReplyClient.class);
        properties.getCommon().getReply().setTimeoutMs(1234);
        when(client.sendAndReceive(eq("order-queries"), eq("request"), eq(String.class), any()))
                .thenReturn("reply");
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null,
                null, null, client);

        String reply = facade.sendAndReceive("order-queries", "request", String.class);

        assertEquals("reply", reply);
        verify(client).sendAndReceive(eq("order-queries"), eq("request"), eq(String.class),
                eq(java.time.Duration.ofMillis(1234)));
    }

    @Test
    void publishViaOutboxWithoutStoreThrows() {
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null);

        assertThrows(com.adhar.kit.messaging.exception.MessagingException.class,
                () -> facade.publishViaOutbox("order-events", "payload"));
    }

    @Test
    void publishViaOutboxPersistsEntryToStore() {
        com.adhar.kit.messaging.outbox.InMemoryOutboxStore store =
                new com.adhar.kit.messaging.outbox.InMemoryOutboxStore();
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null,
                store, null, null);

        String id = facade.publishViaOutbox("order-events", "customer-1", "payload");

        assertNotNull(id);
        assertEquals(1, store.countByStatus(com.adhar.kit.messaging.outbox.OutboxStatus.PENDING));
        com.adhar.kit.messaging.outbox.OutboxEntry entry = store.findById(id).orElseThrow();
        assertEquals("order-events", entry.getDestination());
        assertEquals("customer-1", entry.getRoutingKey());
        assertEquals(String.class.getName(), entry.getPayloadType());
    }

    @Test
    void publishViaOutboxRejectsInvalidArguments() {
        com.adhar.kit.messaging.outbox.InMemoryOutboxStore store =
                new com.adhar.kit.messaging.outbox.InMemoryOutboxStore();
        MessagingFacade facade = new MessagingFacade(messagePublisher, messageListener, properties, null,
                store, null, null);

        assertThrows(IllegalArgumentException.class, () -> facade.publishViaOutbox("", "payload"));
        assertThrows(IllegalArgumentException.class, () -> facade.publishViaOutbox("order-events", (Object) null));
    }

    @SuppressWarnings("unchecked")
    private MessageListener.BiFunction<String, Map<String, Object>, Boolean> captureBiFunction() {
        org.mockito.ArgumentCaptor<MessageListener.BiFunction> captor =
                org.mockito.ArgumentCaptor.forClass(MessageListener.BiFunction.class);
        verify(messageListener).subscribeWithHeadersAndAck(anyString(), anyString(), eq(String.class), captor.capture());
        return captor.getValue();
    }
}
