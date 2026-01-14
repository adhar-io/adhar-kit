package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a Dapr Actor.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprActor(type = "OrderActor")
 * public class OrderActor {
 *
 *     @DaprActorMethod
 *     public Order processOrder(OrderRequest request) {
 *         // Actor method
 *         return new Order(request);
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
public @interface DaprActor {

    /**
     * Actor type name.
     */
    String type();

    /**
     * Actor idle timeout.
     */
    String idleTimeout() default "60m";
}

/**
 * Marks a method as a Dapr Actor method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface DaprActorMethod {
    /**
     * Method name (defaults to method name).
     */
    String value() default "";
}

