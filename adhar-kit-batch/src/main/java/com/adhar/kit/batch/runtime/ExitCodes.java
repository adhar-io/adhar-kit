package com.adhar.kit.batch.runtime;

/**
 * The process exit codes of a batch run (design 0025 §5.7).
 *
 * <p>0, 4, 5, 6 and 7 equal Spring Boot's {@code BatchStatus} ordinals as mapped by
 * {@code JobExecutionExitCodeGenerator}, so a run means the same thing with or without the kit.
 * Ordinals 1-3 are non-terminal statuses a finished run never exits with: the kit gives 2 and 3
 * their own meanings, and 1 stays the JVM's generic failure. A Kubernetes {@code podFailurePolicy}
 * keys on these numbers (2, 3, 4, 6 and 7 fail the Job without a retry), so they are a contract
 * between the kit and the platform and never change meaning.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class ExitCodes {

    /** Completed, or a no-op (already complete, duplicate, not a business day). */
    public static final int COMPLETED = 0;

    /** The JVM or the application context failed before the run (bean creation, database down). */
    public static final int STARTUP_FAILURE = 1;

    /**
     * The request is invalid and retrying cannot help: parameters, unknown job, an incrementer,
     * an in-memory JobRepository, a failed JobRepository migration, an unresolved business date.
     */
    public static final int INVALID_REQUEST = 2;

    /** Failed and not retryable: a non-retryable exception, skip limit exceeded, control totals. */
    public static final int FAILED_NOT_RETRYABLE = 3;

    /** Stopped on request (another process marked the execution STOPPING), not by a signal. */
    public static final int STOPPED = 4;

    /** Failed and retryable: any other failure. */
    public static final int FAILED_RETRYABLE = 5;

    /** The last execution was ABANDONED: the run needs a new identity. */
    public static final int ABANDONED = 6;

    /** The repository state is unknown ({@code BatchStatus.UNKNOWN}). */
    public static final int UNKNOWN = 7;

    private ExitCodes() {
    }
}
