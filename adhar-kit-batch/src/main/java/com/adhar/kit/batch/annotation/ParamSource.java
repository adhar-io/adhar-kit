package com.adhar.kit.batch.annotation;

/**
 * Where a {@link JobParam}'s value comes from when a run does not give one.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum ParamSource {
    /** The run's input ({@code ADHAR_BATCH_PARAMS}), else the default. */
    INPUT,
    /**
     * The run's nominal schedule time: a {@code DATE} is the business date (the scheduled time in
     * the job's time zone), a {@code DATETIME} the scheduled time itself. Never the current time.
     */
    SCHEDULE
}
