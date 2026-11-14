package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties.Quartz.Misfire;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.SlotIdentity;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Calendar;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Quartz job of every in-service Spring Batch job (design 0025 §5.20.5): one fire, one launch,
 * synchronous on a Quartz worker thread. {@link DisallowConcurrentExecution} is cluster-wide in a
 * JDBC store, so one Spring Batch job never runs twice at once across the replicas.
 *
 * <pre>
 * kind     = config | now | at | resume
 * resume:    params = the STOPPED execution's identifying parameters, verbatim (rule 8)
 * otherwise: nominal = config ? slotOf(fire) : at ? adhar.nominal : none
 *            params  = SlotIdentity.of(spec, resolve(spec, adhar.params, nominal, zone), nominal)
 * params  += provenance (non-identifying): adhar.trigger.key, adhar.run, adhar.app.version, adhar.scheduler.instance
 * last     = the identity's last execution
 *   none, FAILED, STOPPED         -> start (a restart when the instance exists; recover first if a step still runs)
 *   COMPLETED                     -> skipped(already-complete)
 *   ABANDONED                     -> failed(abandoned): a new identity is needed
 *   STARTING, STARTED, STOPPING   -> recovering fire, or owner with no fresh check-in: recover, then start (recovered)
 *                                    otherwise skipped(duplicate)
 * </pre>
 *
 * <p>An interval trigger whose {@code RESCHEDULE_NOW} misfire moved it off the kit's grid is
 * re-anchored on the first grid slot after now before the run starts. Under misfire policy
 * {@code all}, a slot older than the {@code adhar.batch.quartz.max-catch-up} most recent ones is
 * skipped and counted. The launch never throws: Quartz would refire or unschedule on a
 * {@code JobExecutionException}, and the JobRepository already records what happened.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
@DisallowConcurrentExecution
public class BatchLaunchJob implements org.quartz.Job {

    /** How often a run started on an asynchronous task executor is polled for its end. */
    static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final QuartzSchedulerEngine engine;

    /**
     * @param engine the engine; Spring Boot's job factory autowires it
     */
    public BatchLaunchJob(QuartzSchedulerEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(JobExecutionContext context) {
        try {
            launch(context);
        } catch (RuntimeException ex) {
            log.error("Scheduled run of {} failed: {}", context.getTrigger().getKey(), ex.getMessage(), ex);
        }
    }

    /**
     * @param context the fire
     * @return what it did
     */
    SlotOutcome launch(JobExecutionContext context) {
        JobDataMap data = context.getMergedJobDataMap();
        String bean = context.getJobDetail().getJobDataMap().getString(QuartzSchedulerEngine.JOB);
        Job job = bean != null ? engine.job(bean) : null;
        if (job == null) {
            log.error("Quartz job {} names Spring Batch job [{}], which this application does not define",
                    context.getJobDetail().getKey(), bean);
            return SlotOutcome.FAILED_TO_START;
        }
        String kind = data.getString(QuartzSchedulerEngine.KIND);
        ZoneId zone = zone(data);
        TriggerKey triggerKey = context.isRecovering() ? context.getRecoveringTriggerKey() : context.getTrigger().getKey();
        String scheduleName = engine.scheduleNameOf(bean);

        JobParameters identity;
        String runName;
        if (QuartzSchedulerEngine.KIND_RESUME.equals(kind)) {
            identity = QuartzSchedulerEngine.decodeIdentity(data.getString(QuartzSchedulerEngine.IDENTITY));
            runName = data.getString(QuartzSchedulerEngine.RUN);
            identity = new JobParametersBuilder(identity)
                    .addString(BatchScheduler.TRIGGER_PARAMETER, BatchScheduler.EMBEDDED_TRIGGER, false)
                    .toJobParameters();
        } else {
            Instant nominal;
            if (QuartzSchedulerEngine.KIND_CONFIG.equals(kind)) {
                Optional<Instant> slot = slotOf(context);
                if (slot.isEmpty()) {
                    return SlotOutcome.BEYOND_CATCH_UP;
                }
                nominal = slot.get();
            } else if (QuartzSchedulerEngine.KIND_AT.equals(kind)) {
                nominal = Instant.parse(data.getString(QuartzSchedulerEngine.NOMINAL));
            } else if (QuartzSchedulerEngine.KIND_NOW.equals(kind)) {
                nominal = null;
            } else {
                log.error("Trigger {} has no kind the kit knows ({}={})", triggerKey, QuartzSchedulerEngine.KIND, kind);
                return SlotOutcome.FAILED_TO_START;
            }
            SlotIdentity slot;
            try {
                slot = SlotIdentity.resolve(engine.resolver(), engine.spec(job),
                        QuartzSchedulerEngine.fromJson(data.getString(QuartzSchedulerEngine.PARAMS)), nominal, zone);
            } catch (ParameterValidationException ex) {
                log.error("Scheduled run of job [{}] by {} could not start: {}", bean, triggerKey, ex.getMessage());
                return SlotOutcome.FAILED_TO_START;
            }
            identity = slot.parameters();
            runName = slot.runName(scheduleName);
        }
        JobParameters parameters = withProvenance(identity, triggerKey, runName);
        return run(context, bean, job, parameters, runName, zone);
    }

    /**
     * The slot a configured trigger's fire stands for, after re-anchoring an interval trigger and
     * applying {@code max-catch-up}; empty when the slot is beyond it.
     */
    private Optional<Instant> slotOf(JobExecutionContext context) {
        Scheduler scheduler = engine.scheduler();
        Trigger trigger = context.getTrigger();
        Calendar calendar = context.getCalendar();
        Instant scheduled = context.getScheduledFireTime().toInstant();
        if (context.isRecovering()) {
            JobDataMap data = context.getMergedJobDataMap();
            scheduled = Instant.ofEpochMilli(Long.parseLong(
                    data.getString(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS)));
            try {
                Trigger original = scheduler.getTrigger(context.getRecoveringTriggerKey());
                if (original != null) {
                    trigger = original;
                    calendar = original.getCalendarName() != null ? scheduler.getCalendar(original.getCalendarName()) : null;
                }
            } catch (SchedulerException ex) {
                log.warn("Could not read the original trigger {} of a recovery fire: {}",
                        context.getRecoveringTriggerKey(), ex.getMessage());
            }
            return Optional.of(SlotResolver.slotOf(trigger, calendar, scheduled));
        }
        Instant slot = SlotResolver.slotOf(trigger, calendar, scheduled);
        Instant now = engine.clock().instant();
        if (!SlotResolver.onGrid(trigger)) {
            reanchor(trigger, now);
        }
        Misfire policy = misfire(trigger.getJobDataMap());
        if (policy == Misfire.ALL) {
            int limit = engine.properties().getQuartz().getMaxCatchUp();
            if (SlotResolver.newerSlots(trigger, calendar, slot, now, limit) >= limit) {
                engine.countBeyondCatchUp();
                log.warn("Skipping slot {} of {}: it is older than the {} most recent missed slots "
                        + "(adhar.batch.quartz.max-catch-up)", slot, trigger.getKey(), limit);
                return Optional.empty();
            }
        }
        return Optional.of(slot);
    }

    /**
     * Puts an interval trigger back on the kit's grid after a {@code RESCHEDULE_NOW} misfire moved
     * its {@code startTime} to the handling time: the next fire is the first grid slot after now.
     */
    private void reanchor(Trigger trigger, Instant now) {
        Instant next = SlotResolver.reanchored(trigger, now);
        Trigger anchored = TriggerBuilder.newTrigger()
                .withIdentity(trigger.getKey())
                .forJob(trigger.getJobKey())
                .withPriority(trigger.getPriority())
                .usingJobData(trigger.getJobDataMap())
                .modifiedByCalendar(trigger.getCalendarName())
                .withDescription(trigger.getDescription())
                .startAt(Date.from(next))
                .withSchedule(trigger.getScheduleBuilder())
                .build();
        try {
            engine.scheduler().rescheduleJob(trigger.getKey(), anchored);
            log.info("Re-anchored interval trigger {} on its grid: next fire {}", trigger.getKey(), next);
        } catch (SchedulerException ex) {
            log.warn("Could not re-anchor interval trigger {} on its grid: {}", trigger.getKey(), ex.getMessage());
        }
    }

    private SlotOutcome run(JobExecutionContext context, String bean, Job job, JobParameters parameters,
                            String runName, ZoneId zone) {
        QuartzSchedulerEngine.LocalRun local = new QuartzSchedulerEngine.LocalRun(bean, job.getName(), runName, zone);
        ExecutionLease.Owner owner = new ExecutionLease.Owner(engine.instanceId(), "", "");
        SlotOutcome outcome = SlotOutcome.RAN;
        engine.started(local);
        engine.ownership().begin(local, owner);
        try {
            Optional<JobExecution> last = engine.operator().findLastExecution(job.getName(), parameters);
            if (last.isPresent()) {
                JobExecution previous = last.get();
                BatchStatus status = previous.getStatus();
                if (status == BatchStatus.COMPLETED) {
                    log.info("Skipping run {} of job [{}]: it already completed (execution {})", runName, bean,
                            previous.getId());
                    return SlotOutcome.ALREADY_RAN;
                }
                if (status == BatchStatus.ABANDONED) {
                    log.error("Run {} of job [{}] cannot start: its last execution {} was ABANDONED, so only a new "
                            + "identity can run", runName, bean, previous.getId());
                    return SlotOutcome.ABANDONED;
                }
                if (status.isRunning()) {
                    String ownerInstance = owner(previous);
                    boolean ownerAlive = ownerInstance != null ? engine.instanceAlive(ownerInstance)
                            : updatedWithin(previous, engine.properties().getRecovery().getStaleAfter());
                    if (!context.isRecovering() && ownerAlive) {
                        log.info("Skipping run {} of job [{}]: execution {} is running on {}", runName, bean,
                                previous.getId(), ownerInstance != null ? ownerInstance : "a replica");
                        return SlotOutcome.DUPLICATE;
                    }
                    log.warn("Recovering execution {} of run {} of job [{}]: {}", previous.getId(), runName, bean,
                            context.isRecovering() ? "Quartz re-fired the work of a replica that died"
                                    : "its owner " + ownerInstance + " no longer checks in");
                    recover(previous);
                    outcome = SlotOutcome.RECOVERED;
                } else if (hasRunningStep(engine.repository().getJobExecution(previous.getId()))) {
                    log.warn("Recovering execution {} of run {} of job [{}]: it ended {} with a step still running",
                            previous.getId(), runName, bean, status);
                    recover(previous);
                }
            }
            JobExecution execution = engine.operator().start(job, parameters);
            engine.ownership().claim(execution);
            execution = awaitEnd(execution);
            BatchStatus status = execution.getStatus();
            if (status == BatchStatus.COMPLETED) {
                log.info("Scheduled run {} of job [{}] completed (execution {})", runName, bean, execution.getId());
            } else {
                log.warn("Scheduled run {} of job [{}] ended {} (execution {}, exit code {})", runName, bean, status,
                        execution.getId(), execution.getExitStatus().getExitCode());
            }
            if (status == BatchStatus.STOPPED && engine.stopping()) {
                engine.requeue(local, execution);
            }
            return outcome;
        } catch (JobInstanceAlreadyCompleteException ex) {
            log.info("Skipping run {} of job [{}]: another replica completed it", runName, bean);
            return SlotOutcome.ALREADY_RAN;
        } catch (JobExecutionAlreadyRunningException | DataIntegrityViolationException
                 | ConcurrencyFailureException ex) {
            log.info("Skipping run {} of job [{}]: another replica runs it", runName, bean);
            return SlotOutcome.DUPLICATE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for run {} of job [{}] to end", runName, bean);
            return outcome;
        } catch (Exception ex) {
            log.error("Scheduled run {} of job [{}] could not start: {}", runName, bean, ex.getMessage(), ex);
            return SlotOutcome.FAILED_TO_START;
        } finally {
            engine.ownership().end();
            if (local.executionId != null) {
                engine.ownership().release(local.executionId);
            }
            engine.ended(local);
        }
    }

    private void recover(JobExecution previous) {
        engine.operator().recover(previous);
        engine.ownership().release(previous.getId());
    }

    private String owner(JobExecution execution) {
        return engine.ownership().owner(execution.getId());
    }

    private boolean updatedWithin(JobExecution execution, Duration staleAfter) {
        var updated = execution.getLastUpdated() != null ? execution.getLastUpdated() : execution.getStartTime();
        if (updated == null) {
            return true;
        }
        Instant at = updated.atZone(ZoneId.systemDefault()).toInstant();
        return Duration.between(at, engine.clock().instant()).compareTo(staleAfter) <= 0;
    }

    private static boolean hasRunningStep(JobExecution execution) {
        if (execution == null) {
            return false;
        }
        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus().isRunning()) {
                return true;
            }
        }
        return false;
    }

    private JobExecution awaitEnd(JobExecution execution) throws InterruptedException {
        JobExecution current = execution;
        while (current.isRunning()) {
            Thread.sleep(POLL_INTERVAL.toMillis());
            JobExecution refreshed = engine.repository().getJobExecution(current.getId());
            if (refreshed == null) {
                return current;
            }
            current = refreshed;
        }
        return current;
    }

    private JobParameters withProvenance(JobParameters identity, TriggerKey trigger, String runName) {
        JobParametersBuilder builder = new JobParametersBuilder(identity)
                .addString(QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER, trigger.getGroup() + "." + trigger.getName(),
                        false)
                .addString(QuartzSchedulerEngine.RUN_PARAMETER, runName, false)
                .addString(QuartzSchedulerEngine.INSTANCE_PARAMETER, engine.instanceId(), false);
        if (engine.appVersion() != null && !engine.appVersion().isBlank()) {
            builder.addString(QuartzSchedulerEngine.APP_VERSION_PARAMETER, engine.appVersion(), false);
        }
        return builder.toJobParameters();
    }

    private ZoneId zone(JobDataMap data) {
        String zone = data.getString(QuartzSchedulerEngine.ZONE);
        return zone != null ? ZoneId.of(zone) : engine.properties().getScheduler().getTimeZone();
    }

    private Misfire misfire(JobDataMap data) {
        String misfire = data.getString(QuartzSchedulerEngine.MISFIRE);
        Map<String, Misfire> names = new LinkedHashMap<>();
        for (Misfire value : Misfire.values()) {
            names.put(value.name().toLowerCase(java.util.Locale.ROOT), value);
        }
        return misfire != null && names.containsKey(misfire) ? names.get(misfire)
                : engine.properties().getQuartz().getMisfire();
    }
}
