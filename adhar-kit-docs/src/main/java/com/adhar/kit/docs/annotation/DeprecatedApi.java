package com.adhar.kit.docs.annotation;

import java.lang.annotation.*;

/**
 * Marks an endpoint as deprecated with migration information.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @GetMapping("/api/v1/orders")
 * @DeprecatedApi(
 *     since = "2.0.0",
 *     migrateToUrl = "/api/v2/orders",
 *     removalDate = "2025-12-31"
 * )
 * public ResponseEntity<List<Order>> getOrdersV1() {
 *     return ResponseEntity.ok(orderService.getOrders());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeprecatedApi {

    /**
     * Version when deprecated.
     */
    String since();

    /**
     * New URL to migrate to.
     */
    String migrateToUrl() default "";

    /**
     * Planned removal date (YYYY-MM-DD).
     */
    String removalDate() default "";

    /**
     * Deprecation reason.
     */
    String reason() default "This API is deprecated";
}

