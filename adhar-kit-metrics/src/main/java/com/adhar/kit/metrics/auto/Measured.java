package com.adhar.kit.metrics.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic method-level metrics collection.
 * <p>
 * When applied to a method or type, the {@link MetricsInterceptor} automatically records:
 * <ul>
 *   <li>{@code adhar.operation.duration} - Timer for execution latency</li>
 *   <li>{@code adhar.operation.count} - Counter for total invocations</li>
 *   <li>{@code adhar.operation.errors} - Counter for failed invocations</li>
 * </ul>
 *
 * <p>Each metric is tagged with: module, operation, class, method, and success.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @Measured(value = "order.create", module = "persistence")
 * public Order createOrder(OrderRequest request) {
 *     // method implementation
 * }
 * }</pre>
 *
 * <p>When applied at the type level, all public methods of the class are measured:</p>
 * <pre>{@code
 * @Measured(module = "cache")
 * public class CacheService {
 *     public void put(String key, Object value) { ... }
 *     public Object get(String key) { ... }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see MetricsInterceptor
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Measured {

    /**
     * The operation name for the metric. If empty, the method name is used.
     *
     * @return the operation name
     */
    String value() default "";

    /**
     * The module name tag (e.g., "persistence", "cache", "security").
     *
     * @return the module name
     */
    String module() default "general";

    /**
     * Optional description for the metric.
     *
     * @return the metric description
     */
    String description() default "";

    /**
     * Additional static tags as key-value pairs (e.g., {"tier", "data", "priority", "high"}).
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
