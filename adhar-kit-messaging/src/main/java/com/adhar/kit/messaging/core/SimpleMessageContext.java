package com.adhar.kit.messaging.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, broker-agnostic implementation of {@link MessageHandler.MessageContext}.
 * <p>
 * Broker-specific listeners (Kafka, RabbitMQ) already provide their own context
 * implementations that talk to the underlying acknowledgment mechanism. This class is
 * for callers - such as {@link com.adhar.kit.messaging.MessagingFacade} - that build a
 * {@link MessageHandler} pipeline (retry, deduplication, dead-letter) on top of the
 * simpler {@code Consumer}-based subscription APIs, where acknowledgment is handled
 * by the surrounding broker adapter rather than by the context itself.
 * <p>
 * {@link #acknowledge()} and {@link #reject(boolean)} both simply return {@code true};
 * callers that need real broker acknowledgment should rely on the boolean return value
 * of {@link MessageHandler#handle} instead.
 */
public class SimpleMessageContext implements MessageHandler.MessageContext {

    private final String source;
    private final String destination;
    private final String routingKey;
    private final long timestamp;
    private final String consumerId;
    private final String consumerGroup;
    private final String messageId;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public SimpleMessageContext(String source, String destination, String routingKey,
                                 String consumerId, String consumerGroup, String messageId) {
        this.source = source;
        this.destination = destination;
        this.routingKey = routingKey;
        this.timestamp = System.currentTimeMillis();
        this.consumerId = consumerId;
        this.consumerGroup = consumerGroup;
        this.messageId = messageId;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getDestination() {
        return destination;
    }

    @Override
    public String getRoutingKey() {
        return routingKey;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getConsumerId() {
        return consumerId;
    }

    @Override
    public String getConsumerGroup() {
        return consumerGroup;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    public boolean acknowledge() {
        return true;
    }

    @Override
    public boolean reject(boolean requeue) {
        return true;
    }
}
