package com.adhar.adharkit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tracing database operations.
 * <p>
 * This annotation creates a span specifically designed for database operations,
 * automatically adding database-related semantic attributes according to OpenTelemetry conventions.
 * </p>
 *
 * <pre>{@code
 * @DatabaseSpan(operation = "SELECT", table = "users", statement = "SELECT * FROM users WHERE id = ?")
 * public User findById(Long id) {
 *     return jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = ?", userRowMapper, id);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DatabaseSpan {

    /**
     * The name of the span. If empty, will be generated from operation and table.
     *
     * @return the span name
     */
    String value() default "";

    /**
     * The database operation (SELECT, INSERT, UPDATE, DELETE, etc.).
     *
     * @return the database operation
     */
    String operation() default "";

    /**
     * The table or collection name being accessed.
     *
     * @return the table name
     */
    String table() default "";

    /**
     * The database statement (SQL query, etc.). Can use SpEL expressions.
     *
     * @return the database statement
     */
    String statement() default "";

    /**
     * The database name.
     *
     * @return the database name
     */
    String database() default "";

    /**
     * Whether to include the actual SQL parameters in the span.
     *
     * @return true to include parameters
     */
    boolean includeParameters() default false;

    /**
     * Additional tags to add to the span.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
