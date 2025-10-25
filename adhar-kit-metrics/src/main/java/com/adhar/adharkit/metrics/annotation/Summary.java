package com.adhar.adharkit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for distribution summary metrics collection.
 * <p>
 * When applied to a method, this annotation will automatically record the return value
 * (or a derived value) in a distribution summary using Micrometer DistributionSummary.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Summary(name = "user.service.response.size", description = "Size of user service responses")
 * public List<User> findUsers(UserSearchCriteria criteria) {
 *     List<User> users = userRepository.findByCriteria(criteria);
 *     return users; // The size of this list will be recorded
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Summary {

    /**
     * The name of the distribution summary metric.
     * If not specified, a name will be generated based on the class and method name.
     *
     * @return the summary name
     */
    String name() default "";

    /**
     * The description of the distribution summary metric.
     *
     * @return the summary description
     */
    String description() default "";

    /**
     * Additional tags to be added to the summary metric.
     * Should be provided as key-value pairs.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * The base unit of measurement for the summary.
     * Examples: "bytes", "items", "requests", etc.
     *
     * @return the base unit
     */
    String baseUnit() default "";

    /**
     * The percentiles to calculate for the summary.
     *
     * @return array of percentile values (e.g., 0.5, 0.95, 0.99)
     */
    double[] percentiles() default {};

    /**
     * The field or property name to extract the value from the return object.
     * If not specified, the following logic will be applied:
     * - For collections/arrays: the size/length
     * - For numbers: the value itself
     * - For other objects: toString().length() or a configured extractor
     *
     * @return the field name to extract value from
     */
    String valueField() default "";

    /**
     * Whether this summary should be registered with the registry.
     * Set to false if you want to handle registration manually.
     *
     * @return true to auto-register the summary
     */
    boolean autoRegister() default true;

    /**
     * Whether to record values for successful executions only.
     * If false, values will be recorded for all executions regardless of outcome.
     *
     * @return true to record only successful executions
     */
    boolean successOnly() default false;
}