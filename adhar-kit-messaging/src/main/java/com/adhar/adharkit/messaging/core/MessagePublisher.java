package com.adhar.adharkit.messaging.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Common interface for publishing messages to a messaging system.
 * <p>
 * This interface provides a unified API for publishing messages to different messaging systems
 * like Kafka and RabbitMQ. It abstracts away the underlying implementation details and provides
 * a simple, consistent way to send messages.
 * <p>
 * The interface supports both synchronous and asynchronous publishing, as well as publishing
 * with headers and routing information.
 */
public interface MessagePublisher {

    /**
     * Publishes a message to the default destination.
     *
     * @param payload the message payload
     * @param <T>     the type of the payload
     * @return true if the message was published successfully, false otherwise
     */
    <T> boolean publish(T payload);

    /**
     * Publishes a message to the specified destination.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return true if the message was published successfully, false otherwise
     */
    <T> boolean publish(String destination, T payload);

    /**
     * Publishes a message to the specified destination with routing information.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param routingKey  the routing key or partition key
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return true if the message was published successfully, false otherwise
     */
    <T> boolean publish(String destination, String routingKey, T payload);

    /**
     * Publishes a message to the specified destination with headers.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param headers     the message headers
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return true if the message was published successfully, false otherwise
     */
    <T> boolean publish(String destination, Map<String, Object> headers, T payload);

    /**
     * Publishes a message to the specified destination with routing information and headers.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param routingKey  the routing key or partition key
     * @param headers     the message headers
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return true if the message was published successfully, false otherwise
     */
    <T> boolean publish(String destination, String routingKey, Map<String, Object> headers, T payload);

    /**
     * Asynchronously publishes a message to the default destination.
     *
     * @param payload the message payload
     * @param <T>     the type of the payload
     * @return a CompletableFuture that will be completed when the message is published
     */
    <T> CompletableFuture<Boolean> publishAsync(T payload);

    /**
     * Asynchronously publishes a message to the specified destination.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return a CompletableFuture that will be completed when the message is published
     */
    <T> CompletableFuture<Boolean> publishAsync(String destination, T payload);

    /**
     * Asynchronously publishes a message to the specified destination with routing information.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param routingKey  the routing key or partition key
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return a CompletableFuture that will be completed when the message is published
     */
    <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, T payload);

    /**
     * Asynchronously publishes a message to the specified destination with headers.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param headers     the message headers
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return a CompletableFuture that will be completed when the message is published
     */
    <T> CompletableFuture<Boolean> publishAsync(String destination, Map<String, Object> headers, T payload);

    /**
     * Asynchronously publishes a message to the specified destination with routing information and headers.
     *
     * @param destination the destination to publish to (topic, exchange, etc.)
     * @param routingKey  the routing key or partition key
     * @param headers     the message headers
     * @param payload     the message payload
     * @param <T>         the type of the payload
     * @return a CompletableFuture that will be completed when the message is published
     */
    <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, Map<String, Object> headers, T payload);
}