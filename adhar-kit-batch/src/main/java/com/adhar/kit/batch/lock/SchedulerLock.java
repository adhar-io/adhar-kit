package com.adhar.kit.batch.lock;

import java.time.Duration;

/**
 * A named lock shared by the instances of a service, which the embedded scheduler holds for the
 * whole run of a scheduled job.
 *
 * <p>Only one holder owns a name at a time. A lock carries a lease: the holder renews it while
 * its run lasts, and a holder that crashed loses it when the lease expires, so it never blocks
 * the schedule for ever. Comparable to ShedLock, without the dependency.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public interface SchedulerLock {

    /**
     * Acquires the named lock if nobody holds it or its holder's lease has expired.
     *
     * @param name  the lock name (the scheduled job's name)
     * @param lease how long the lock stays held without a renewal
     * @return {@code true} when acquired; {@code false} when another holder owns it, this holder
     *         already owns it, or the database could not be asked
     */
    boolean tryLock(String name, Duration lease);

    /**
     * Extends a lock this holder owns.
     *
     * @param name  the lock name
     * @param lease the new lease, from now
     * @return {@code true} when still owned and extended; {@code false} when another holder has
     *         taken it over (the lease had expired)
     * @throws org.springframework.dao.DataAccessException when the database could not be
     *         reached: ownership is then unknown, and the caller may retry before the lease ends
     */
    boolean renew(String name, Duration lease);

    /**
     * Releases a lock this holder owns. Releasing a lock owned by someone else does nothing.
     *
     * @param name the lock name
     */
    void unlock(String name);

    /**
     * Checks that the lock can be used at all, so a missing table fails when the scheduler
     * starts and not at the first fire.
     *
     * @throws IllegalStateException describing what is missing
     */
    default void verify() {
    }
}
