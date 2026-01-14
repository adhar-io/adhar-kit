package com.adhar.kit.commons.event;

import java.lang.annotation.*;

/**
 * Marks a method as an event handler that consumes CloudEvents.
 *
 * <p>This annotation indicates that a method handles specific types of CloudEvents.
 * It serves as documentation and can be used by frameworks for automatic event routing.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderEventHandler {
 *
 *     @EventHandler(
 *         eventType = "com.adhar.order.created",
 *         source = "https://adhar.example.com/orders"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         processOrder(event);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Multiple Event Types:</b></p>
 * <pre>{@code
 * @EventHandler(
 *     eventTypes = {"com.adhar.order.created", "com.adhar.order.updated"}
 * )
 * public void handleOrderEvent(OrderEvent event) {
 *     processOrderEvent(event);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHandler {

    /**
     * Single CloudEvent type this handler processes.
     *
     * @return the event type
     */
    String eventType() default "";

    /**
     * Multiple CloudEvent types this handler processes.
     *
     * @return array of event types
     */
    String[] eventTypes() default {};

    /**
     * The CloudEvent source URI to filter on.
     *
     * @return the source URI
     */
    String source() default "";

    /**
     * Description of what this handler does.
     *
     * @return handler description
     */
    String description() default "";

    /**
     * Order/priority of this handler.
     *
     * @return handler order
     */
    int order() default 0;
}

