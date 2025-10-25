package com.adhar.adharkit.messaging.core;

import java.util.Map;

/**
 * Interface for handling messages in a type-safe way.
 * <p>
 * This interface provides a method for handling messages of a specific type.
 * It is used by the message listener to delegate the handling of messages to
 * appropriate handlers based on the message type.
 * <p>
 * Implementations of this interface should handle the processing of messages
 * and any necessary error handling.
 *
 * @param <T> the type of the message payload
 */
@FunctionalInterface
public interface MessageHandler<T> {

    /**
     * Handles a message.
     *
     * @param payload the message payload
     * @param headers the message headers
     * @param context additional context information
     * @return true if the message was handled successfully, false otherwise
     */
    boolean handle(T payload, Map<String, Object> headers, MessageContext context);

    /**
     * Context information for message handling.
     */
    interface MessageContext {
        /**
         * Gets the source of the message (topic, queue, etc.).
         *
         * @return the message source
         */
        String getSource();

        /**
         * Gets the destination of the message (topic, exchange, etc.).
         *
         * @return the message destination
         */
        String getDestination();

        /**
         * Gets the routing key or partition key for the message.
         *
         * @return the routing key
         */
        String getRoutingKey();

        /**
         * Gets the timestamp when the message was received.
         *
         * @return the timestamp in milliseconds
         */
        long getTimestamp();

        /**
         * Gets the consumer ID that received the message.
         *
         * @return the consumer ID
         */
        String getConsumerId();

        /**
         * Gets the consumer group that received the message.
         *
         * @return the consumer group
         */
        String getConsumerGroup();

        /**
         * Gets the message ID.
         *
         * @return the message ID
         */
        String getMessageId();

        /**
         * Gets a context attribute.
         *
         * @param key the attribute key
         * @return the attribute value, or null if not found
         */
        Object getAttribute(String key);

        /**
         * Sets a context attribute.
         *
         * @param key   the attribute key
         * @param value the attribute value
         */
        void setAttribute(String key, Object value);

        /**
         * Acknowledges the message.
         *
         * @return true if the message was acknowledged successfully, false otherwise
         */
        boolean acknowledge();

        /**
         * Rejects the message.
         *
         * @param requeue whether to requeue the message
         * @return true if the message was rejected successfully, false otherwise
         */
        boolean reject(boolean requeue);
    }
}