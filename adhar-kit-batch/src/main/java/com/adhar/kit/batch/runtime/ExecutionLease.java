package com.adhar.kit.batch.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Who owns a running execution, and since when it last proved alive (design 0025 §5.9). The
 * single or manager process writes its lease when the execution starts and renews it every
 * {@code adhar.batch.recovery.heartbeat-interval}; another run may take a STARTED execution over
 * only when the owner pod is gone or the lease is older than {@code recovery.stale-after}.
 *
 * <p>Times come from the database's clock, so pods on nodes whose clocks drift still agree on
 * how old a lease is.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public interface ExecutionLease {

    /**
     * @param executionId the execution this process just started
     * @param owner       this process
     */
    void acquire(long executionId, Owner owner);

    /**
     * @param executionId the execution
     * @param owner       this process
     * @param lastCommit  when the execution last committed a chunk, {@code null} if not yet
     * @return false when the lease is no longer this process's (another run recovered the
     *         execution): the process must stop writing
     */
    boolean heartbeat(long executionId, Owner owner, Instant lastCommit);

    /**
     * @param executionId an execution that ended, or was recovered
     */
    void release(long executionId);

    /**
     * @param executionId an execution
     * @return its lease, empty when none was written (or it was released)
     */
    Optional<Lease> find(long executionId);

    /**
     * @return a lease that keeps nothing, for a run without a database (which the runner refuses)
     */
    static ExecutionLease none() {
        return new ExecutionLease() {
            @Override
            public void acquire(long executionId, Owner owner) {
            }

            @Override
            public boolean heartbeat(long executionId, Owner owner, Instant lastCommit) {
                return true;
            }

            @Override
            public void release(long executionId) {
            }

            @Override
            public Optional<Lease> find(long executionId) {
                return Optional.empty();
            }
        };
    }

    /**
     * The process owning an execution.
     *
     * @param pod  the pod's name
     * @param uid  the pod's uid, empty when unknown
     * @param node the node, empty when unknown
     */
    record Owner(String pod, String uid, String node) {

        /**
         * @param context a run's context
         * @return its pod as an owner
         */
        public static Owner of(RunContext context) {
            return new Owner(context.podName(), context.podUid(), context.nodeName());
        }
    }

    /**
     * A lease as read.
     *
     * @param executionId  the execution
     * @param owner        who holds it
     * @param acquiredAt   when it was written
     * @param heartbeatAt  when it was last renewed
     * @param lastCommitAt when the execution last committed, if known
     * @param age          how long ago it was last renewed, by the database's clock
     */
    record Lease(long executionId, Owner owner, Instant acquiredAt, Instant heartbeatAt, Instant lastCommitAt,
                 Duration age) {
    }
}
