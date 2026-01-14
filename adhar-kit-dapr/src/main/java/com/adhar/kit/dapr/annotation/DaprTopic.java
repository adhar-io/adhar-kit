package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to subscribe to a Dapr pub/sub topic.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Component
 * public class OrderEventHandler {
 *
 *     @DaprTopic(pubsubName = "pubsub", name = "orders.created")
 *     public void handleOrderCreated(CloudEvent<OrderCreatedEvent> event) {
 *         OrderCreatedEvent data = event.getData();
 *         log.info("Processing order: {}", data.getOrderId());
 *     }
 *
 *     @DaprTopic(pubsubName = "pubsub", name = "orders.cancelled")
 *     public void handleOrderCancelled(CloudEvent<OrderCancelledEvent> event) {
 *         // Handle cancellation
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprTopic {

    /**
     * The pub/sub component name.
     */
    String pubsubName();

    /**
     * The topic name to subscribe to.
     */
    String name();

    /**
     * Optional dead letter topic for failed messages.
     */
    String deadLetterTopic() default "";

    /**
     * Metadata for subscription.
     */
    String[] metadata() default {};
}

