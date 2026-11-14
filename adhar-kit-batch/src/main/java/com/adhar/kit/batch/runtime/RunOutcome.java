package com.adhar.kit.batch.runtime;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;

import java.util.Locale;

/**
 * How a Kubernetes run ended: what the termination summary says and what the process exits with
 * (design 0025 §5.7, D13).
 *
 * @param kind      the outcome
 * @param reason    why, for {@link Kind#SKIPPED} ({@value #ALREADY_COMPLETE}, {@value #DUPLICATE},
 *                  {@code calendar}) and {@link Kind#INVALID} ({@code parameters},
 *                  {@value #UNKNOWN_JOB}, {@value #INCREMENTER}, {@value #RESOURCELESS_REPOSITORY},
 *                  {@code migration}, {@code business-date}); {@code null} otherwise
 * @param exitCode  the process exit code
 * @param execution the execution the run started or found, {@code null} when there is none
 * @param instanceId the JobInstance, when known without an execution of this run
 * @param message   what happened, for the summary's {@code exitDescription}
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record RunOutcome(Kind kind, String reason, int exitCode, JobExecution execution, Long instanceId,
                         String message) {

    /** Skipped: the identity already completed. */
    public static final String ALREADY_COMPLETE = "already-complete";

    /** Skipped: another pod is running the identity. */
    public static final String DUPLICATE = "duplicate";

    /** Invalid: no job of that name. */
    public static final String UNKNOWN_JOB = "unknown-job";

    /** Invalid: the job has an incrementer it is not allowed. */
    public static final String INCREMENTER = "incrementer";

    /** Invalid: the JobRepository keeps nothing. */
    public static final String RESOURCELESS_REPOSITORY = "resourceless-repository";

    /** The process status of a JVM ended by SIGTERM (128 + 15). */
    public static final int SIGTERM_STATUS = 143;

    /**
     * How a run ended, as the summary's {@code outcome}.
     */
    public enum Kind {
        /** The execution completed. */
        COMPLETED,
        /** Nothing to do: already complete, a duplicate, or not a business day. */
        SKIPPED,
        /** The request cannot succeed: parameters, unknown job, repository, business date. */
        INVALID,
        /** The execution failed, or the run could not start. */
        FAILED,
        /** The execution stopped. */
        STOPPED,
        /** The last execution was abandoned. */
        ABANDONED,
        /** The repository state is unknown. */
        UNKNOWN;

        /**
         * @return the summary's {@code outcome} value
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param execution a finished execution
     * @return its outcome and {@link ExitCodes#of(JobExecution) exit code}
     */
    public static RunOutcome of(JobExecution execution) {
        BatchStatus status = execution.getStatus();
        Kind kind = switch (status) {
            case COMPLETED -> Kind.COMPLETED;
            case STOPPED -> Kind.STOPPED;
            case FAILED -> Kind.FAILED;
            case ABANDONED -> Kind.ABANDONED;
            default -> Kind.UNKNOWN;
        };
        return new RunOutcome(kind, null, ExitCodes.of(execution), execution, null,
                execution.getExitStatus().getExitDescription());
    }

    /**
     * @param execution an execution stopped because the pod received SIGTERM
     * @return outcome {@code stopped} with the JVM's own signal status, which is what the pod
     *         reports: the kit never calls {@code System.exit} during a shutdown
     */
    public static RunOutcome stoppedBySignal(JobExecution execution) {
        return new RunOutcome(Kind.STOPPED, null, SIGTERM_STATUS, execution, null,
                "stopped by SIGTERM: the pod was suspended, preempted, evicted or drained");
    }

    /**
     * @param reason     why
     * @param exitCode   0, or 3 for {@code adhar.batch.exit.already-complete=fail}
     * @param instanceId the JobInstance, when known
     * @param execution  the execution found, when there is one
     * @param message    what happened
     * @return a skipped run
     */
    public static RunOutcome skipped(String reason, int exitCode, Long instanceId, JobExecution execution,
                                     String message) {
        return new RunOutcome(Kind.SKIPPED, reason, exitCode, execution, instanceId, message);
    }

    /**
     * @param reason  why
     * @param message what is wrong and how to fix it
     * @return an invalid run, exit 2
     */
    public static RunOutcome invalid(String reason, String message) {
        return new RunOutcome(Kind.INVALID, reason, ExitCodes.INVALID_REQUEST, null, null, message);
    }

    /**
     * @param exitCode the exit code
     * @param message  what happened
     * @return a run that failed before or around its execution
     */
    public static RunOutcome failed(int exitCode, String message) {
        return new RunOutcome(Kind.FAILED, null, exitCode, null, null, message);
    }

    /**
     * @param execution the abandoned execution
     * @return exit 6: the run needs a new identity
     */
    public static RunOutcome abandoned(JobExecution execution) {
        return new RunOutcome(Kind.ABANDONED, null, ExitCodes.ABANDONED, execution, null,
                "the last execution of this identity was abandoned: run it with a new identity");
    }
}
