package com.adhar.kit.batch.lock;

import java.time.Duration;

/**
 * Abstraction for a distributed lock used to ensure that a scheduled batch job
 * runs on only one instance at a time in a multi-instance (clustered)
 * deployment.
 *
 * <p>This provides the same core semantics as libraries such as ShedLock, but
 * without an external dependency: only one holder may own a named lock at a
 * time, and locks automatically expire after a time-to-live so that a crashed
 * instance never blocks the schedule forever.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface SchedulerLock {

    /**
     * Attempts to acquire the named lock.
     *
     * <p>The lock is granted if it is currently unheld or if the existing lock
     * has expired. When granted, it is held for at most {@code ttl} before it
     * automatically expires.</p>
     *
     * @param name the logical lock name (typically the scheduled job name)
     * @param ttl  the maximum time the lock may be held before expiring
     * @return {@code true} if the lock was acquired, {@code false} if it is held elsewhere
     */
    boolean tryLock(String name, Duration ttl);

    /**
     * Releases a lock previously acquired by this instance.
     *
     * <p>Releasing a lock not owned by this instance is a no-op.</p>
     *
     * @param name the logical lock name
     */
    void unlock(String name);
}
