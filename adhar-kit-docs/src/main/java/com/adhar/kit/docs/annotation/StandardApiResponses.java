package com.adhar.kit.docs.annotation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.*;

/**
 * Provides standard API responses for common HTTP status codes.
 *
 * <p>Automatically adds standard response documentation for:</p>
 * <ul>
 *   <li>200 OK - Success</li>
 *   <li>400 Bad Request - Validation Error</li>
 *   <li>401 Unauthorized - Authentication Required</li>
 *   <li>403 Forbidden - Access Denied</li>
 *   <li>404 Not Found - Resource Not Found</li>
 *   <li>500 Internal Server Error - Server Error</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/orders")
 * public class OrderController {
 *
 *     @GetMapping("/{id}")
 *     @StandardApiResponses
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
@ApiResponse(responseCode = "200", description = "Success")
@ApiResponse(responseCode = "400", description = "Bad Request - Validation error")
@ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required")
@ApiResponse(responseCode = "403", description = "Forbidden - Access denied")
@ApiResponse(responseCode = "404", description = "Not Found - Resource not found")
@ApiResponse(responseCode = "500", description = "Internal Server Error")
public @interface StandardApiResponses {
}

