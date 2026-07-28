package com.adhar.kit.persistence.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * {@link OutboxRelay} that publishes each due {@link OutboxEvent} onto a Kafka topic.
 *
 * <p>The Kafka message uses the event's {@link OutboxEvent#getAggregateId() aggregate id} as the
 * record key (so all events for the same aggregate land on the same partition and preserve order),
 * the {@link OutboxEvent#getPayload() payload} as the record value, and carries the remaining
 * outbox metadata (event id, aggregate type, aggregate id, event type) as Kafka record headers so
 * downstream consumers can route/deserialize without re-parsing the payload.</p>
 *
 * <p>Delivery is synchronous: {@link #relay(OutboxEvent)} blocks on the send acknowledgement and
 * throws if it fails, so that {@link OutboxPublisher} applies its retry / backoff / dead-letter
 * policy exactly as it does for any other relay.</p>
 *
 * <p>This class references {@code spring-kafka} types and is only instantiated by
 * {@code PersistenceAutoConfiguration} when {@link KafkaTemplate} is on the classpath, so the
 * module continues to work when {@code spring-kafka} is absent.</p>
 *
 * @author Adhar Platform Team
 * @since 1.4.0
 */
public class KafkaOutboxRelay implements OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxRelay.class);

    /** Header carrying the outbox row's UUID. */
    public static final String HEADER_EVENT_ID = "outbox-event-id";
    /** Header carrying the aggregate type. */
    public static final String HEADER_AGGREGATE_TYPE = "outbox-aggregate-type";
    /** Header carrying the aggregate id. */
    public static final String HEADER_AGGREGATE_ID = "outbox-aggregate-id";
    /** Header carrying the event type. */
    public static final String HEADER_EVENT_TYPE = "outbox-event-type";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    /**
     * Creates a new relay.
     *
     * @param kafkaTemplate the Kafka template used to publish events (must not be {@code null})
     * @param topic         the destination topic (must not be blank)
     */
    public KafkaOutboxRelay(KafkaTemplate<String, String> kafkaTemplate, String topic) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        this.topic = topic;
    }

    @Override
    public void relay(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String key = event.getAggregateId();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, null, key, event.getPayload());
        addHeader(record, HEADER_EVENT_ID, event.getId() != null ? event.getId().toString() : null);
        addHeader(record, HEADER_AGGREGATE_TYPE, event.getAggregateType());
        addHeader(record, HEADER_AGGREGATE_ID, event.getAggregateId());
        addHeader(record, HEADER_EVENT_TYPE, event.getEventType());

        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
            future.get();
            log.debug("Relayed outbox event id={}, type={} to Kafka topic {}",
                    event.getId(), event.getEventType(), topic);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OutboxRelayException(
                    "Interrupted while publishing outbox event " + event.getId() + " to Kafka", ex);
        } catch (ExecutionException ex) {
            throw new OutboxRelayException(
                    "Failed to publish outbox event " + event.getId() + " to Kafka topic " + topic,
                    ex.getCause() != null ? ex.getCause() : ex);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Thrown when publishing to Kafka fails; propagated to {@link OutboxPublisher} which then
     * schedules a retry.
     */
    public static class OutboxRelayException extends RuntimeException {
        public OutboxRelayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
