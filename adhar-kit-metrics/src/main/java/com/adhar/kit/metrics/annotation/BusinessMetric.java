package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to record business KPI metrics for a method.
 * <p>
 * Every invocation increments the counter {@code adhar.business.<name>} tagged with the
 * business {@code category}, an {@code outcome} tag (success/error) and any custom tags.
 * When the method returns a {@link Number} and {@link #recordValue()} is enabled, the
 * value is additionally recorded in the distribution summary
 * {@code adhar.business.<name>.value} (e.g. revenue per order).
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @BusinessMetric(name = "orders.placed", category = "sales", tags = {"channel", "web"})
 * public BigDecimal placeOrder(Order order) {
 *     // returns the order total which is recorded as a value distribution
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessMetric {

    /**
     * The business metric name (appended to the {@code adhar.business.} prefix).
     * If not specified, a name is derived from the class and method name.
     *
     * @return the metric name
     */
    String name() default "";

    /**
     * The business category the KPI belongs to (e.g. "sales", "billing").
     *
     * @return the category tag value
     */
    String category() default "business";

    /**
     * The description of the business metric.
     *
     * @return the metric description
     */
    String description() default "";

    /**
     * Additional tags as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record the numeric return value in a distribution summary
     * ({@code adhar.business.<name>.value}) when the method returns a {@link Number}.
     *
     * @return true to record the value distribution
     */
    boolean recordValue() default true;
}
