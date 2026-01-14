package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as an Aggregate Root in Domain-Driven Design.
 *
 * <p>An Aggregate Root is the entry point for a cluster of domain objects
 * that should be treated as a single unit for data changes.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @AggregateRoot
 * @Entity
 * public class Order {
 *     @Id
 *     private String orderId;
 *
 *     @OneToMany(cascade = CascadeType.ALL)
 *     private List<OrderItem> items;
 *
 *     // Only Order can modify OrderItems
 *     public void addItem(OrderItem item) {
 *         this.items.add(item);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AggregateRoot {

    /**
     * The aggregate root name.
     */
    String value() default "";

    /**
     * The bounded context this aggregate belongs to.
     */
    String boundedContext() default "";
}

