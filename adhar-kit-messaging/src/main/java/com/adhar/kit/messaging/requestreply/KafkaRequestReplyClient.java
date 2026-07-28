package com.adhar.kit.messaging.requestreply;

import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Kafka {@link RequestReplyClient} implementing request-reply with an explicit reply topic
 * and correlation id.
 * <p>
 * For each request a correlation id is generated and a {@link CompletableFuture} is parked
 * in a pending-replies map. The request is sent with {@link #CORRELATION_ID_HEADER} and
 * {@link #REPLY_TOPIC_HEADER} headers; a single subscription per reply topic (established
 * lazily via the shared {@link MessageListener}) completes the matching future when a reply
 * arrives. This keeps the implementation broker-agnostic and unit-testable without a running
 * Kafka cluster (see {@link #completeReply(String, Object)}).
 */
public class KafkaRequestReplyClient implements RequestReplyClient {

    private static final Logger log = LoggerFactory.getLogger(KafkaRequestReplyClient.class);

    /** Header carrying the request's correlation id, echoed back on the reply. */
    public static final String CORRELATION_ID_HEADER = "adhar_correlationId";

    /** Header telling the responder which topic to publish the reply to. */
    public static final String REPLY_TOPIC_HEADER = "adhar_replyTopic";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageListener messageListener;
    private final AdharMessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final Supplier<String> correlationIdGenerator;

    /** Unique consumer group so this client instance receives all of its own replies. */
    private final String replyGroup = "adhar-reply-" + UUID.randomUUID();

    private final Map<String, CompletableFuture<Object>> pendingReplies = new ConcurrentHashMap<>();
    private final Map<String, String> replyConsumerIds = new ConcurrentHashMap<>();

    public KafkaRequestReplyClient(KafkaTemplate<String, Object> kafkaTemplate, MessageListener messageListener,
                                   AdharMessagingProperties properties, ObjectMapper objectMapper) {
        this(kafkaTemplate, messageListener, properties, objectMapper, () -> UUID.randomUUID().toString());
    }

    /**
     * Constructor exposing the correlation-id generator seam (chiefly for tests, which can
     * pin a deterministic id).
     */
    public KafkaRequestReplyClient(KafkaTemplate<String, Object> kafkaTemplate, MessageListener messageListener,
                            AdharMessagingProperties properties, ObjectMapper objectMapper,
                            Supplier<String> correlationIdGenerator) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.messageListener = messageListener;
        this.properties = properties != null ? properties : new AdharMessagingProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.correlationIdGenerator = correlationIdGenerator;
    }

    @Override
    public <REQ, REP> REP sendAndReceive(String topic, REQ request, Class<REP> replyType, Duration timeout) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(request, "request");
        String replyTopic = topic + properties.getCommon().getReply().getTopicSuffix();
        ensureReplySubscription(replyTopic);

        String correlationId = correlationIdGenerator.get();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingReplies.put(correlationId, future);

        try {
            org.springframework.messaging.Message<Object> message = MessageBuilder.withPayload((Object) request)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(CORRELATION_ID_HEADER, correlationId)
                    .setHeader(REPLY_TOPIC_HEADER, replyTopic)
                    .build();
            kafkaTemplate.send(message);
            log.debug("Sent request to topic {} (correlationId={}, replyTopic={})", topic, correlationId, replyTopic);

            Object reply = future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            return convert(reply, replyType);
        } catch (TimeoutException e) {
            throw new MessagingException("Request-reply timed out after " + timeout.toMillis()
                    + "ms waiting for a reply on " + replyTopic, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for a reply on " + replyTopic, e);
        } catch (ExecutionException e) {
            throw new MessagingException("Request-reply failed for topic " + topic,
                    e.getCause() != null ? e.getCause() : e);
        } finally {
            pendingReplies.remove(correlationId);
        }
    }

    private void ensureReplySubscription(String replyTopic) {
        replyConsumerIds.computeIfAbsent(replyTopic, rt -> {
            if (messageListener == null) {
                throw new MessagingException("Kafka request-reply requires a MessageListener to receive replies on "
                        + rt + ", but none is configured");
            }
            log.debug("Establishing reply subscription on {}", rt);
            return messageListener.subscribeWithHeadersAndAck(rt, replyGroup, Object.class,
                    (payload, headers) -> {
                        completeReply(headerAsString(headers.get(CORRELATION_ID_HEADER)), payload);
                        return true;
                    });
        });
    }

    /**
     * Completes the parked future for {@code correlationId} with {@code payload}. Invoked by
     * the reply subscription (and directly by tests). Unknown/late correlation ids are
     * ignored.
     *
     * @param correlationId the correlation id echoed on the reply
     * @param payload       the reply payload
     */
    public void completeReply(String correlationId, Object payload) {
        if (correlationId == null) {
            log.warn("Received a reply without a correlation id - ignoring");
            return;
        }
        CompletableFuture<Object> future = pendingReplies.get(correlationId);
        if (future != null) {
            future.complete(payload);
        } else {
            log.debug("No pending request for correlationId {} - reply ignored (late or duplicate)", correlationId);
        }
    }

    private <REP> REP convert(Object payload, Class<REP> replyType) {
        if (payload == null) {
            return null;
        }
        if (replyType.isInstance(payload)) {
            return replyType.cast(payload);
        }
        return objectMapper.convertValue(payload, replyType);
    }

    private static String headerAsString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }
}
