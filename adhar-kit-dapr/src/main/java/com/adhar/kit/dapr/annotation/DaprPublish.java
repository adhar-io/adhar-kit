package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to publish events to Dapr pub/sub.
 *
 * <p>The event is published after the method returns, outside any transaction it ran in. When the
 * event must not be lost if the process dies right after the commit, write it in the same state
 * transaction instead (Dapr's transactional outbox, see
 * {@code DaprFacade#executeStateTransaction}).</p>
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
     * Pub/sub component name; empty for the service default ({@code adhar.dapr.pubsub}).
     */
    String pubsubName() default "";

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

