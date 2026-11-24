package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.fault.ControlTotalsException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How a Kubernetes run ended: what the termination summary says and what the process exits with
 * (design 0025 §5.7, D13).
 *
 * @param kind      the outcome
 * @param reason    why, for {@link Kind#SKIPPED} ({@value #ALREADY_COMPLETE}, {@value #DUPLICATE},
 *                  {@value #CALENDAR}) and {@link Kind#INVALID} ({@code parameters},
 *                  {@value #UNKNOWN_JOB}, {@value #INCREMENTER}, {@value #RESOURCELESS_REPOSITORY},
 *                  {@value #UNSUPPORTED}, {@code migration}, {@code business-date}, {@value #CALENDAR_FILE},
 *                  {@value #DEFINITION_CHANGED}); for {@link Kind#FAILED}, {@value #CONTROL_TOTALS} when a
 *                  step's control totals did not balance; {@code null} otherwise
 * @param exitCode  the process exit code
 * @param execution the execution the run started or found, {@code null} when there is none
 * @param instanceId the JobInstance, when known without an execution of this run
 * @param message   what happened, for the summary's {@code exitDescription}
 * @param command   what an ops run did instead of the business job ({@code ADHAR_BATCH_COMMAND}),
 *                  {@code null} for a business run; an ops run carries no {@code execution}, its
 *                  command names the one it acted on
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record RunOutcome(Kind kind, String reason, int exitCode, JobExecution execution, Long instanceId,
                         String message, Command command) {

    /** Skipped: the identity already completed. */
    public static final String ALREADY_COMPLETE = "already-complete";

    /** Skipped: another pod is running the identity. */
    public static final String DUPLICATE = "duplicate";

    /** Skipped: the business date is not a business day of the run's calendar (D5). */
    public static final String CALENDAR = "calendar";

    /** Invalid: the calendar the run names ({@code ADHAR_BATCH_CALENDAR}) is missing or invalid (D5). */
    public static final String CALENDAR_FILE = "calendar-file";

    /**
     * Invalid: a restart would resume a job that no longer has a step the identity's executions
     * ran, a step renamed or removed by a new image (the version-safe restart guard).
     */
    public static final String DEFINITION_CHANGED = "definition-changed";

    /** Failed (exit 3): a step's control totals did not balance ({@link ControlTotalsException}). */
    public static final String CONTROL_TOTALS = "control-totals";

    /** Invalid: no job of that name. */
    public static final String UNKNOWN_JOB = "unknown-job";

    /** Invalid: the job has an incrementer it is not allowed. */
    public static final String INCREMENTER = "incrementer";

    /** Invalid: the JobRepository keeps nothing. */
    public static final String RESOURCELESS_REPOSITORY = "resourceless-repository";

    /**
     * Invalid: the run names a value reserved for a later phase ({@code adhar.batch.distribution=chunked},
     * a partition transport other than {@code jdbc}), or a role this application cannot play.
     */
    public static final String UNSUPPORTED = "unsupported";

    /** The process status of a JVM ended by SIGTERM (128 + 15). */
    public static final int SIGTERM_STATUS = 143;

    /**
     * A business run's outcome.
     *
     * @param kind       the outcome
     * @param reason     why, or {@code null}
     * @param exitCode   the process exit code
     * @param execution  the execution, or {@code null}
     * @param instanceId the JobInstance, or {@code null}
     * @param message    what happened
     */
    public RunOutcome(Kind kind, String reason, int exitCode, JobExecution execution, Long instanceId,
                      String message) {
        this(kind, reason, exitCode, execution, instanceId, message, null);
    }

    /**
     * An ops run's outcome: every one carries a reason (D13).
     *
     * @param kind     the outcome
     * @param reason   why
     * @param exitCode the process exit code
     * @param command  what the command did, and to which execution
     * @param message  what happened
     * @return the outcome
     */
    public static RunOutcome command(Kind kind, String reason, int exitCode, Command command, String message) {
        return new RunOutcome(kind, reason, exitCode, null, command.instanceId(), message, command);
    }

    /**
     * What an ops command did, for its termination summary.
     *
     * @param verb        the command ({@code abandon}, {@code recover}, ...)
     * @param instanceId  the JobInstance of the execution it acted on, {@code null} when none
     * @param executionId the execution it acted on, {@code null} when none
     * @param status      that execution's status once the command ended, {@code null} when none
     * @param counts      what it counted ({@code replayed}, {@code rejected}, {@code deleted}, ...),
     *                    in summary order
     */
    public record Command(String verb, Long instanceId, Long executionId, String status, Map<String, Long> counts) {

        /** Keeps a copy of the counts, in their order. */
        public Command {
            counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }

        /**
         * @param verb      the command
         * @param execution the execution it acted on, may be {@code null}
         * @return the command, with the execution's ids and status as they are now
         */
        public static Command on(String verb, JobExecution execution) {
            return execution == null ? new Command(verb, null, null, null, Map.of())
                    : new Command(verb, execution.getJobInstance().getInstanceId(), execution.getId(),
                    execution.getStatus().name(), Map.of());
        }

        /**
         * @param counts what the command counted, in order
         * @return this command with those counts
         */
        public Command counting(Map<String, Long> counts) {
            return new Command(verb, instanceId, executionId, status, counts);
        }
    }

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
     * @return its outcome and {@link ExitCodes#of(JobExecution) exit code}; a failure caused by
     *         control totals carries the reason {@value #CONTROL_TOTALS}
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
        String reason = status == BatchStatus.FAILED && ControlTotalsException.isCause(execution)
                ? CONTROL_TOTALS : null;
        return new RunOutcome(kind, reason, ExitCodes.of(execution), execution, null,
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
