package com.adhar.kit.commons.event;

import java.lang.annotation.*;

/**
 * Marks a class or method as an event publisher that emits CloudEvents.
 *
 * <p>This annotation can be used at class or method level to indicate CloudEvent publishing.
 * It serves as both documentation and enabler for CloudEvent-aware tooling and frameworks.</p>
 *
 * <p><b>Usage at Class Level:</b></p>
 * <pre>{@code
 * @EventPublisher
 * @Service
 * public class OrderService {
 *     public OrderCreatedEvent createOrder(OrderRequest request) {
 *         // Publishes CloudEvent
 *         return new OrderCreatedEvent(order);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Usage at Method Level:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @EventPublisher(
 *         eventType = "com.adhar.order.created",
 *         source = "https://adhar.example.com/orders"
 *     )
 *     public OrderCreatedEvent createOrder(OrderRequest request) {
 *         return new OrderCreatedEvent(order);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventPublisher {

    /**
     * The CloudEvent type that this publisher emits.
     *
     * @return the event type
     */
    String eventType() default "";

    /**
     * The CloudEvent source URI.
     *
     * @return the source URI
     */
    String source() default "";

    /**
     * Description of the events published.
     *
     * @return event description
     */
    String description() default "";
}

