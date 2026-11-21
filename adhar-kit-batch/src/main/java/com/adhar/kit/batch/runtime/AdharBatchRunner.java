package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.calendar.BusinessCalendar;
import com.adhar.kit.batch.calendar.BusinessCalendarParser;
import com.adhar.kit.batch.calendar.BusinessCalendarParser.InvalidCalendarException;
import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.ops.BatchCommandRunner;
import com.adhar.kit.batch.ops.RepositoryHousekeeping;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.params.JobParametersResolver.ResolvedParameters;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.repository.InstanceCreationRetry;
import com.adhar.kit.batch.repository.ResourcelessRepositoryGuard;
import com.adhar.kit.batch.runtime.ExecutionLease.Owner;
import com.adhar.kit.batch.runtime.RecoveryPolicy.Verdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.util.function.SingletonSupplier;

import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs one job, once, in a Kubernetes run pod (design 0025 §5.5) and records how it ended.
 *
 * <pre>
 * guard   the JobRepository is JDBC                          else exit 2 (resourceless-repository)
 * context RunContextResolver: env, downward API, owner Job, pod
 * job     the job named by ADHAR_BATCH_JOB                   else exit 2 (unknown-job, incrementer)
 * ops     ADHAR_BATCH_COMMAND set: BatchCommandRunner runs THAT on ADHAR_BATCH_EXECUTION_ID instead of the
 *         job (a worker exits 0 skipped worker); no parameters are resolved
 *         a reserved distribution or transport, or a worker without remote partitioning: exit 2 (unsupported)
 * params  ADHAR_BATCH_PARAMS &gt; schedule &gt; defaults, validated  else exit 2 (parameters, business-date)
 * calendar adhar.batch.calendar (ADHAR_BATCH_CALENDAR) on a schedule, dependency or backfill run: a business
 *         date that is not a business day exits 0 skipped calendar; a missing or invalid file exit 2 calendar-file
 * worker  role worker (or indexed above 0): serve the identity's partitions (PartitionConsumer), start nothing
 * last execution of the identity:
 *   none, FAILED, STOPPED  start (a restart when the instance exists); with a step still running, as STARTED
 *                          a restart whose job lost a step the identity ran: exit 2 definition-changed (RestartGuard)
 *   COMPLETED              exit 0 skipped already-complete (or 3: adhar.batch.exit.already-complete=fail)
 *   ABANDONED              exit 6
 *   STARTING/STARTED/STOPPING  RecoveryPolicy: recover then start | wait for a terminating owner | exit 0 skipped duplicate
 * during  the execution's lease is renewed; a SIGTERM stops it at a chunk boundary (GracefulStopLifecycle)
 * after   COMPLETED: D4 housekeeping (adhar.batch.retention.*), whose failure is logged and counted only;
 *         ExitCodes.of(execution); TerminationSummaryWriter; BatchExitCodeGenerator
 * </pre>
 *
 * <p>Provenance travels as non-identifying parameters: {@value #TRIGGER}, {@value #ACTOR},
 * {@value #K8S_JOB}, {@value #APP_VERSION} and {@value #ATTEMPT}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class AdharBatchRunner implements ApplicationRunner, BatchRunObserver, Ordered {

    /** Non-identifying parameter: why the run exists. */
    public static final String TRIGGER = "adhar.trigger";
    /** Non-identifying parameter: who caused it. */
    public static final String ACTOR = "adhar.actor";
    /** Non-identifying parameter: the Kubernetes Job. */
    public static final String K8S_JOB = "adhar.k8s.job";
    /** Non-identifying parameter: the image digest. */
    public static final String APP_VERSION = "adhar.app.version";
    /** Non-identifying parameter: the Job's pod attempt. */
    public static final String ATTEMPT = "adhar.attempt";

    /** The ops command a run carries out instead of its job (design 0025 §6.5). */
    public static final String COMMAND = "ADHAR_BATCH_COMMAND";
    /** The execution an ops command acts on. */
    public static final String EXECUTION_ID = "ADHAR_BATCH_EXECUTION_ID";
    /** Skipped: an ops command on a worker, which leaves it to the manager. */
    public static final String WORKER = "worker";

    /** The triggers a business calendar applies to (D5): a person's run, a restart or a smoke run is never skipped. */
    static final Set<RunTrigger> CALENDAR_TRIGGERS = Set.of(RunTrigger.SCHEDULE, RunTrigger.DEPENDENCY,
            RunTrigger.BACKFILL);

    /** How often a run waiting for a terminating owner looks again. */
    static final Duration WAIT_POLL = Duration.ofSeconds(5);

    private final Supplier<Collaborators> collaborators;
    private final BatchProperties properties;
    private final Function<String, String> env;
    private final Clock clock;
    private final Sleeper sleeper;
    private final AtomicReference<Running> running = new AtomicReference<>();
    private volatile String currentJob;
    private volatile RunContext currentContext;

    /**
     * @param with       what the runner works with, resolved on first use: the runner is also an
     *                   observer of the jobs, so it exists before they do
     * @param properties {@code adhar.batch.*}
     * @param env        the process environment ({@code ADHAR_BATCH_PARAMS})
     * @param clock      the clock of the run's own start and end
     * @param sleeper    waits between polls of a terminating owner
     */
    public AdharBatchRunner(Supplier<Collaborators> with, BatchProperties properties, Function<String, String> env,
                            Clock clock, Sleeper sleeper) {
        this.collaborators = SingletonSupplier.of(with);
        this.properties = properties;
        this.env = env;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    private Collaborators with() {
        return collaborators.get();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant start = clock.instant();
        with().stop().runStarted();
        RunOutcome outcome;
        Optional<LocalDate> businessDate = Optional.empty();
        try {
            Attempt attempt = new Attempt();
            try {
                outcome = execute(attempt);
            } finally {
                businessDate = attempt.businessDate;
            }
        } catch (ParameterValidationException ex) {
            log.error("{}", ex.getMessage());
            outcome = RunOutcome.invalid(ex.getReason(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("The run could not start", ex);
            outcome = RunOutcome.failed(ExitCodes.STARTUP_FAILURE, ex.getClass().getName() + ": " + ex.getMessage());
        }
        if (with().stop().isStopping() && outcome.execution() != null
                && outcome.execution().getStatus() == BatchStatus.STOPPED) {
            outcome = RunOutcome.stoppedBySignal(outcome.execution());
        }
        try {
            with().exitCodes().record(outcome);
            log.info("Run of job [{}] ended: {}{} (exit {})", currentJob, outcome.kind().label(),
                    outcome.reason() == null ? "" : " (" + outcome.reason() + ")", outcome.exitCode());
            with().summary().write(outcome, currentContext, businessDate, start, clock.instant());
        } finally {
            with().stop().runFinished();
        }
    }

    private RunOutcome execute(Attempt attempt) {
        if (ResourcelessRepositoryGuard.isResourceless(with().jobRepository())) {
            return RunOutcome.invalid(RunOutcome.RESOURCELESS_REPOSITORY, "The JobRepository is in memory: a "
                    + "Kubernetes run needs the application's database (spring.datasource.*) to be restartable");
        }
        RunContext context = with().contextResolver().resolve();
        currentContext = context;
        currentJob = context.jobName();
        if (context.jobName().isBlank()) {
            return RunOutcome.invalid(RunOutcome.UNKNOWN_JOB, "No job named: set ADHAR_BATCH_JOB (adhar.batch.job)");
        }
        Job job;
        try {
            job = with().jobResolver().resolve(context.jobName());
        } catch (IllegalArgumentException | BeansException ex) {
            return RunOutcome.invalid(RunOutcome.UNKNOWN_JOB, ex.getMessage());
        }
        currentJob = job.getName();
        String command = env.apply(COMMAND);
        if (command != null && !command.isBlank()) {
            if (context.role() == BatchRole.WORKER) {
                return RunOutcome.command(RunOutcome.Kind.SKIPPED, WORKER, ExitCodes.COMPLETED,
                        new RunOutcome.Command(command.strip(), null, null, null, Map.of()),
                        "an ops command is carried out by the manager; this worker has nothing to do");
            }
            return with().commands().run(command, env.apply(EXECUTION_ID), job, context);
        }
        RunOutcome unsupported = unsupported(context);
        if (unsupported != null) {
            return unsupported;
        }
        JobParameterSpec spec = with().specs().find(job.getName())
                .orElseGet(() -> with().specs().spec(context.jobName()));
        if (job.getJobParametersIncrementer() != null && !spec.allowIncrementer()) {
            return RunOutcome.invalid(RunOutcome.INCREMENTER, "Job [" + job.getName() + "] has an incrementer, so "
                    + "Spring Batch would ignore its parameters and no run could be restarted. Remove it, or declare "
                    + "@AdharBatchJob(allowIncrementer = true) for a housekeeping job");
        }
        ResolvedParameters resolved = with().parametersResolver().resolve(spec, input(job.getName()),
                context.scheduledTime(), context.zone());
        attempt.businessDate = resolved.businessDate().or(context::businessDate);
        JobParameters parameters = withProvenance(resolved.parameters(), context);
        RunOutcome holiday = calendar(job, context, attempt.businessDate, parameters);
        if (holiday != null) {
            // Before the worker branch: a worker would otherwise wait for partitions the skipped
            // manager never hands out.
            return holiday;
        }
        if (context.role() == BatchRole.WORKER) {
            // A worker starts nothing: it serves the partitions the manager of the same identity
            // hands out, and the manager's execution decides the run (design 0025 §5.10).
            return with().partitions().consume(job, parameters, context, with().stop()::isStopping);
        }
        RunOutcome outcome = runIdentity(job, parameters, context);
        if (outcome.kind() == RunOutcome.Kind.COMPLETED && outcome.execution() != null && !with().stop().isStopping()) {
            housekeeping(job);
        }
        return outcome;
    }

    /**
     * D4: after a COMPLETED run, the job's metadata the retention no longer keeps goes. A failure
     * is logged and counted ({@code adhar.batch.housekeeping{result=failed}}) and never changes the
     * run's outcome: the run did its work.
     */
    private void housekeeping(Job job) {
        BatchProperties.Retention retention = properties.getRetention();
        if (retention.getDays() == null || with().housekeeping() == null) {
            return;
        }
        try {
            with().housekeeping().clean(job.getName(), job.isRestartable(), retention.getDays(),
                    retention.getKeepLast());
        } catch (RuntimeException ex) {
            log.warn("Housekeeping of job [{}] failed; the run's outcome stands: {}: {}", job.getName(),
                    ex.getClass().getName(), ex.getMessage());
        }
    }

    /**
     * D5: a run the calendar applies to whose business date is not a business day has nothing to
     * do. The business date is judged as it is, a date: the calendar's {@code timeZone} serves the
     * engines that turn instants into dates (Quartz), and the runner's date already is one.
     *
     * @return the skip, or the refusal of an unusable calendar; {@code null} to run
     */
    private RunOutcome calendar(Job job, RunContext context, Optional<LocalDate> businessDate,
                                JobParameters parameters) {
        String file = properties.getCalendar();
        if (file == null || file.isBlank() || !CALENDAR_TRIGGERS.contains(context.trigger())) {
            return null;
        }
        BusinessCalendar calendar;
        try {
            calendar = BusinessCalendarParser.parse(Path.of(file.strip()));
        } catch (InvalidCalendarException | InvalidPathException ex) {
            return RunOutcome.invalid(RunOutcome.CALENDAR_FILE, ex.getMessage()
                    + ": the platform mounts the BatchJob's schedule.calendar at ADHAR_BATCH_CALENDAR");
        }
        if (businessDate.isEmpty()) {
            if (true) return null;
            return RunOutcome.invalid(ParameterValidationException.BUSINESS_DATE, "Job [" + job.getName()
                    + "] runs on calendar [" + calendar.name() + "] but this " + context.trigger().label()
                    + " run has no business date to judge: declare a DATE parameter with source SCHEDULE, or pass "
                    + "businessDate");
        }
        LocalDate date = businessDate.get();
        if (calendar.isBusinessDay(date)) {
            return null;
        }
        return skipped(RunOutcome.CALENDAR, ExitCodes.COMPLETED, null, null, parameters, date + " ("
                + date.getDayOfWeek() + ") is not a business day of calendar [" + calendar.name() + "]");
    }

    /**
     * The version-safe restart guard: a restart must not resume a job that lost a step the
     * identity ran.
     *
     * @return the refusal, exit 2 {@code definition-changed}; {@code null} to restart
     */
    private RunOutcome changedDefinition(Job job, JobInstance instance, RunContext context) {
        Optional<RestartGuard.Change> change = new RestartGuard(with().jobRepository())
                .check(job, instance, context.imageDigest());
        if (change.isEmpty()) {
            return null;
        }
        if (properties.getRestart().getDefinitionCheck() == BatchProperties.Restart.DefinitionCheck.WARN) {
            log.warn("{}. Restarting anyway (adhar.batch.restart.definition-check=warn)", change.get().message());
            return null;
        }
        return new RunOutcome(RunOutcome.Kind.INVALID, RunOutcome.DEFINITION_CHANGED, ExitCodes.INVALID_REQUEST,
                null, instance.getInstanceId(), change.get().message());
    }

    /**
     * @return the refusal of a run this kit cannot carry out, {@code null} when it can
     */
    private RunOutcome unsupported(RunContext context) {
        if (properties.getDistribution() == BatchProperties.Distribution.CHUNKED) {
            return RunOutcome.invalid(RunOutcome.UNSUPPORTED, "adhar.batch.distribution=chunked (remote chunking, "
                    + "ADHAR_BATCH_DISTRIBUTION) is reserved for Phase 4 and not available in this kit: run the job "
                    + "partitioned");
        }
        boolean partitioned = context.role() == BatchRole.MANAGER || context.role() == BatchRole.WORKER;
        BatchProperties.Partition.Transport transport = properties.getPartition().getTransport();
        if (partitioned && transport != BatchProperties.Partition.Transport.JDBC) {
            return RunOutcome.invalid(RunOutcome.UNSUPPORTED, "adhar.batch.partition.transport="
                    + transport.name().toLowerCase(Locale.ROOT) + " (ADHAR_BATCH_PARTITION_TRANSPORT) is a "
                    + "reserved name: jdbc is the only partition transport");
        }
        if (context.role() == BatchRole.WORKER && with().partitions() == null) {
            return RunOutcome.invalid(RunOutcome.UNSUPPORTED, "Role " + context.role().label() + " needs remote "
                    + "partitioning: add org.springframework.batch:spring-batch-integration and "
                    + "org.springframework.integration:spring-integration-jdbc to the application");
        }
        return null;
    }

    private RunOutcome runIdentity(Job job, JobParameters parameters, RunContext context) {
        Instant waitingSince = null;
        while (true) {
            JobInstance instance = with().jobRepository().getJobInstance(job.getName(), parameters);
            JobExecution last = instance == null ? null : with().jobRepository().getLastJobExecution(instance);
            BatchStatus status = last == null ? null : last.getStatus();
            boolean ended = (status == BatchStatus.FAILED || status == BatchStatus.STOPPED) && runningSteps(last) == 0;
            // Ended in the repository is not ended for its owner while it holds the lease: an ops stop
            // is recorded STOPPED, steps included, at once, while the owner still works up to its next
            // chunk boundary (design 0025 §5.9). A restart then would be a second writer.
            if (status == null) {
                return start(job, parameters);
            }
            if (ended && with().lease().find(last.getId()).isEmpty()) {
                return restart(job, parameters, instance, context, () -> { });
            }
            switch (status) {
                case COMPLETED -> {
                    return skipped(RunOutcome.ALREADY_COMPLETE, alreadyCompleteExit(), instance, last, parameters,
                            "this identity already completed in execution " + last.getId());
                }
                case ABANDONED -> {
                    return RunOutcome.abandoned(last);
                }
                case UNKNOWN -> {
                    return new RunOutcome(RunOutcome.Kind.UNKNOWN, null, ExitCodes.UNKNOWN, last, null,
                            "the last execution's state is UNKNOWN: inspect and abandon it before running again");
                }
                default -> {
                    // Still running, or ended with steps that never did: a pod stopped by a signal records
                    // the execution STOPPED at once (JobOperator.stop), and if it is killed before its
                    // steps stop (a gang's dead worker, a grace period too short), they stay STARTED and
                    // Spring Batch refuses every restart as "already running". Both are its owner's to
                    // finish or a recovery's to take over.
                    Verdict verdict = with().recovery().decide(last, context);
                    log.info("Execution {} of this identity is {}{}: {} ({})", last.getId(), status,
                            status.isRunning() ? "" : ended ? " with its owner's lease still held"
                                    : " with " + runningSteps(last) + " step(s) still running",
                            verdict.decision(), verdict.reason());
                    switch (verdict.decision()) {
                        case RECOVER -> {
                            if (ended) {
                                // Only the gone owner's lease was left: the execution restarts as it is.
                                return restart(job, parameters, instance, context,
                                        () -> with().lease().release(last.getId()));
                            }
                            return restart(job, parameters, instance, context, () -> {
                                JobExecution recovered = with().jobOperator().recover(last);
                                with().lease().release(last.getId());
                                with().events().recovered(recovered, verdict.reason());
                            });
                        }
                        case WAIT -> {
                            waitingSince = waitingSince == null ? clock.instant() : waitingSince;
                            Duration waited = Duration.between(waitingSince, clock.instant());
                            if (with().stop().isStopping()) {
                                return RunOutcome.failed(RunOutcome.SIGTERM_STATUS,
                                        "the pod was terminated while waiting for the owner of execution "
                                                + last.getId());
                            }
                            if (waited.compareTo(properties.getRecovery().getMaxWait()) >= 0) {
                                return RunOutcome.failed(ExitCodes.FAILED_RETRYABLE, "execution " + last.getId()
                                        + " is still owned by a terminating pod after " + waited);
                            }
                            sleeper.sleep(WAIT_POLL);
                        }
                        default -> {
                            return skipped(RunOutcome.DUPLICATE, ExitCodes.COMPLETED, instance, last, parameters,
                                    verdict.reason());
                        }
                    }
                }
            }
        }
    }

    /**
     * A restart of the instance: refused before anything changes when the job lost a step the
     * identity ran, otherwise {@code prepare} (recovery, the lease) and the start.
     */
    private RunOutcome restart(Job job, JobParameters parameters, JobInstance instance, RunContext context,
                               Runnable prepare) {
        RunOutcome refused = changedDefinition(job, instance, context);
        if (refused != null) {
            return refused;
        }
        prepare.run();
        return start(job, parameters);
    }

    private static long runningSteps(JobExecution execution) {
        return execution.getStepExecutions().stream().filter(step -> step.getStatus().isRunning()).count();
    }

    private RunOutcome start(Job job, JobParameters parameters) {
        try {
            JobExecution execution = InstanceCreationRetry.start(with().jobOperator(), job, parameters);
            return RunOutcome.of(execution);
        } catch (JobInstanceAlreadyCompleteException ex) {
            return skipped(RunOutcome.ALREADY_COMPLETE, alreadyCompleteExit(), null, null, parameters, ex.getMessage());
        } catch (JobExecutionAlreadyRunningException ex) {
            return skipped(RunOutcome.DUPLICATE, ExitCodes.COMPLETED, null, null, parameters, ex.getMessage());
        } catch (InvalidJobParametersException ex) {
            return RunOutcome.invalid(ParameterValidationException.PARAMETERS, ex.getMessage());
        } catch (JobRestartException ex) {
            return RunOutcome.failed(ExitCodes.FAILED_NOT_RETRYABLE, "the identity cannot be restarted: "
                    + ex.getMessage());
        }
    }

    private RunOutcome skipped(String reason, int exitCode, JobInstance instance, JobExecution found,
                               JobParameters parameters, String message) {
        Long instanceId = instance != null ? instance.getInstanceId() : null;
        with().events().skipped(currentJob, instanceId, found == null ? null : found.getId(), parameters, reason);
        return RunOutcome.skipped(reason, exitCode, instanceId, found, message);
    }

    private int alreadyCompleteExit() {
        return properties.getExit().getAlreadyComplete() == BatchProperties.Exit.AlreadyComplete.FAIL
                ? ExitCodes.FAILED_NOT_RETRYABLE : ExitCodes.COMPLETED;
    }

    private Map<String, String> input(String jobName) {
        String text = env.apply("ADHAR_BATCH_PARAMS");
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException ex) {
            throw new ParameterValidationException(jobName, List.of("ADHAR_BATCH_PARAMS is not JSON: "
                    + ex.getMessage()), ParameterValidationException.PARAMETERS);
        }
        if (!(parsed instanceof Map<?, ?> object)) {
            throw new ParameterValidationException(jobName, List.of("ADHAR_BATCH_PARAMS must be a JSON object of "
                    + "parameter names to values"), ParameterValidationException.PARAMETERS);
        }
        Map<String, String> input = new LinkedHashMap<>();
        object.forEach((name, value) -> {
            if (value instanceof Map || value instanceof List) {
                throw new ParameterValidationException(jobName, List.of(name + " must be a single value"),
                        ParameterValidationException.PARAMETERS);
            }
            if (value != null) {
                input.put((String) name, value instanceof BigDecimal number ? number.toPlainString()
                        : String.valueOf(value));
            }
        });
        return input;
    }

    private static JobParameters withProvenance(JobParameters declared, RunContext context) {
        JobParametersBuilder builder = new JobParametersBuilder(declared)
                .addString(TRIGGER, context.trigger().label(), false)
                .addLong(ATTEMPT, (long) context.attempt(), false);
        if (!context.actor().isBlank()) {
            builder.addString(ACTOR, context.actor(), false);
        }
        if (!context.k8sJobName().isBlank()) {
            builder.addString(K8S_JOB, context.k8sJobName(), false);
        }
        if (!context.imageDigest().isBlank()) {
            builder.addString(APP_VERSION, context.imageDigest(), false);
        }
        return builder.toJobParameters();
    }

    // -------------------------------------------------------------------------------------------
    // The live execution: its lease and the graceful stop, from the kit's job listener
    // -------------------------------------------------------------------------------------------

    @Override
    public void jobStarted(JobExecution execution) {
        RunContext context = currentContext;
        if (context == null || !execution.getJobInstance().getJobName().equals(currentJob)) {
            return;
        }
        Owner owner = Owner.of(context);
        with().lease().acquire(execution.getId(), owner);
        Running run = new Running(execution, owner);
        running.set(run);
        run.heartbeat = with().heartbeats().scheduleWithFixedDelay(() -> heartbeat(run),
                properties.getRecovery().getHeartbeatInterval().toMillis(),
                properties.getRecovery().getHeartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
        with().stop().executionStarted(execution);
    }

    @Override
    public void chunkCommitted(StepExecution execution, long items) {
        Running run = running.get();
        if (run != null && run.execution.getId() == execution.getJobExecutionId()) {
            run.lastCommit = clock.instant();
        }
    }

    @Override
    public void jobFinished(JobExecution execution) {
        Running run = running.get();
        if (run == null || run.execution.getId() != execution.getId()) {
            return;
        }
        running.set(null);
        run.heartbeat.cancel(false);
        with().stop().executionEnded(execution);
        try {
            with().lease().release(execution.getId());
        } catch (RuntimeException ex) {
            log.warn("Could not release the lease of execution {}: {}", execution.getId(), ex.getMessage());
        }
    }

    private void heartbeat(Running run) {
        try {
            if (!with().lease().heartbeat(run.execution.getId(), run.owner, run.lastCommit)) {
                log.error("Execution {} is no longer this pod's: another run recovered it. Stopping; its next "
                        + "commit would be refused anyway", run.execution.getId());
                run.heartbeat.cancel(false);
                with().jobOperator().stop(run.execution);
            }
        } catch (Exception ex) {
            log.warn("Could not renew the lease of execution {}: {}", run.execution.getId(), ex.getMessage());
        }
    }

    /**
     * What the runner works with(). {@code partitions} serves a worker's partitions; it is
     * {@code null} when the application has no remote partitioning on its classpath.
     * {@code commands} carries out an ops run; {@code housekeeping} is the D4 retention after a
     * COMPLETED run, {@code null} for none.
     */
    public record Collaborators(JobRepository jobRepository, JobOperator jobOperator, JobResolver jobResolver,
                                JobParameterSpecRegistry specs, JobParametersResolver parametersResolver,
                                RunContextResolver contextResolver, RecoveryPolicy recovery, ExecutionLease lease,
                                GracefulStopLifecycle stop, TerminationSummaryWriter summary,
                                BatchExitCodeGenerator exitCodes, BatchRunEventPublisher events,
                                ScheduledExecutorService heartbeats, PartitionConsumer partitions,
                                BatchCommandRunner commands, RepositoryHousekeeping housekeeping) {

        /**
         * @return a single daemon thread for lease heartbeats
         */
        public static ScheduledExecutorService heartbeatThread() {
            return Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "adhar-batch-lease");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /** Waits; a test replaces the clock and the sleep. */
    @FunctionalInterface
    public interface Sleeper {
        /**
         * @param duration how long
         */
        void sleep(Duration duration);

        /**
         * @return {@link Thread#sleep(Duration)}, interrupted by a shutdown
         */
        static Sleeper threadSleep() {
            return duration -> {
                try {
                    Thread.sleep(duration);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting", ex);
                }
            };
        }
    }

    /** What one run has learned so far, for the summary. */
    private static final class Attempt {
        private Optional<LocalDate> businessDate = Optional.empty();
    }

    /** The execution the runner started, while it runs. */
    private static final class Running {
        private final JobExecution execution;
        private final Owner owner;
        private volatile Instant lastCommit;
        private volatile ScheduledFuture<?> heartbeat;

        Running(JobExecution execution, Owner owner) {
            this.execution = execution;
            this.owner = owner;
        }
    }
}
