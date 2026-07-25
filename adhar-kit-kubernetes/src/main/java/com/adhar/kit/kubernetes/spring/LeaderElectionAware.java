package com.adhar.kit.kubernetes.spring;

/**
 * Optional callback contract for {@link com.adhar.kit.kubernetes.annotation.LeaderElected}
 * beans that want to be notified of leadership transitions directly, rather than
 * polling {@code isLeader()} from a {@link com.adhar.kit.kubernetes.service.LeaderElectionService}.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @LeaderElected(lockName = "job-scheduler-lock")
 * @Component
 * public class JobScheduler implements LeaderElectionAware {
 *
 *     private volatile boolean leader;
 *
 *     @Override
 *     public void onStartedLeading() {
 *         leader = true;
 *     }
 *
 *     @Override
 *     public void onStoppedLeading() {
 *         leader = false;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public interface LeaderElectionAware {

    /**
     * Invoked when this bean's instance becomes the elected leader.
     */
    default void onStartedLeading() {
    }

    /**
     * Invoked when this bean's instance stops being (or never becomes) leader.
     */
    default void onStoppedLeading() {
    }
}
