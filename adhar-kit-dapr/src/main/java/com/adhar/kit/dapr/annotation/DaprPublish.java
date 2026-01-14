package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to publish events to Dapr pub/sub.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprPublish(pubsubName = "pubsub", topic = "order-created")
 * public void publishOrderCreated(OrderCreatedEvent event) {
 *     // Event automatically published after method execution
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprPublish {

    /**
     * Pub/sub component name.
     */
    String pubsubName() default "pubsub";

    /**
     * Topic name.
     */
    String topic();

    /**
     * Publish return value (true) or specific parameter (false).
     */
    boolean publishReturnValue() default true;

    /**
     * Parameter index to publish (if publishReturnValue = false).
     */
    int parameterIndex() default 0;
}

