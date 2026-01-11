package com.adhar.kit.metrics.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for database operation metrics monitoring.
 * <p>
 * This annotation automatically tracks database-related metrics including
 * query execution times, connection pool metrics, and database error rates.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @DatabaseMetrics(
 *     operation = "SELECT",
 *     table = "users",
 *     recordConnectionTime = true,
 *     recordRowCount = true
 * )
 * public List<User> findUsersByStatus(String status) {
 *     // database query implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseMetrics {

    /**
     * The name of the database metric.
     *
     * @return the metric name
     */
    String name() default "";

    /**
     * The database operation type (SELECT, INSERT, UPDATE, DELETE, etc.).
     *
     * @return the operation type
     */
    String operation() default "";

    /**
     * The table or entity being accessed.
     *
     * @return the table name
     */
    String table() default "";

    /**
     * The data source or database name.
     *
     * @return the data source name
     */
    String dataSource() default "";

    /**
     * Additional tags for the database metrics.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};

    /**
     * Whether to record query execution time.
     *
     * @return true to record execution time
     */
    boolean recordExecutionTime() default true;

    /**
     * Whether to record connection acquisition time.
     *
     * @return true to record connection time
     */
    boolean recordConnectionTime() default false;

    /**
     * Whether to record the number of rows affected/returned.
     *
     * @return true to record row count
     */
    boolean recordRowCount() default true;

    /**
     * Whether to record database errors separately.
     *
     * @return true to record errors
     */
    boolean recordErrors() default true;

    /**
     * Whether to record slow query alerts.
     *
     * @return true to monitor slow queries
     */
    boolean alertOnSlowQuery() default false;

    /**
     * The threshold in milliseconds for slow query alerts.
     *
     * @return the slow query threshold in ms
     */
    long slowQueryThresholdMs() default 5000;
}
