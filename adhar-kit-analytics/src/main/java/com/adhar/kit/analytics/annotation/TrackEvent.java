package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Automatically tracks method execution as an analytics event.
 *
 * <p>Simplifies event tracking by declaratively marking methods that should
 * generate analytics events when executed.</p>
 *
 * <p><b>Example - Track User Actions:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @TrackEvent(
 *         event = "User Registered",
 *         userIdParam = "userId",
 *         properties = {"email", "plan"}
 *     )
 *     public User register(String userId, String email, String plan) {
 *         // Registration logic...
 *         return user;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Track with Custom Properties:</b></p>
 * <pre>{@code
 * @TrackEvent(
 *     event = "Product Purchased",
 *     userIdParam = "userId",
 *     properties = {"productId", "price", "currency"}
 * )
 * public Order purchaseProduct(String userId, String productId, double price, String currency) {
 *     // Purchase logic...
 *     return order;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackEvent {

    /**
     * Event name to track.
     * Supports parameter substitution using {paramName}.
     */
    String event();

    /**
     * Parameter name containing the user ID.
     * Defaults to "userId".
     */
    String userIdParam() default "userId";

    /**
     * Parameter names to include as event properties.
     * Empty array means include all parameters.
     */
    String[] properties() default {};

    /**
     * Exclude specific parameters from properties.
     */
    String[] excludeProperties() default {};

    /**
     * Track on method entry (before execution).
     */
    boolean trackOnEntry() default false;

    /**
     * Track on method success (after execution).
     */
    boolean trackOnSuccess() default true;

    /**
     * Track on method failure (exception thrown).
     */
    boolean trackOnFailure() default false;

    /**
     * Include method return value in properties.
     */
    boolean includeReturnValue() default false;

    /**
     * Return value property name.
     */
    String returnValueProperty() default "result";

    /**
     * Track asynchronously (non-blocking).
     */
    boolean async() default true;
}

