package com.adhar.kit.docs.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.*;

/**
 * Marks a parameter as pageable with standard pagination documentation.
 *
 * <p>Automatically documents pagination parameters:</p>
 * <ul>
 *   <li>page - Page number (default: 0)</li>
 *   <li>size - Page size (default: 20)</li>
 *   <li>sort - Sort criteria (e.g., "name,asc")</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @GetMapping("/api/orders")
 * @PageableDocumentation
 * public ResponseEntity<Page<Order>> getOrders(@PageableDefault Pageable pageable) {
 *     return ResponseEntity.ok(orderService.getOrders(pageable));
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(
    in = ParameterIn.QUERY,
    name = "page",
    description = "Page number (0-based)",
    schema = @Schema(type = "integer", defaultValue = "0")
)
@Parameter(
    in = ParameterIn.QUERY,
    name = "size",
    description = "Number of items per page",
    schema = @Schema(type = "integer", defaultValue = "20")
)
@Parameter(
    in = ParameterIn.QUERY,
    name = "sort",
    description = "Sort criteria (e.g., 'name,asc' or 'createdDate,desc')",
    schema = @Schema(type = "string")
)
public @interface PageableDocumentation {

    /**
     * Default page size.
     */
    int defaultSize() default 20;

    /**
     * Maximum page size.
     */
    int maxSize() default 100;
}

