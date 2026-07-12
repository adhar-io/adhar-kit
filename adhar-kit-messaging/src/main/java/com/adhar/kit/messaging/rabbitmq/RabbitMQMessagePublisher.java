package com.adhar.kit.messaging.rabbitmq;

import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link MessagePublisher} for RabbitMQ.
 * <p>
 * This class uses Spring AMQP's {@link RabbitTemplate} to publish messages to RabbitMQ exchanges.
 * It supports both synchronous and asynchronous publishing, as well as publishing with headers
 * and routing information.
 */
public class RabbitMQMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AdharMessagingProperties properties;

    /**
     * Creates a new RabbitMQMessagePublisher.
     *
     * @param rabbitTemplate the RabbitTemplate to use for publishing messages
     * @param properties     the messaging properties
     */
    public RabbitMQMessagePublisher(RabbitTemplate rabbitTemplate, AdharMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public <T> boolean publish(T payload) {
        return publish(properties.getRabbitmq().getDefaultExchange(), payload);
    }

    @Override
    public <T> boolean publish(String destination, T payload) {
        return publishWithStringRouting(destination, properties.getRabbitmq().getDefaultRoutingKey(), payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, T payload) {
        return publishWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> boolean publish(String destination, Map<String, Object> headers, T payload) {
        return publishWithMapHeaders(destination, properties.getRabbitmq().getDefaultRoutingKey(), headers, payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, Map<String, Object> headers, T payload) {
        try {
            String exchange = StringUtils.hasText(destination) ? destination : properties.getRabbitmq().getDefaultExchange();
            String routing = StringUtils.hasText(routingKey) ? routingKey : properties.getRabbitmq().getDefaultRoutingKey();

            Message message = createMessage(headers, payload);
            String correlationId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(correlationId);

            rabbitTemplate.send(exchange, routing, message, correlationData);
            log.debug("Published message to RabbitMQ exchange {} with routing key {}: {}", exchange, routing, correlationId);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to RabbitMQ", e);
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
    private <T> boolean publishWithMapHeaders(String destination, String routingKey, Map<String, Object> headers, T payload) {
        return publish(destination, routingKey, headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(T payload) {
        return publishAsync(properties.getRabbitmq().getDefaultExchange(), payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, T payload) {
        return publishAsyncWithStringRouting(destination, properties.getRabbitmq().getDefaultRoutingKey(), payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, T payload) {
        return publishAsyncWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, Map<String, Object> headers, T payload) {
        return publishAsyncWithMapHeaders(destination, properties.getRabbitmq().getDefaultRoutingKey(), headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, Map<String, Object> headers, T payload) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        try {
            String exchange = StringUtils.hasText(destination) ? destination : properties.getRabbitmq().getDefaultExchange();
            String routing = StringUtils.hasText(routingKey) ? routingKey : properties.getRabbitmq().getDefaultRoutingKey();

            Message message = createMessage(headers, payload);
            String correlationId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(correlationId);

            if (properties.getRabbitmq().getPublisher().isConfirms()) {
                // Set up a callback for publisher confirms
                rabbitTemplate.setConfirmCallback((correlationData1, ack, cause) -> {
                    if (correlationData1 != null && correlationData1.getId().equals(correlationId)) {
                        if (ack) {
                            log.debug("Message confirmed by RabbitMQ: {}", correlationId);
                            result.complete(true);
                        } else {
                            log.error("Message rejected by RabbitMQ: {} - Cause: {}", correlationId, cause);
                            result.complete(false);
                        }
                    }
                });
            }

            rabbitTemplate.send(exchange, routing, message, correlationData);
            log.debug("Published message to RabbitMQ exchange {} with routing key {}: {}", exchange, routing, correlationId);

            if (!properties.getRabbitmq().getPublisher().isConfirms()) {
                // Publisher confirms are not enabled: the send above completed without
                // throwing, so the publish is successful. Complete only after the send
                // so a send failure is reported as false (via the catch block below)
                // rather than being masked by an early completion.
                result.complete(true);
            }
        } catch (Exception e) {
            log.error("Failed to publish message to RabbitMQ", e);
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
    private <T> CompletableFuture<Boolean> publishAsyncWithMapHeaders(String destination, String routingKey, Map<String, Object> headers, T payload) {
        return publishAsync(destination, routingKey, headers, payload);
    }

    /**
     * Creates an AMQP Message from the payload and headers.
     *
     * @param headers the message headers
     * @param payload the message payload
     * @param <T>     the type of the payload
     * @return an AMQP Message
     */
    private <T> Message createMessage(Map<String, Object> headers, T payload) {
        MessageProperties properties = new MessageProperties();

        // Set content type based on serialization format
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        // Add headers
        if (headers != null && !headers.isEmpty()) {
            headers.forEach((key, value) -> {
                if (value != null) {
                    properties.setHeader(key, value);
                }
            });
        }

        // Add trace headers if tracing is enabled
        if (this.properties.getCommon().isTraceEnabled()) {
            String messageId = UUID.randomUUID().toString();
            properties.setMessageId(messageId);
            properties.setTimestamp(new java.util.Date());
        }

        // Convert payload to byte array
        byte[] body;
        if (payload instanceof byte[]) {
            body = (byte[]) payload;
        } else if (payload instanceof String) {
            body = ((String) payload).getBytes();
        } else {
            // Use the message converter from RabbitTemplate
            body = rabbitTemplate.getMessageConverter().toMessage(payload, properties).getBody();
        }

        return MessageBuilder.withBody(body)
                .andProperties(properties)
                .build();
    }
}
