package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Enables analytics tracking for an entire class.
 *
 * <p>Apply this annotation at class level to automatically track all public methods
 * as analytics events.</p>
 *
 * <p><b>Example - Service Level Tracking:</b></p>
 * <pre>{@code
 * @Service
 * @EnableAnalytics(
 *     userIdParam = "userId",
 *     includeAllMethods = true,
 *     eventPrefix = "Product."
 * )
 * public class ProductService {
 *
 *     // Automatically tracked as "Product.viewProduct"
 *     public Product viewProduct(String userId, String productId) {
 *         return product;
 *     }
 *
 *     // Automatically tracked as "Product.addToCart"
 *     public void addToCart(String userId, String productId, int quantity) {
 *         // Add to cart logic
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Controller Tracking:</b></p>
 * <pre>{@code
 * @RestController
 * @EnableAnalytics(
 *     userIdFrom = "session",
 *     trackPageViews = true,
 *     eventPrefix = "API."
 * )
 * public class ApiController {
 *     // All endpoints automatically tracked
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableAnalytics {

    /**
     * Parameter name containing user ID in all methods.
     */
    String userIdParam() default "userId";

    /**
     * Where to get user ID: "param", "session", "principal".
     */
    String userIdFrom() default "param";

    /**
     * Track all public methods.
     */
    boolean includeAllMethods() default true;

    /**
     * Prefix for all event names.
     */
    String eventPrefix() default "";

    /**
     * Track page views for controller methods.
     */
    boolean trackPageViews() default false;

    /**
     * Include method parameters as event properties.
     */
    boolean includeParameters() default true;

    /**
     * Parameters to exclude globally.
     */
    String[] excludeParameters() default {"password", "token", "secret", "apiKey"};

    /**
     * Track asynchronously.
     */
    boolean async() default true;
}

