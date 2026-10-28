package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Subscribes a bean method to a Dapr pub/sub topic. The kit advertises it on
 * {@code GET /dapr/subscribe} and routes the sidecar's deliveries to the method (default route
 * {@code /dapr/events/<pubsub>/<topic>}); without a web server it becomes a streaming
 * subscription, and in memory mode it receives this process's own publishes.
 *
 * <p>The method takes the payload type ({@code OrderCreated}), a
 * {@link com.adhar.kit.dapr.pubsub.DaprCloudEvent DaprCloudEvent&lt;OrderCreated&gt;} or the SDK's
 * {@code CloudEvent<OrderCreated>}, or nothing. It answers Dapr by returning {@code void}
 * (SUCCESS), a {@link com.adhar.kit.dapr.pubsub.DispatchStatus} or a {@code boolean} (false =
 * RETRY); an exception means RETRY and a
 * {@link com.adhar.kit.dapr.pubsub.DropEventException} (or data that cannot be read as the
 * parameter type) means DROP.</p>
 *
 * <pre>{@code
 * @DaprSubscribe(topic = "orders", deadLetterTopic = "orders-dlq")
 * public void onOrder(OrderCreated event) {
 *     // Process event
 * }
 * }</pre>
 *
 * <p>String attributes accept {@code ${...}} placeholders.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprSubscribe {

    /**
     * Pub/sub component name; empty for the service default ({@code adhar.dapr.pubsub},
     * {@code pubsub} on the platform).
     */
    String pubsubName() default "";

    /**
     * Topic name.
     */
    String topic();

    /**
     * Dead letter topic (optional).
     */
    String deadLetterTopic() default "";

    /**
     * Route Dapr delivers to; empty for {@code /dapr/events/<pubsub>/<topic>}.
     */
    String route() default "";

    /**
     * CEL routing rule (for example {@code event.type == "order.cancelled"}): several methods can
     * share a topic, each receiving the events its rule matches; the method without a rule gets
     * the rest. Rules are evaluated by the sidecar.
     */
    String match() default "";

    /**
     * Priority of the routing rule (lower first).
     */
    int priority() default 0;

    /**
     * Subscription metadata as {@code key=value} pairs (for example {@code rawPayload=true}).
     */
    String[] metadata() default {};

    /**
     * Ask Dapr to deliver in batches; the kit still calls the method once per event and reports
     * a status for each.
     */
    boolean bulk() default false;

    /**
     * Maximum batch size when {@link #bulk()} is on ({@code -1}: Dapr's default).
     */
    int maxMessagesCount() default -1;

    /**
     * Maximum batch wait in milliseconds when {@link #bulk()} is on ({@code -1}: Dapr's default).
     */
    int maxAwaitDurationMs() default -1;
}
