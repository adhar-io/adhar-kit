package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Distributed cache lock to prevent cache stampede.
 *
 * <p>Ensures only one thread/instance computes a value while others wait.</p>
 *
 * <p><b>Example - Prevent Cache Stampede:</b></p>
 * <pre>{@code
 * @Service
 * public class ReportService {
 *
 *     @CacheLock(
 *         lockKey = "#reportId",
 *         waitTime = 30,
 *         leaseTime = 60
 *     )
 *     @Cacheable(cacheName = "reports", key = "#reportId")
 *     public Report generateReport(String reportId) {
 *         // Expensive operation - only one instance executes
 *         return heavyReportGeneration(reportId);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheLock {

    /**
     * Lock key expression.
     */
    String lockKey();

    /**
     * Maximum wait time for lock (seconds).
     */
    long waitTime() default 10;

    /**
     * Lock lease time (seconds).
     */
    long leaseTime() default 30;

    /**
     * Fail immediately if lock not acquired.
     */
    boolean failFast() default false;
}

