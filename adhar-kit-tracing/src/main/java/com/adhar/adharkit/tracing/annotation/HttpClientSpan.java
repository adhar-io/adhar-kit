package com.adhar.adharkit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tracing HTTP client operations.
 * <p>
 * This annotation creates a span specifically designed for HTTP client operations,
 * automatically adding HTTP-related semantic attributes according to OpenTelemetry conventions.
 * </p>
 *
 * <pre>{@code
 * @HttpClientSpan(method = "GET", url = "#{#baseUrl}/users/#{#userId}")
 * public User fetchUserFromRemoteService(String baseUrl, String userId) {
 *     return restTemplate.getForObject(baseUrl + "/users/" + userId, User.class);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpClientSpan {

    /**
     * The name of the span. If empty, will be generated from HTTP method and URL.
     *
     * @return the span name
     */
    String value() default "";

    /**
     * The HTTP method (GET, POST, PUT, DELETE, etc.).
     *
     * @return the HTTP method
     */
    String method() default "";

    /**
     * The URL being called. Can use SpEL expressions.
     *
     * @return the URL
     */
    String url() default "";

    /**
     * Whether to include request headers in the span.
     *
     * @return true to include request headers
     */
    boolean includeRequestHeaders() default false;

    /**
     * Whether to include response headers in the span.
     *
     * @return true to include response headers
     */
    boolean includeResponseHeaders() default false;

    /**
     * Whether to include request body in the span.
     *
     * @return true to include request body
     */
    boolean includeRequestBody() default false;

    /**
     * Whether to include response body in the span.
     *
     * @return true to include response body
     */
    boolean includeResponseBody() default false;

    /**
     * Additional tags to add to the span.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
