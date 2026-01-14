package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to subscribe to Dapr pub/sub topic.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprSubscribe(pubsubName = "pubsub", topic = "order-created")
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     // Process event
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprSubscribe {

    /**
     * Pub/sub component name.
     */
    String pubsubName() default "pubsub";

    /**
     * Topic name.
     */
    String topic();

    /**
     * Dead letter topic (optional).
     */
    String deadLetterTopic() default "";

    /**
     * Route path for subscription.
     */
    String route() default "";
}

