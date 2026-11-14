package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.listener.BatchRunObserver;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * params  ADHAR_BATCH_PARAMS &gt; schedule &gt; defaults, validated  else exit 2 (parameters, business-date)
 * last execution of the identity:
 *   none, FAILED, STOPPED  start (a restart when the instance exists)
 *   COMPLETED              exit 0 skipped already-complete (or 3: adhar.batch.exit.already-complete=fail)
 *   ABANDONED              exit 6
 *   STARTING/STARTED/STOPPING  RecoveryPolicy: recover then start | wait for a terminating owner | exit 0 skipped duplicate
 * during  the execution's lease is renewed; a SIGTERM stops it at a chunk boundary (GracefulStopLifecycle)
 * after   ExitCodes.of(execution); TerminationSummaryWriter; BatchExitCodeGenerator
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
        if (context.role() == BatchRole.WORKER) {
            return RunOutcome.invalid(ParameterValidationException.PARAMETERS, "Role worker needs remote "
                    + "partitioning, which this kit does not provide yet: run the job as single");
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
        return runIdentity(job, parameters, context);
    }

    private RunOutcome runIdentity(Job job, JobParameters parameters, RunContext context) {
        Instant waitingSince = null;
        while (true) {
            JobInstance instance = with().jobRepository().getJobInstance(job.getName(), parameters);
            JobExecution last = instance == null ? null : with().jobRepository().getLastJobExecution(instance);
            BatchStatus status = last == null ? null : last.getStatus();
            if (status == null || status == BatchStatus.FAILED || status == BatchStatus.STOPPED) {
                return start(job, parameters);
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
                    Verdict verdict = with().recovery().decide(last, context);
                    log.info("Execution {} of this identity is {}: {} ({})", last.getId(), status,
                            verdict.decision(), verdict.reason());
                    switch (verdict.decision()) {
                        case RECOVER -> {
                            JobExecution recovered = with().jobOperator().recover(last);
                            with().lease().release(last.getId());
                            with().events().recovered(recovered, verdict.reason());
                            return start(job, parameters);
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

    /** What the runner works with(). */
    public record Collaborators(JobRepository jobRepository, JobOperator jobOperator, JobResolver jobResolver,
                                JobParameterSpecRegistry specs, JobParametersResolver parametersResolver,
                                RunContextResolver contextResolver, RecoveryPolicy recovery, ExecutionLease lease,
                                GracefulStopLifecycle stop, TerminationSummaryWriter summary,
                                BatchExitCodeGenerator exitCodes, BatchRunEventPublisher events,
                                ScheduledExecutorService heartbeats) {

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
