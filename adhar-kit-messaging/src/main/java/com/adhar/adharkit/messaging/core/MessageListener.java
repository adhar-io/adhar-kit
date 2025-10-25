package com.adhar.adharkit.messaging.core;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Common interface for consuming messages from a messaging system.
 * <p>
 * This interface provides a unified API for consuming messages from different messaging systems
 * like Kafka and RabbitMQ. It abstracts away the underlying implementation details and provides
 * a simple, consistent way to receive messages.
 * <p>
 * The interface supports both simple message consumption and more advanced scenarios like
 * manual acknowledgment and error handling.
 */
public interface MessageListener {

    /**
     * Registers a consumer for messages from the specified source.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribe(String source, Class<T> type, Consumer<T> consumer);

    /**
     * Registers a consumer for messages from the specified source with a specific group.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param group    the consumer group
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribe(String source, String group, Class<T> type, Consumer<T> consumer);

    /**
     * Registers a consumer for messages from the specified source with access to message headers.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages and headers
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithHeaders(String source, Class<T> type, BiConsumer<T, Map<String, Object>> consumer);

    /**
     * Registers a consumer for messages from the specified source with a specific group and access to message headers.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param group    the consumer group
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages and headers
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithHeaders(String source, String group, Class<T> type, BiConsumer<T, Map<String, Object>> consumer);

    /**
     * Registers a consumer for messages from the specified source with manual acknowledgment.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages and acknowledge
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithAck(String source, Class<T> type, Function<T, Boolean> consumer);

    /**
     * Registers a consumer for messages from the specified source with a specific group and manual acknowledgment.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param group    the consumer group
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages and acknowledge
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithAck(String source, String group, Class<T> type, Function<T, Boolean> consumer);

    /**
     * Registers a consumer for messages from the specified source with manual acknowledgment and access to message headers.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages, headers, and acknowledge
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithHeadersAndAck(String source, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> consumer);

    /**
     * Registers a consumer for messages from the specified source with a specific group, manual acknowledgment, and access to message headers.
     *
     * @param source   the source to consume from (topic, queue, etc.)
     * @param group    the consumer group
     * @param type     the type of the payload
     * @param consumer the consumer function to process the messages, headers, and acknowledge
     * @param <T>      the type of the payload
     * @return a unique identifier for the registered consumer
     */
    <T> String subscribeWithHeadersAndAck(String source, String group, Class<T> type, BiFunction<T, Map<String, Object>, Boolean> consumer);

    /**
     * Unsubscribes a previously registered consumer.
     *
     * @param consumerId the unique identifier of the consumer to unsubscribe
     * @return true if the consumer was successfully unsubscribed, false otherwise
     */
    boolean unsubscribe(String consumerId);

    /**
     * Pauses a previously registered consumer.
     *
     * @param consumerId the unique identifier of the consumer to pause
     * @return true if the consumer was successfully paused, false otherwise
     */
    boolean pause(String consumerId);

    /**
     * Resumes a previously paused consumer.
     *
     * @param consumerId the unique identifier of the consumer to resume
     * @return true if the consumer was successfully resumed, false otherwise
     */
    boolean resume(String consumerId);

    /**
     * Functional interface for consuming a message with headers.
     *
     * @param <T> the type of the payload
     * @param <H> the type of the headers
     */
    @FunctionalInterface
    interface BiConsumer<T, H> {
        /**
         * Consumes a message with headers.
         *
         * @param payload the message payload
         * @param headers the message headers
         */
        void accept(T payload, H headers);
    }

    /**
     * Functional interface for consuming a message with headers and acknowledging.
     *
     * @param <T> the type of the payload
     * @param <H> the type of the headers
     * @param <R> the return type (Boolean for acknowledgment)
     */
    @FunctionalInterface
    interface BiFunction<T, H, R> {
        /**
         * Consumes a message with headers and acknowledges.
         *
         * @param payload the message payload
         * @param headers the message headers
         * @return true to acknowledge the message, false to reject it
         */
        R apply(T payload, H headers);
    }
}