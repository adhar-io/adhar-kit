package com.adhar.adharkit.messaging.requestreply;

import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.requestreply.KafkaRequestReplyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link KafkaRequestReplyClient} with a mocked {@link KafkaTemplate}/
 * {@link MessageListener} - no Kafka broker is started.
 */
class KafkaRequestReplyClientTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final MessageListener messageListener = mock(MessageListener.class);
    private AdharMessagingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AdharMessagingProperties();
    }

    private KafkaRequestReplyClient clientWithFixedCorrelation(String correlationId) {
        return new KafkaRequestReplyClient(kafkaTemplate, messageListener, properties,
                new ObjectMapper(), () -> correlationId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendAndReceiveCompletesWhenReplyArrives() throws Exception {
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(Object.class), any()))
                .thenReturn("reply-consumer");
        KafkaRequestReplyClient client = clientWithFixedCorrelation("corr-1");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> result = CompletableFuture.supplyAsync(
                    () -> client.sendAndReceive("orders", "request", String.class, Duration.ofSeconds(5)), executor);

            // Wait until the request has been sent (proving the pending future is registered).
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> verify(kafkaTemplate).send(any(org.springframework.messaging.Message.class)));

            // Simulate the responder replying on the reply topic.
            client.completeReply("corr-1", "the-reply");

            assertEquals("the-reply", result.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestIsSentWithCorrelationAndReplyTopicHeaders() {
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(Object.class), any()))
                .thenReturn("reply-consumer");
        AtomicReference<org.springframework.messaging.Message<?>> sent = new AtomicReference<>();
        when(kafkaTemplate.send(any(org.springframework.messaging.Message.class))).thenAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
        KafkaRequestReplyClient client = clientWithFixedCorrelation("corr-xyz");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> client.sendAndReceive("orders", "request", String.class, Duration.ofSeconds(2)));
            await().atMost(Duration.ofSeconds(5)).until(() -> sent.get() != null);

            Map<String, Object> headers = sent.get().getHeaders();
            assertEquals("corr-xyz", headers.get(KafkaRequestReplyClient.CORRELATION_ID_HEADER));
            assertEquals("orders.reply", headers.get(KafkaRequestReplyClient.REPLY_TOPIC_HEADER));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sendAndReceiveTimesOutWhenNoReply() {
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(Object.class), any()))
                .thenReturn("reply-consumer");
        KafkaRequestReplyClient client = clientWithFixedCorrelation("corr-timeout");

        MessagingException ex = assertThrows(MessagingException.class,
                () -> client.sendAndReceive("orders", "request", String.class, Duration.ofMillis(50)));
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void sendAndReceiveWithoutListenerThrows() {
        KafkaRequestReplyClient client = new KafkaRequestReplyClient(kafkaTemplate, null, properties,
                new ObjectMapper(), () -> "corr");

        assertThrows(MessagingException.class,
                () -> client.sendAndReceive("orders", "request", String.class, Duration.ofSeconds(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replyHandlerConvertsPojoReplyViaObjectMapper() throws Exception {
        AtomicReference<MessageListener.BiFunction<Object, Map<String, Object>, Boolean>> handler =
                new AtomicReference<>();
        when(messageListener.subscribeWithHeadersAndAck(anyString(), anyString(), eq(Object.class), any()))
                .thenAnswer(inv -> {
                    handler.set(inv.getArgument(3));
                    return "reply-consumer";
                });
        KafkaRequestReplyClient client = clientWithFixedCorrelation("corr-pojo");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Point> result = CompletableFuture.supplyAsync(
                    () -> client.sendAndReceive("orders", "request", Point.class, Duration.ofSeconds(5)), executor);

            await().atMost(Duration.ofSeconds(5)).until(() -> handler.get() != null);
            // Deliver a Map payload with the correlation id header; convert() should map it to Point.
            handler.get().apply(Map.of("x", 3, "y", 4),
                    Map.of(KafkaRequestReplyClient.CORRELATION_ID_HEADER, "corr-pojo"));

            Point reply = result.get(5, TimeUnit.SECONDS);
            assertInstanceOf(Point.class, reply);
            assertEquals(3, reply.x);
            assertEquals(4, reply.y);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completeReplyWithUnknownCorrelationIsIgnored() {
        KafkaRequestReplyClient client = clientWithFixedCorrelation("corr");
        // Must not throw.
        client.completeReply("nobody-waiting", "reply");
        client.completeReply(null, "reply");
    }

    public static class Point {
        public int x;
        public int y;
    }
}
