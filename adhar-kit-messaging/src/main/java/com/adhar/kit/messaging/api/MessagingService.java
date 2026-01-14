package com.adhar.kit.messaging.api;

import java.util.function.Consumer;

/**
 * Framework-agnostic Messaging Service API.
 *
 * <p>This interface provides a unified messaging abstraction for event-driven
 * architectures across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Message Brokers:</b></p>
 * <ul>
 *   <li>Apache Kafka</li>
 *   <li>RabbitMQ</li>
 *   <li>Amazon SQS/SNS</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * MessagingService messaging = MessagingFacade.getInstance();
 *
 * // Publish event
 * OrderCreatedEvent event = new OrderCreatedEvent(orderId);
 * messaging.publish("order-events", event);
 *
 * // Subscribe to events
 * messaging.subscribe("order-events", OrderCreatedEvent.class,
 *     event -> processOrder(event));
 *
 * // Publish to specific partition (Kafka)
 * messaging.publish("order-events", orderId, event);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.messaging.MessagingFacade
 */
public interface MessagingService {

    /**
     * Publishes a message to a topic/queue.
     *
     * @param topic the topic or queue name
     * @param message the message to publish
     * @param <T> the message type
     */
    <T> void publish(String topic, T message);

    /**
     * Publishes a message with a specific key (for partitioning).
     *
     * <p>In Kafka, the key determines which partition the message goes to.</p>
     *
     * @param topic the topic name
     * @param key the message key (for partitioning)
     * @param message the message to publish
     * @param <T> the message type
     */
    <T> void publish(String topic, String key, T message);

    /**
     * Subscribes to a topic/queue and processes messages.
     *
     * <p>The handler will be called for each message received.</p>
     *
     * @param topic the topic or queue name
     * @param messageType the expected message type
     * @param handler the message handler function
     * @param <T> the message type
     * @return subscription ID (can be used to unsubscribe)
     */
    <T> String subscribe(String topic, Class<T> messageType, Consumer<T> handler);

    /**
     * Subscribes to a topic/queue with a consumer group.
     *
     * <p>Multiple subscribers in the same group will load-balance messages.</p>
     *
     * @param topic the topic or queue name
     * @param consumerGroup the consumer group name
     * @param messageType the expected message type
     * @param handler the message handler function
     * @param <T> the message type
     * @return subscription ID
     */
    <T> String subscribe(String topic, String consumerGroup,
                         Class<T> messageType, Consumer<T> handler);

    /**
     * Unsubscribes from a topic/queue.
     *
     * @param subscriptionId the subscription ID returned from subscribe()
     */
    void unsubscribe(String subscriptionId);

    /**
     * Sends a message and waits for reply (request-reply pattern).
     *
     * @param topic the topic name
     * @param request the request message
     * @param replyType the expected reply type
     * @param <REQ> the request type
     * @param <REP> the reply type
     * @return the reply message
     */
    <REQ, REP> REP sendAndReceive(String topic, REQ request, Class<REP> replyType);

    /**
     * Checks if the messaging service is connected and healthy.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Gets the number of active subscriptions.
     *
     * @return number of subscriptions
     */
    int getSubscriptionCount();
}

