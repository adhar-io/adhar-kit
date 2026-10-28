package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Receives the events of a Dapr input binding (a queue, a cron, a webhook source, ...). The kit
 * answers the sidecar's startup {@code OPTIONS /<name>} probe and routes its
 * {@code POST /<name>} deliveries to the method.
 *
 * <p>The method takes the event data (as {@code byte[]}, {@code String} or a JSON-mapped type)
 * and optionally a second {@code Map<String, String>} parameter with the binding metadata. A
 * returned value is sent back to the binding as JSON; an exception answers 500 so bindings that
 * support it redeliver.</p>
 *
 * <pre>{@code
 * @DaprInputBinding(name = "orders-queue")
 * public void onQueueMessage(OrderMessage message, Map<String, String> metadata) { ... }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprInputBinding {

    /**
     * The binding component name.
     */
    String name();

    /**
     * The route Dapr delivers to when the component sets a {@code route} metadata; empty for
     * {@code /<name>}.
     */
    String route() default "";
}
