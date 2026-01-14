package com.adhar.kit.docs.annotation;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.lang.annotation.*;

/**
 * Marks an API endpoint as secured with JWT authentication.
 *
 * <p>Automatically adds JWT Bearer token requirement to the endpoint documentation.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/orders")
 * @SecuredEndpoint  // All endpoints require JWT
 * public class OrderController {
 *
 *     @GetMapping("/{id}")
 *     public ResponseEntity<Order> getOrder(@PathVariable String id) {
 *         return ResponseEntity.ok(orderService.getOrder(id));
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SecurityRequirement(name = "bearerAuth")
public @interface SecuredEndpoint {

    /**
     * Security scheme names.
     */
    String[] value() default {"bearerAuth"};
}

