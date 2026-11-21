package com.adhar.kit.batch.ops;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.runtime.AdharBatchRunner.Sleeper;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.RecoveryPolicy;
import com.adhar.kit.batch.runtime.RecoveryPolicy.Verdict;
import com.adhar.kit.batch.runtime.RunContext;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.runtime.RunOutcome.Command;
import com.adhar.kit.batch.runtime.RunOutcome.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Carries out an ops command (design 0025 §5.2 {@code BatchCommandRunner}, §8.1) in a run pod
 * started by {@code adhar batch abandon|recover|dlq --replay} or the Console: the run template,
 * unchanged but for {@code ADHAR_BATCH_COMMAND}, {@code ADHAR_BATCH_EXECUTION_ID} and the trigger
 * {@code ops}. It acts on the job's own JobRepository with the job's own credentials and never runs
 * the business job.
 *
 * <pre>
 * abandon       ended (FAILED, STOPPED, STOPPING with a dead owner) -&gt; ABANDONED       exit 0 abandoned | already-abandoned
 * recover       left running by a dead owner -&gt; FAILED, restartable                   exit 0 recovered | nothing-to-recover
 * stop          running -&gt; STOPPED, steps stop at a chunk; waits shutdown.timeout   exit 0 stopped | stop-requested | not-running
 * status        the execution (or the job's latest), unchanged                        exit 0 &lt;status&gt;
 * housekeeping  D4 retention of the job's metadata                                     exit 0 housekept
 * dlq-replay    the execution's rejected items through their step again               exit 0 replayed | nothing-to-replay
 *                                                                                     exit 3 rejected (some remain)
 * refused       exit 2 invalid: execution-id, unknown-execution, other-job, completed, running, owner-alive, retention
 * </pre>
 *
 * <p>An execution whose owner may still hold it is never changed: the {@link RecoveryPolicy} the
 * runner itself uses decides, so a live owner refuses the command ({@code owner-alive}) and a
 * terminating one is waited for. A dlq-replay that leaves items rejected exits 3, not 0: a
 * succeeded ops Job is reused by its name and never runs again, so a success would hide them.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchCommandRunner {

    /** Invalid: the command needs {@code ADHAR_BATCH_EXECUTION_ID} and it is missing or not a number. */
    public static final String EXECUTION_ID = "execution-id";
    /** Invalid: {@code ADHAR_BATCH_COMMAND} names no command. */
    public static final String UNKNOWN_COMMAND = "unknown-command";
    /** Invalid: the JobRepository has no such execution. */
    public static final String UNKNOWN_EXECUTION = "unknown-execution";
    /** Invalid: the execution belongs to another job than the run template's. */
    public static final String OTHER_JOB = "other-job";
    /** Invalid: the execution completed, which nothing can abandon or recover. */
    public static final String COMPLETED = "completed";
    /** Invalid: the execution is running and its items may still change. */
    public static final String RUNNING = "running";
    /** Invalid: the execution's owner pod is alive and holds its lease. */
    public static final String OWNER_ALIVE = "owner-alive";
    /** Invalid: housekeeping was asked for, and no retention is configured. */
    public static final String RETENTION = "retention";
    /** Failed (exit 3): dead letters of the execution are still rejected after the replay. */
    public static final String REJECTED = "rejected";

    /** How often {@code stop} and a wait for a terminating owner look again. */
    static final Duration POLL = Duration.ofSeconds(5);

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final RecoveryPolicy recovery;
    private final ExecutionLease lease;
    private final RepositoryHousekeeping housekeeping;
    private final DeadLetterReplayer replayer;
    private final BatchProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;
    private final BooleanSupplier stopping;

    /**
     * @param jobRepository the JobRepository
     * @param jobOperator   Spring Batch's operator
     * @param recovery      decides whether an execution's owner is gone
     * @param lease         the execution leases
     * @param housekeeping  the D4 retention
     * @param replayer      the dead-letter replay, {@code null} when there is no dead-letter table
     * @param properties    {@code adhar.batch.*}
     * @param clock         the clock of the waits
     * @param sleeper       waits between polls
     * @param stopping      whether the pod is being terminated (a wait then gives up)
     */
    public BatchCommandRunner(JobRepository jobRepository, JobOperator jobOperator, RecoveryPolicy recovery,
                              ExecutionLease lease, RepositoryHousekeeping housekeeping, DeadLetterReplayer replayer,
                              BatchProperties properties, Clock clock, Sleeper sleeper, BooleanSupplier stopping) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.recovery = recovery;
        this.lease = lease;
        this.housekeeping = housekeeping;
        this.replayer = replayer;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
        this.stopping = stopping;
    }

    /**
     * @param value       {@code ADHAR_BATCH_COMMAND}
     * @param executionId {@code ADHAR_BATCH_EXECUTION_ID}, may be blank for {@code status} and
     *                    {@code housekeeping}
     * @param job         the run template's job
     * @param context     this run
     * @return how the command ended
     */
    public RunOutcome run(String value, String executionId, Job job, RunContext context) {
        BatchCommand command = BatchCommand.parse(value).orElse(null);
        if (command == null) {
            return invalid(new Command(value.strip(), null, null, null, Map.of()), UNKNOWN_COMMAND,
                    "ADHAR_BATCH_COMMAND=" + value.strip() + " is not a command: one of abandon, recover, stop, status, "
                            + "housekeeping, dlq-replay");
        }
        Long id = null;
        if (executionId != null && !executionId.isBlank()) {
            try {
                id = Long.parseLong(executionId.strip());
            } catch (NumberFormatException ex) {
                id = -1L;
            }
        }
        if ((id != null && id <= 0) || (id == null && command.needsExecution())) {
            return invalid(Command.on(command.label(), null), EXECUTION_ID, command.label() + " needs "
                    + "ADHAR_BATCH_EXECUTION_ID, the positive id of the execution it acts on");
        }
        log.info("Ops command {} on job [{}]{}", command.label(), job.getName(), id == null ? "" : ", execution " + id);
        if (command == BatchCommand.HOUSEKEEPING) {
            return housekeeping(job);
        }
        if (command == BatchCommand.DLQ_REPLAY) {
            return replay(job, id);
        }
        JobExecution execution = id != null ? execution(id) : latest(job.getName());
        if (execution == null) {
            return invalid(new Command(command.label(), null, id, null, Map.of()), UNKNOWN_EXECUTION, id == null
                    ? "job [" + job.getName() + "] has no execution" : "the JobRepository has no execution " + id);
        }
        if (!execution.getJobInstance().getJobName().equals(job.getName())) {
            return otherJob(command, execution, job);
        }
        return switch (command) {
            case ABANDON -> abandon(execution, context);
            case RECOVER -> recover(execution, context);
            case STOP -> stop(execution);
            default -> status(execution);
        };
    }

    /**
     * @return the execution, {@code null} when there is none (Spring Batch 6.0's DAO throws for an
     *         unknown id instead of answering {@code null})
     */
    private JobExecution execution(long id) {
        try {
            return jobRepository.getJobExecution(id);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private JobExecution latest(String jobName) {
        JobInstance instance = jobRepository.getLastJobInstance(jobName);
        return instance == null ? null : jobRepository.getLastJobExecution(instance);
    }

    // ---------------------------------------------------------------------------------------
    // abandon, recover: only an execution nobody owns
    // ---------------------------------------------------------------------------------------

    private RunOutcome abandon(JobExecution execution, RunContext context) {
        String verb = BatchCommand.ABANDON.label();
        if (execution.getStatus() == BatchStatus.ABANDONED) {
            return done(Command.on(verb, execution), "already-abandoned", "execution " + execution.getId()
                    + " was already abandoned");
        }
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            return invalid(Command.on(verb, execution), COMPLETED, "execution " + execution.getId()
                    + " completed: there is nothing to abandon");
        }
        Owned owned = ownership(execution, context, verb);
        if (owned.refusal != null) {
            return owned.refusal;
        }
        JobExecution current = owned.execution;
        if (owned.orphaned) {
            // Its steps still say running: recover first, or the abandoned execution would keep
            // a step Spring Batch counts as running forever.
            current = jobOperator.recover(current);
        }
        try {
            JobExecution abandoned = jobOperator.abandon(current);
            lease.release(abandoned.getId());
            return done(Command.on(verb, abandoned), "abandoned", "execution " + abandoned.getId() + " is abandoned: "
                    + "a run of its identity now exits 6 and needs new parameters");
        } catch (JobExecutionAlreadyRunningException ex) {
            return invalid(Command.on(verb, current), RUNNING, ex.getMessage());
        }
    }

    private RunOutcome recover(JobExecution execution, RunContext context) {
        String verb = BatchCommand.RECOVER.label();
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            return invalid(Command.on(verb, execution), COMPLETED, "execution " + execution.getId()
                    + " completed: there is nothing to recover");
        }
        Owned owned = ownership(execution, context, verb);
        if (owned.refusal != null) {
            return owned.refusal;
        }
        if (!owned.orphaned) {
            return done(Command.on(verb, owned.execution), "nothing-to-recover", "execution " + execution.getId()
                    + " is " + owned.execution.getStatus() + " with no step running: it needs no recovery");
        }
        JobExecution recovered = jobOperator.recover(owned.execution);
        lease.release(recovered.getId());
        return done(Command.on(verb, recovered), "recovered", "execution " + recovered.getId() + " is "
                + recovered.getStatus() + ": its identity can run or be restarted again");
    }

    /**
     * Whether anyone may still own the execution, decided as the runner decides it for a run of
     * the same identity; waits while its owner terminates.
     */
    private Owned ownership(JobExecution execution, RunContext context, String verb) {
        Instant waitingSince = null;
        JobExecution current = execution;
        while (true) {
            if (!current.getStatus().isRunning() && runningSteps(current) == 0) {
                return new Owned(current, false, null);
            }
            Verdict verdict = recovery.decide(current, context);
            log.info("Execution {} is {}: {} ({})", current.getId(), current.getStatus(), verdict.decision(),
                    verdict.reason());
            switch (verdict.decision()) {
                case RECOVER -> {
                    return new Owned(current, true, null);
                }
                case WAIT -> {
                    waitingSince = waitingSince == null ? clock.instant() : waitingSince;
                    Duration waited = Duration.between(waitingSince, clock.instant());
                    if (stopping.getAsBoolean() || waited.compareTo(properties.getRecovery().getMaxWait()) >= 0) {
                        return new Owned(current, false, RunOutcome.command(Kind.FAILED, OWNER_ALIVE,
                                ExitCodes.FAILED_RETRYABLE, Command.on(verb, current), "execution " + current.getId()
                                        + " is still owned by a terminating pod after " + waited));
                    }
                    sleeper.sleep(POLL);
                    JobExecution reread = execution(current.getId());
                    current = reread != null ? reread : current;
                }
                default -> {
                    return new Owned(current, false, invalid(Command.on(verb, current), OWNER_ALIVE,
                            "execution " + current.getId() + " may still be running: " + verdict.reason()
                                    + ". Stop its run first"));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // stop, status
    // ---------------------------------------------------------------------------------------

    private RunOutcome stop(JobExecution execution) {
        String verb = BatchCommand.STOP.label();
        try {
            jobOperator.stop(execution);
        } catch (JobExecutionNotRunningException ex) {
            return done(Command.on(verb, execution), "not-running", "execution " + execution.getId() + " is "
                    + execution.getStatus() + ": there is nothing to stop");
        }
        Instant asked = clock.instant();
        JobExecution current = execution;
        // Spring Batch records the execution and its running steps STOPPED at once; the owner pod
        // notices at its next chunk boundary, ends its job and releases the lease. Only then has
        // the run stopped.
        while ((stillRunning(current) || lease.find(current.getId()).isPresent()) && !stopping.getAsBoolean()
                && Duration.between(asked, clock.instant()).compareTo(properties.getShutdown().getTimeout()) < 0) {
            sleeper.sleep(POLL);
            JobExecution reread = execution(execution.getId());
            current = reread != null ? reread : current;
        }
        if (!stillRunning(current) && lease.find(current.getId()).isEmpty()) {
            return done(Command.on(verb, current), "stopped", "execution " + current.getId() + " is "
                    + current.getStatus() + " and its owner has let it go");
        }
        return done(Command.on(verb, current), "stop-requested", "execution " + current.getId() + " is "
                + current.getStatus() + " and its owner still holds it: its run stops at the next chunk boundary and "
                + "exits 4. An owner that is gone never lets go; recover the execution then");
    }

    private RunOutcome status(JobExecution execution) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("runningSteps", runningSteps(execution));
        lease.find(execution.getId()).ifPresent(owner -> counts.put("leaseAgeSeconds", owner.age().toSeconds()));
        return done(Command.on(BatchCommand.STATUS.label(), execution).counting(counts),
                execution.getStatus().name().toLowerCase(Locale.ROOT), "execution " + execution.getId() + " of job ["
                        + execution.getJobInstance().getJobName() + "] is " + execution.getStatus());
    }

    // ---------------------------------------------------------------------------------------
    // housekeeping, dlq-replay
    // ---------------------------------------------------------------------------------------

    private RunOutcome housekeeping(Job job) {
        String verb = BatchCommand.HOUSEKEEPING.label();
        BatchProperties.Retention retention = properties.getRetention();
        if (retention.getDays() == null) {
            return invalid(Command.on(verb, null), RETENTION, "no retention is configured: set "
                    + "adhar.batch.retention.days (ADHAR_BATCH_RETENTION_DAYS, which the platform passes)");
        }
        try {
            RepositoryHousekeeping.Result result = housekeeping.clean(job.getName(), job.isRestartable(),
                    retention.getDays(), retention.getKeepLast());
            return done(Command.on(verb, null).counting(counts("deleted", result.deleted(),
                    "kept", result.kept())), "housekept", "deleted " + result.deleted() + " of "
                    + result.examined() + " instances of job [" + job.getName() + "]");
        } catch (RuntimeException ex) {
            log.error("Housekeeping of job [{}] failed", job.getName(), ex);
            return RunOutcome.command(Kind.FAILED, "housekeeping", ExitCodes.FAILED_RETRYABLE, Command.on(verb, null),
                    ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    private RunOutcome replay(Job job, long executionId) {
        String verb = BatchCommand.DLQ_REPLAY.label();
        JobExecution execution = execution(executionId);
        Command target = execution != null ? Command.on(verb, execution)
                : new Command(verb, null, executionId, null, Map.of());
        if (replayer == null) {
            return RunOutcome.command(Kind.FAILED, REJECTED, ExitCodes.FAILED_NOT_RETRYABLE, target,
                    "there is no dead-letter table (adhar.batch.dead-letter.enabled=false, or no DataSource)");
        }
        if (execution != null && !execution.getJobInstance().getJobName().equals(job.getName())) {
            return otherJob(BatchCommand.DLQ_REPLAY, execution, job);
        }
        if (execution != null && (execution.getStatus().isRunning() || runningSteps(execution) > 0)) {
            return invalid(target, RUNNING, "execution " + executionId + " is still " + execution.getStatus()
                    + ": its items may still be dead-lettered. Replay it once its run has ended");
        }
        for (String other : replayer.jobsOf(executionId)) {
            if (!other.equals(job.getName())) {
                return invalid(target, OTHER_JOB, "the dead letters of execution " + executionId + " are job ["
                        + other + "]'s, not [" + job.getName() + "]'s: start the replay from that job's template");
            }
        }
        if (execution == null && replayer.jobsOf(executionId).isEmpty()) {
            return invalid(target, UNKNOWN_EXECUTION, "the JobRepository has no execution " + executionId
                    + " and it has no dead letters");
        }
        if (replayer.pending(executionId) == 0) {
            return done(target.counting(counts("replayed", 0, "rejected", 0)), "nothing-to-replay",
                    "execution " + executionId + " has no rejected item left");
        }
        DeadLetterReplayer.Result result = replayer.replay(job.getName(), executionId);
        Command counted = target.counting(counts("replayed", result.replayed(), "rejected",
                result.rejected()));
        if (result.rejected() > 0) {
            return RunOutcome.command(Kind.FAILED, REJECTED, ExitCodes.FAILED_NOT_RETRYABLE, counted, result.rejected()
                    + " item(s) of execution " + executionId + " are still rejected (" + result.replayed()
                    + " replayed): fix them and ask again");
        }
        return done(counted, "replayed", result.replayed() + " item(s) of execution " + executionId + " replayed");
    }

    // ---------------------------------------------------------------------------------------

    /** Two counts, in this order. */
    private static Map<String, Long> counts(String first, long firstValue, String second, long secondValue) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(first, firstValue);
        counts.put(second, secondValue);
        return counts;
    }

    private static boolean stillRunning(JobExecution execution) {
        return execution.getStatus().isRunning() || runningSteps(execution) > 0;
    }

    private static long runningSteps(JobExecution execution) {
        return execution.getStepExecutions().stream().filter(step -> step.getStatus().isRunning()).count();
    }

    private static RunOutcome otherJob(BatchCommand command, JobExecution execution, Job job) {
        return invalid(Command.on(command.label(), execution), OTHER_JOB, "execution " + execution.getId()
                + " is job [" + execution.getJobInstance().getJobName() + "]'s, not [" + job.getName() + "]'s: "
                + "start the ops Job from that job's run template");
    }

    private static RunOutcome done(Command command, String reason, String message) {
        log.info("Ops command {}: {} ({})", command.verb(), reason, message);
        return RunOutcome.command(Kind.COMPLETED, reason, ExitCodes.COMPLETED, command, message);
    }

    private static RunOutcome invalid(Command command, String reason, String message) {
        log.error("Ops command {} refused ({}): {}", command.verb(), reason, message);
        return RunOutcome.command(Kind.INVALID, reason, ExitCodes.INVALID_REQUEST, command, message);
    }

    /**
     * @param execution the execution as last read
     * @param orphaned  whether it, or a step of it, still says running with nobody owning it
     * @param refusal   the outcome when the command must not touch it
     */
    private record Owned(JobExecution execution, boolean orphaned, RunOutcome refusal) {
    }
}
