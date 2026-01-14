package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to invoke another service via Dapr service invocation.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprInvoke(appId = "order-service", method = "GET", endpoint = "/orders/{orderId}")
 * public Order getOrder(String orderId) {
 *     // Actual invocation handled by annotation processor
 *     return null;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprInvoke {

    /**
     * Target service app ID.
     */
    String appId();

    /**
     * HTTP method.
     */
    String method() default "GET";

    /**
     * Endpoint path (supports path variables).
     */
    String endpoint();

    /**
     * Response type.
     */
    Class<?> responseType() default Object.class;
}

