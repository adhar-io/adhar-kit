package com.adhar.kit.kubernetes.annotation;

import java.lang.annotation.*;

/**
 * Marks a component as a leader-elected service.
 *
 * <p>Only one instance of the annotated bean will be active at a time across the cluster.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @LeaderElected(lockName = "job-scheduler-lock")
 * @Component
 * public class JobScheduler {
 *
 *     @Scheduled(fixedRate = 60000)
 *     public void processJobs() {
 *         // Only executed by the leader instance
 *         if (isLeader()) {
 *             // Process jobs
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LeaderElected {

    /**
     * Lock name for leader election.
     */
    String lockName() default "leader-election";

    /**
     * Namespace for the lock.
     */
    String namespace() default "";

    /**
     * Lease duration in milliseconds.
     */
    long leaseDuration() default 15000;

    /**
     * Renew deadline in milliseconds.
     */
    long renewDeadline() default 10000;

    /**
     * Retry period in milliseconds.
     */
    long retryPeriod() default 2000;
}

