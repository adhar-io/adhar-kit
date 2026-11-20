package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.scheduler.SlotIdentity;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Triggers a service creates while it runs (design 0025 §5.20.5 rules 5-8), on the engine
 * {@code quartz} only: a request, a message or a received file starts a run. They are durable
 * one-shot triggers in the group {@value QuartzSchedulerEngine#RUNTIME_GROUP}, keyed by the run's
 * name (naming v1), so asking twice for the same identity stores one trigger and yields one run:
 *
 * <ol>
 *   <li>The parameters are resolved and validated now, exactly as the launch will resolve them; an
 *       invalid request throws {@link ParameterValidationException} to the caller, as exit 2 would.</li>
 *   <li>The identity's last execution COMPLETED: nothing is stored, the run is {@link Status#COMPLETED}.</li>
 *   <li>A trigger keyed by the run name is stored already, or the identity is running: nothing new
 *       is stored, the run is {@link Status#PENDING}.</li>
 *   <li>Otherwise a trigger keyed by the run name is stored: {@link Status#SCHEDULED}.</li>
 * </ol>
 *
 * <p>A {@link #fireNow} identity is the caller's parameters and nothing else: no
 * {@code adhar.scheduled-time}, no scheduled business date (a {@code @JobParam(source = SCHEDULE)}
 * is passed explicitly), and a job whose identity would be empty is refused, because its only
 * JobInstance would complete once and every later call would answer {@code COMPLETED}; such a job
 * runs with {@link #fireAt}, whose identity is the requested instant (whole seconds, in
 * {@code adhar.batch.scheduler.time-zone}). A runtime trigger always fires, however late: it stands
 * for an event.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchTriggers {

    private final QuartzSchedulerEngine engine;

    /**
     * @param engine the engine {@code quartz}
     */
    public BatchTriggers(QuartzSchedulerEngine engine) {
        this.engine = engine;
    }

    /**
     * Runs a job as soon as a replica is free.
     *
     * @param job        the job's bean name or {@code Job#getName()}
     * @param parameters values for its declared INPUT parameters
     * @return the run's name and what happened
     * @throws ParameterValidationException when the parameters do not resolve, a schedule-sourced
     *                                      parameter is missing, or the identity would be empty
     * @throws IllegalArgumentException     for an unknown job, or one that cannot be scheduled
     */
    public Result fireNow(String job, Map<String, String> parameters) {
        return fire(job, null, parameters);
    }

    /**
     * Runs a job at an instant, with that instant as the run's nominal time.
     *
     * @param job        the job's bean name or {@code Job#getName()}
     * @param at         when; truncated to the whole second. A past instant fires at once.
     * @param parameters values for its declared INPUT parameters
     * @return the run's name and what happened
     * @throws ParameterValidationException when the parameters do not resolve
     * @throws IllegalArgumentException     for an unknown job, or one that cannot be scheduled
     */
    public Result fireAt(String job, Instant at, Map<String, String> parameters) {
        if (at == null) {
            throw new IllegalArgumentException("fireAt needs an instant; fireNow runs a job now");
        }
        return fire(job, at.truncatedTo(ChronoUnit.SECONDS), parameters);
    }

    /**
     * Removes a runtime trigger that has not fired yet. A run already in progress is not stopped.
     *
     * @param runName the run's name, as {@link Result#runName()} gave it
     * @return whether a trigger was removed
     */
    public boolean cancel(String runName) {
        try {
            boolean removed = engine.scheduler().unscheduleJob(new TriggerKey(runName, QuartzSchedulerEngine.RUNTIME_GROUP));
            if (removed) {
                log.info("Cancelled runtime trigger {}", runName);
                engine.triggersChanged();
            }
            return removed;
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not cancel runtime trigger " + runName + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * @return the runtime triggers waiting to fire (including a shutdown's resume triggers), by run name
     */
    public List<Pending> list() {
        List<Pending> pending = new ArrayList<>();
        try {
            for (TriggerKey key : engine.scheduler().getTriggerKeys(
                    GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.RUNTIME_GROUP))) {
                Trigger trigger = engine.scheduler().getTrigger(key);
                if (trigger == null) {
                    continue;
                }
                JobDataMap data = trigger.getJobDataMap();
                String bean = engine.scheduler().getJobDetail(trigger.getJobKey()) != null
                        ? engine.scheduler().getJobDetail(trigger.getJobKey()).getJobDataMap()
                        .getString(QuartzSchedulerEngine.JOB) : null;
                String nominal = data.getString(QuartzSchedulerEngine.NOMINAL);
                pending.add(new Pending(key.getName(), bean, data.getString(QuartzSchedulerEngine.KIND),
                        instant(trigger.getNextFireTime()), nominal != null ? Instant.parse(nominal) : null,
                        QuartzSchedulerEngine.fromJson(data.getString(QuartzSchedulerEngine.PARAMS))));
            }
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not list the runtime triggers: " + ex.getMessage(), ex);
        }
        pending.sort(Comparator.comparing(Pending::runName));
        return pending;
    }

    private Result fire(String jobName, Instant nominal, Map<String, String> parameters) {
        String bean = engine.beanNameOf(jobName);
        Job job = engine.schedulableJob(bean);
        ZoneId zone = engine.properties().getScheduler().getTimeZone();
        Map<String, String> input = parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
        JobParameterSpec spec = engine.spec(job);
        SlotIdentity identity = SlotIdentity.resolve(engine.resolver(), spec, input, nominal, zone);
        if (nominal == null && identity.identifying().isEmpty()) {
            throw new ParameterValidationException(job.getName(), List.of("fireNow would run job " + job.getName()
                    + " with an empty identity, whose one JobInstance completes once and never runs again: give it "
                    + "an identifying parameter, or run it with fireAt, whose identity is the instant"),
                    ParameterValidationException.PARAMETERS);
        }
        String runName = identity.runName(engine.scheduleNameOf(bean));
        Optional<JobExecution> last = engine.operator().findLastExecution(job.getName(), identity.parameters());
        if (last.isPresent() && last.get().getStatus() == BatchStatus.COMPLETED) {
            return new Result(runName, Status.COMPLETED, last.get().getId());
        }
        if (last.isPresent() && last.get().getStatus().isRunning()) {
            return new Result(runName, Status.PENDING, last.get().getId());
        }
        TriggerKey key = new TriggerKey(runName, QuartzSchedulerEngine.RUNTIME_GROUP);
        try {
            if (engine.scheduler().checkExists(key)) {
                return new Result(runName, Status.PENDING, null);
            }
            Map<String, String> data = new LinkedHashMap<>();
            data.put(QuartzSchedulerEngine.KIND, nominal == null ? QuartzSchedulerEngine.KIND_NOW
                    : QuartzSchedulerEngine.KIND_AT);
            data.put(QuartzSchedulerEngine.PARAMS, QuartzSchedulerEngine.toJson(input));
            data.put(QuartzSchedulerEngine.ZONE, zone.getId());
            data.put(QuartzSchedulerEngine.RUN, runName);
            if (nominal != null) {
                data.put(QuartzSchedulerEngine.NOMINAL, nominal.toString());
            }
            Instant fireTime = nominal != null ? nominal : engine.clock().instant();
            engine.scheduler().scheduleJob(QuartzSchedulerEngine.oneShot(key, engine.jobKey(bean), data, fireTime));
        } catch (ObjectAlreadyExistsException ex) {
            return new Result(runName, Status.PENDING, null);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not store the runtime trigger of run " + runName + ": "
                    + ex.getMessage(), ex);
        }
        log.info("Stored runtime trigger {} for job [{}]{}", runName, bean,
                nominal != null ? " at " + nominal : " to run now");
        engine.triggersChanged();
        return new Result(runName, Status.SCHEDULED, null);
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /**
     * What a runtime trigger call did.
     */
    public enum Status {
        /** A trigger was stored; the run starts when it fires. */
        SCHEDULED,
        /** A trigger for this run was stored already, or the run is in progress: nothing new was stored. */
        PENDING,
        /** The run already completed: nothing was stored. */
        COMPLETED
    }

    /**
     * @param runName     the run's name (naming v1), the trigger's key
     * @param status      what the call did
     * @param executionId the execution found, for {@link Status#COMPLETED} and a running one
     */
    public record Result(String runName, Status status, Long executionId) {
    }

    /**
     * A runtime trigger waiting to fire.
     *
     * @param runName    the run's name, the trigger's key
     * @param job        the job's bean name
     * @param kind       {@code now}, {@code at} or {@code resume}
     * @param nextFire   when it fires
     * @param nominal    a {@code fireAt}'s requested instant, else {@code null}
     * @param parameters the parameters it was given
     */
    public record Pending(String runName, String job, String kind, Instant nextFire, Instant nominal,
                          Map<String, String> parameters) {
    }
}
