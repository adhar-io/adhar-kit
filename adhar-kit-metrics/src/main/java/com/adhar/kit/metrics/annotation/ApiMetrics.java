package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for API endpoint metrics monitoring.
 * <p>
 * This annotation automatically tracks REST API metrics including
 * request/response times, status codes, payload sizes, and rate limiting.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @ApiMetrics(
 *     endpoint = "/api/users",
 *     recordPayloadSize = true,
 *     recordStatusCodes = true,
 *     trackRateLimit = true
 * )
 * @GetMapping("/api/users")
 * public ResponseEntity<List<User>> getUsers() {
 *     // API implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiMetrics {

    /**
     * The API endpoint path.
     *
     * @return the endpoint path
     */
    String endpoint() default "";

    /**
     * The HTTP method (GET, POST, PUT, DELETE, etc.).
     *
     * @return the HTTP method
     */
    String method() default "";

    /**
     * The API version.
     *
     * @return the API version
     */
    String version() default "";

    /**
     * Additional tags for the API metrics.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record request/response payload sizes.
     *
     * @return true to record payload sizes
     */
    boolean recordPayloadSize() default true;

    /**
     * Whether to record HTTP status codes.
     *
     * @return true to record status codes
     */
    boolean recordStatusCodes() default true;

    /**
     * Whether to track rate limiting metrics.
     *
     * @return true to track rate limits
     */
    boolean trackRateLimit() default false;

    /**
     * Whether to record client IP-based metrics.
     *
     * @return true to record client metrics
     */
    boolean recordClientMetrics() default false;

    /**
     * Whether to alert on high error rates.
     *
     * @return true to monitor error rates
     */
    boolean alertOnHighErrorRate() default false;

    /**
     * The error rate threshold (percentage) for alerts.
     *
     * @return the error rate threshold
     */
    double errorRateThreshold() default 5.0;
}
