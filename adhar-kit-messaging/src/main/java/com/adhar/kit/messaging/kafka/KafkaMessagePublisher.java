package com.adhar.kit.messaging.kafka;

import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Implementation of {@link MessagePublisher} for Kafka.
 * <p>
 * This class uses Spring Kafka's {@link KafkaTemplate} to publish messages to Kafka topics.
 * It supports both synchronous and asynchronous publishing, as well as publishing with headers
 * and routing information.
 */
public class KafkaMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagePublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdharMessagingProperties properties;

    /**
     * Creates a new KafkaMessagePublisher.
     *
     * @param kafkaTemplate the KafkaTemplate to use for publishing messages
     * @param properties    the messaging properties
     */
    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate, AdharMessagingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public <T> boolean publish(T payload) {
        return publish(properties.getKafka().getDefaultTopic(), payload);
    }

    @Override
    public <T> boolean publish(String destination, T payload) {
        return publishWithStringRouting(destination, null, payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, T payload) {
        return publishWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> boolean publish(String destination, Map<String, Object> headers, T payload) {
        return publishWithMapHeaders(destination, headers, payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, Map<String, Object> headers, T payload) {
        try {
            String topic = StringUtils.hasText(destination) ? destination : properties.getKafka().getDefaultTopic();

            org.springframework.messaging.Message<T> message = createMessage(topic, routingKey, headers, payload);

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
            SendResult<String, Object> result = future.get();

            log.debug("Published message to Kafka topic {}: {}", topic, result);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing message to Kafka", e);
            return false;
        } catch (ExecutionException e) {
            log.error("Failed to publish message to Kafka", e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error while publishing message to Kafka", e);
            return false;
        }
    }

    /**
     * Helper method to disambiguate method calls with String routing key.
     */
    private <T> boolean publishWithStringRouting(String destination, String routingKey, T payload) {
        return publish(destination, routingKey, Collections.emptyMap(), payload);
    }

    /**
     * Helper method to disambiguate method calls with Map headers.
     */
    private <T> boolean publishWithMapHeaders(String destination, Map<String, Object> headers, T payload) {
        return publish(destination, null, headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(T payload) {
        return publishAsync(properties.getKafka().getDefaultTopic(), payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, T payload) {
        return publishAsyncWithStringRouting(destination, null, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, T payload) {
        return publishAsyncWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, Map<String, Object> headers, T payload) {
        return publishAsyncWithMapHeaders(destination, headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, Map<String, Object> headers, T payload) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        try {
            String topic = StringUtils.hasText(destination) ? destination : properties.getKafka().getDefaultTopic();

            org.springframework.messaging.Message<T> message = createMessage(topic, routingKey, headers, payload);

            kafkaTemplate.send(message)
                .whenComplete((sendResult, exception) -> {
                    if (exception != null) {
                        log.error("Failed to publish message to Kafka topic {}", topic, exception);
                        result.complete(false);
                    } else {
                        log.debug("Published message to Kafka topic {}: {}", topic, sendResult);
                        result.complete(true);
                    }
                });
        } catch (Exception e) {
            log.error("Unexpected error while publishing message to Kafka", e);
            result.complete(false);
        }

        return result;
    }

    /**
     * Helper method to disambiguate method calls with String routing key.
     */
    private <T> CompletableFuture<Boolean> publishAsyncWithStringRouting(String destination, String routingKey, T payload) {
        return publishAsync(destination, routingKey, Collections.emptyMap(), payload);
    }

    /**
     * Helper method to disambiguate method calls with Map headers.
     */
    private <T> CompletableFuture<Boolean> publishAsyncWithMapHeaders(String destination, Map<String, Object> headers, T payload) {
        return publishAsync(destination, null, headers, payload);
    }

    /**
     * Creates a Spring Messaging Message from the payload, headers, and routing information.
     *
     * @param topic      the Kafka topic
     * @param routingKey the routing key (partition key)
     * @param headers    the message headers
     * @param payload    the message payload
     * @param <T>        the type of the payload
     * @return a Spring Messaging Message
     */
    private <T> org.springframework.messaging.Message<T> createMessage(String topic, String routingKey, Map<String, Object> headers, T payload) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic);

        if (StringUtils.hasText(routingKey)) {
            builder.setHeader(KafkaHeaders.KEY, routingKey);
        }

        if (headers != null && !headers.isEmpty()) {
            headers.forEach(builder::setHeader);
        }

        // Add trace headers if tracing is enabled
        if (properties.getCommon().isTraceEnabled()) {
            String messageId = UUID.randomUUID().toString();
            builder.setHeader("messageId", messageId);
            // Note: "timestamp" is a reserved, read-only header in Spring Messaging
            // (MessageHeaders.TIMESTAMP), so a custom header name is used instead.
            builder.setHeader("messageTimestamp", System.currentTimeMillis());
        }

        return builder.build();
    }
}
