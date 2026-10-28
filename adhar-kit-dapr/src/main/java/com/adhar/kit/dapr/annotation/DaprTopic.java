package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Subscribes a bean method to a Dapr pub/sub topic, like {@link DaprSubscribe} with the
 * attribute names of the Dapr SDK's {@code @Topic}.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Component
 * public class OrderEventHandler {
 *
 *     @DaprTopic(pubsubName = "pubsub", name = "orders.created")
 *     public void handleOrderCreated(DaprCloudEvent<OrderCreatedEvent> event) {
 *         OrderCreatedEvent data = event.data();
 *         log.info("Processing order: {}", data.getOrderId());
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
     * The pub/sub component name; empty for the service default.
     */
    String pubsubName() default "";

    /**
     * The topic name to subscribe to.
     */
    String name();

    /**
     * Optional dead letter topic for failed messages.
     */
    String deadLetterTopic() default "";

    /**
     * Metadata for subscription, as {@code key=value} pairs.
     */
    String[] metadata() default {};
}
