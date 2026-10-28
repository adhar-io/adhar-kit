package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to invoke another service via Dapr service invocation.
 *
 * <p>Enforced by {@code DaprInvokeAspect}: the method body is not run. {@code {name}}
 * placeholders in the endpoint take the argument of that parameter name; the remaining argument
 * (if any) is the request body; the response is read as the method's (generic) return type. For a
 * whole API, prefer a typed {@code @HttpExchange} interface from {@code DaprServiceClients}.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprInvoke(appId = "order-service", method = "GET", endpoint = "/api/v1/orders/{orderId}")
 * public Order getOrder(String orderId) {
 *     return null; // never runs
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

