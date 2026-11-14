package com.adhar.kit.batch.scheduler;

/**
 * What one fire of a schedule did, on either engine of {@link BatchScheduler} (design 0025
 * §5.20.2). The engines share it so their meters, logs and reports speak one vocabulary; a value
 * only one engine produces says so.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum SlotOutcome {

    /** The job ran (whatever its status). */
    RAN,

    /** Fired later than the misfire threshold ({@code simple}; Quartz applies the trigger's misfire policy instead). */
    MISFIRED,

    /** The job was still running in this process ({@code simple}). */
    OVERLAPPING,

    /** Another instance held the job's lock ({@code simple}). */
    LOCKED_ELSEWHERE,

    /** The slot's JobInstance already existed, or had COMPLETED: {@code skipped(already-complete)}. */
    ALREADY_RAN,

    /** The job could not be started. */
    FAILED_TO_START,

    /**
     * The identity's last execution was left running by a replica that is gone, or Quartz re-fired
     * a dead replica's work: it was recovered and restarted from its last commit ({@code quartz}).
     */
    RECOVERED,

    /** Another live replica is running the identity: {@code skipped(duplicate)} ({@code quartz}). */
    DUPLICATE,

    /** The identity's last execution was ABANDONED, so only a new identity can run: {@code failed(abandoned)} ({@code quartz}). */
    ABANDONED,

    /**
     * Misfire policy {@code all}: the slot is older than the {@code adhar.batch.quartz.max-catch-up}
     * most recent missed slots, so it was skipped and counted ({@code quartz}).
     */
    BEYOND_CATCH_UP
}
