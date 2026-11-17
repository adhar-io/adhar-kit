package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.config.BatchProperties.Quartz.Misfire;
import com.adhar.kit.batch.config.BatchProperties.Quartz.TriggerSettings;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.params.ParameterValidationException;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.runtime.Json;
import com.adhar.kit.batch.scheduler.SchedulerEngine;
import com.adhar.kit.batch.scheduler.SlotIdentity;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TimeOfDay;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * The engine {@code quartz} of {@link com.adhar.kit.batch.scheduler.BatchScheduler} (design 0025
 * §5.20, D24): a clustered Quartz JDBC job store in the batch schema, shared by every replica of
 * the service, so a slot runs exactly once across them, a replica dying mid-run is re-fired on
 * another one, and each trigger has a misfire policy.
 *
 * <ul>
 *   <li><b>One durable JobDetail per Spring Batch job</b>: key {@code <scheduleName>} in group
 *       {@value #JOB_GROUP}, class {@link BatchLaunchJob} ({@code @DisallowConcurrentExecution},
 *       cluster-wide in a JDBC store), {@code requestsRecovery}, data {@code {job: <bean name>}}.</li>
 *   <li><b>Two trigger groups.</b> {@value #CONFIG_GROUP} holds the configured triggers, named
 *       {@code <scheduleName>.<name>} and reconciled at every start ({@link #afterSingletonsInstantiated}):
 *       added, replaced when their definition changed, removed when no longer configured, and left
 *       alone otherwise, so a misfire waiting in the store survives the restart that handles it.
 *       {@value #RUNTIME_GROUP} holds triggers created while the service runs (runtime calls, and the
 *       resume triggers of {@link #stop()}); they persist and the reconcile never touches them.</li>
 *   <li><b>Shutdown</b>: standby, so this replica acquires nothing new; stop this replica's runs at
 *       their next chunk boundary and wait up to {@code adhar.batch.shutdown.timeout}; each run that
 *       recorded STOPPED stores a one-shot resume trigger carrying its identity verbatim, so another
 *       replica resumes it within one acquisition cycle. A run that did not stop in time is left to
 *       Quartz's recovery on another replica, as if this one had died.</li>
 * </ul>
 *
 * <p>The launch itself is {@link BatchLaunchJob}; which slot a fire stands for is
 * {@link SlotResolver}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class QuartzSchedulerEngine implements SchedulerEngine, SmartInitializingSingleton {

    /** The engine's name, as {@code adhar.batch.scheduler.engine} spells it. */
    public static final String NAME = "quartz";

    /** The JobDetails' group (reserved). */
    public static final String JOB_GROUP = "adhar-batch";

    /** The configured triggers' group (reserved). */
    public static final String CONFIG_GROUP = "adhar-batch-config";

    /** The runtime and resume triggers' group (reserved). */
    public static final String RUNTIME_GROUP = "adhar-batch-runtime";

    /** JobDetail data: the Spring Batch job's bean name. */
    public static final String JOB = "job";

    /** Trigger data: {@value #KIND_CONFIG}, {@value #KIND_NOW}, {@value #KIND_AT} or {@value #KIND_RESUME}. */
    public static final String KIND = "adhar.kind";

    /** {@value #KIND} of a configured trigger. */
    public static final String KIND_CONFIG = "config";

    /** {@value #KIND} of a runtime trigger without a nominal time. */
    public static final String KIND_NOW = "now";

    /** {@value #KIND} of a runtime trigger for a requested instant. */
    public static final String KIND_AT = "at";

    /** {@value #KIND} of a shutdown's resume trigger. */
    public static final String KIND_RESUME = "resume";

    /** Trigger data: JSON object of INPUT parameter values. */
    public static final String PARAMS = "adhar.params";

    /** Trigger data of a {@value #KIND_AT} trigger: the requested instant, ISO-8601. */
    public static final String NOMINAL = "adhar.nominal";

    /** Trigger data of a {@value #KIND_RESUME} trigger: the identifying parameters, verbatim. */
    public static final String IDENTITY = "adhar.identity";

    /** Trigger data of a {@value #KIND_RESUME} trigger: the run's name. */
    public static final String RUN = "adhar.run";

    /** Trigger data of an interval trigger: the grid's anchor, ISO-8601. */
    public static final String GRID_START = "adhar.grid.start";

    /** Trigger data of an interval trigger: the grid's period, ISO-8601. */
    public static final String GRID_EVERY = "adhar.grid.every";

    /** Trigger data: the trigger's time zone. */
    public static final String ZONE = "adhar.zone";

    /** Trigger data of a configured trigger: its misfire policy. */
    public static final String MISFIRE = "adhar.misfire";

    /** Trigger data of a configured trigger: its definition, compared by the reconcile. */
    static final String DEFINITION = "adhar.definition";

    /** Provenance parameter: the trigger, {@code <group>.<name>}. */
    public static final String TRIGGER_KEY_PARAMETER = "adhar.trigger.key";

    /** Provenance parameter: the run's name (naming v1). */
    public static final String RUN_PARAMETER = "adhar.run";

    /** Provenance parameter: the application's version, when known. */
    public static final String APP_VERSION_PARAMETER = "adhar.app.version";

    /** Provenance parameter: the Quartz instance that ran it. */
    public static final String INSTANCE_PARAMETER = "adhar.scheduler.instance";

    /** The name of the trigger an {@code adhar.batch.scheduler.jobs} entry becomes. */
    public static final String SCHEDULE_TRIGGER = "schedule";

    /** Quartz's tolerance before a replica that missed its check-in is called dead. */
    static final Duration CHECKIN_TOLERANCE = Duration.ofMillis(7500);

    /** The longest schedule name: the {@code <batchjob>} of naming v1. */
    static final int MAX_SCHEDULE_NAME = 40;

    private static final Pattern DNS_LABEL = Pattern.compile("[a-z]([-a-z0-9]*[a-z0-9])?");

    private static final DefaultJobParametersConverter PARAMETER_CONVERTER = new DefaultJobParametersConverter();

    private final Scheduler scheduler;
    private final BatchOperator operator;
    private final JobRepository repository;
    private final Map<String, Job> jobs;
    private final JobParameterSpecRegistry specs;
    private final JobParametersResolver resolver;
    private final Ownership ownership;
    private final JdbcOperations jdbc;
    private final String tablePrefix;
    private final BatchProperties properties;
    private final String appVersion;
    private final Clock clock;

    private final Map<String, String> scheduleNames = new HashMap<>();
    private final Map<String, String> pendingSchedules = new LinkedHashMap<>();
    private final Map<LocalRun, Boolean> running = new ConcurrentHashMap<>();
    private final AtomicLong beyondCatchUp = new AtomicLong();
    private final Object lifecycleMonitor = new Object();
    private volatile boolean reconciled;
    private volatile boolean started;
    private volatile boolean stopping;

    /**
     * @param scheduler   the Quartz scheduler of Spring Boot's {@code SchedulerFactoryBean}
     * @param operator    starts, stops and recovers runs
     * @param repository  reads executions
     * @param jobs        the application's jobs by bean name
     * @param specs       their declared parameters
     * @param resolver    resolves a fire's parameters, as the Kubernetes runner does
     * @param ownership   writes the execution lease of this replica's runs
     * @param jdbc        the job store's database
     * @param tablePrefix Quartz's table prefix, {@code <schema>.QRTZ_}
     * @param properties  {@code adhar.batch.*}
     * @param appVersion  the application's version for the provenance, {@code null} when unknown
     * @param clock       the time source of the kit's own decisions
     */
    public QuartzSchedulerEngine(Scheduler scheduler, BatchOperator operator, JobRepository repository,
                                 Map<String, Job> jobs, JobParameterSpecRegistry specs, JobParametersResolver resolver,
                                 Ownership ownership, JdbcOperations jdbc, String tablePrefix,
                                 BatchProperties properties, String appVersion, Clock clock) {
        this.scheduler = scheduler;
        this.operator = operator;
        this.repository = repository;
        this.jobs = Map.copyOf(jobs);
        this.specs = specs;
        this.resolver = resolver;
        this.ownership = ownership;
        this.jdbc = jdbc;
        this.tablePrefix = tablePrefix;
        this.properties = properties;
        this.appVersion = appVersion;
        this.clock = clock;
        this.jobs.keySet().forEach(bean -> scheduleNames.put(bean, scheduleName(bean, properties.getQuartz())));
        Map<String, String> owners = new HashMap<>();
        scheduleNames.forEach((bean, name) -> {
            String other = owners.putIfAbsent(name, bean);
            if (other != null) {
                throw new IllegalStateException("Jobs [" + other + "] and [" + bean + "] have the same schedule name '"
                        + name + "': set adhar.batch.quartz.jobs.<job>.name on one of them");
            }
        });
    }

    @Override
    public String name() {
        return NAME;
    }

    // ------------------------------------------------------------------
    // The reconcile, before the Quartz scheduler starts
    // ------------------------------------------------------------------

    /**
     * Stores a JobDetail per job and reconciles the configured triggers into {@value #CONFIG_GROUP}.
     * Runs once every singleton exists, before Spring Boot's {@code SchedulerFactoryBean} starts the
     * scheduler: a trigger's definition is current before anything fires it.
     *
     * @throws IllegalStateException naming the job or trigger that cannot be scheduled
     */
    @Override
    public void afterSingletonsInstantiated() {
        synchronized (lifecycleMonitor) {
            try {
                reconcile();
            } catch (SchedulerException ex) {
                throw new IllegalStateException("Could not reconcile the in-service schedules into the Quartz job "
                        + "store: " + ex.getMessage(), ex);
            }
            reconciled = true;
        }
    }

    private void reconcile() throws SchedulerException {
        for (Map.Entry<String, Job> entry : new TreeMap<>(jobs).entrySet()) {
            if (entry.getValue().getJobParametersIncrementer() == null) {
                scheduler.addJob(jobDetail(entry.getKey()), true);
            }
        }
        Map<TriggerKey, Trigger> desired = new LinkedHashMap<>();
        Instant now = clock.instant();
        properties.getQuartz().getTriggers().forEach((bean, settings) -> {
            Set<String> names = new LinkedHashSet<>();
            for (TriggerSettings trigger : settings) {
                if (trigger.getName() == null || trigger.getName().isBlank()) {
                    throw new IllegalStateException("adhar.batch.quartz.triggers." + bean + ": every trigger needs a name");
                }
                if (!names.add(trigger.getName())) {
                    throw new IllegalStateException("adhar.batch.quartz.triggers." + bean + " names trigger '"
                            + trigger.getName() + "' twice");
                }
                Trigger built = configTrigger(bean, trigger, now);
                desired.put(built.getKey(), built);
            }
        });
        Map<String, String> crons = new LinkedHashMap<>(properties.getScheduler().getJobs());
        crons.putAll(pendingSchedules);
        crons.forEach((bean, cron) -> {
            Trigger built = configTrigger(bean, springCronTrigger(cron), now);
            desired.put(built.getKey(), built);
        });
        int kept = 0;
        for (TriggerKey key : scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(CONFIG_GROUP))) {
            Trigger wanted = desired.remove(key);
            Trigger stored = scheduler.getTrigger(key);
            if (wanted == null) {
                scheduler.unscheduleJob(key);
                log.info("Removed trigger {}: it is no longer configured", key);
            } else if (stored != null && definition(stored).equals(definition(wanted))
                    && stored.getJobKey().equals(wanted.getJobKey())) {
                kept++;
            } else {
                scheduler.rescheduleJob(key, wanted);
                log.info("Updated trigger {}: its definition changed", key);
            }
        }
        for (Trigger trigger : desired.values()) {
            scheduler.scheduleJob(trigger);
            log.info("Added trigger {}", trigger.getKey());
        }
        log.info("In-service schedules reconciled on Quartz scheduler '{}': {} job(s), {} trigger(s) added, {} kept",
                scheduler.getSchedulerName(), jobs.size(), desired.size(), kept);
    }

    private JobDetail jobDetail(String bean) {
        return JobBuilder.newJob(BatchLaunchJob.class)
                .withIdentity(scheduleNames.get(bean), JOB_GROUP)
                .storeDurably()
                .requestRecovery()
                .usingJobData(JOB, bean)
                .withDescription("Spring Batch job " + bean)
                .build();
    }

    /**
     * Builds a configured trigger of a job, validating its definition and the identity its fires
     * will resolve to.
     */
    Trigger configTrigger(String bean, TriggerSettings settings, Instant now) {
        Job job = schedulable(bean);
        String scheduleName = scheduleNames.get(bean);
        String where = "adhar.batch.quartz.triggers." + bean + "[" + settings.getName() + "]";
        ZoneId zone = settings.getTimeZone() != null ? settings.getTimeZone() : properties.getScheduler().getTimeZone();
        Misfire misfire = settings.getMisfire() != null ? settings.getMisfire() : properties.getQuartz().getMisfire();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(KIND, KIND_CONFIG);
        data.put(ZONE, zone.getId());
        data.put(MISFIRE, misfire.name().toLowerCase(Locale.ROOT));
        data.put(PARAMS, toJson(settings.getParameters()));
        ScheduleBuilder<? extends Trigger> schedule;
        Instant startAt = null;
        StringJoiner definition = new StringJoiner(";");
        definition.add("kind=" + settings.getKind()).add("zone=" + zone.getId()).add("misfire=" + misfire)
                .add("priority=" + settings.getPriority()).add("calendar=" + settings.getCalendar())
                .add("params=" + data.get(PARAMS));
        switch (settings.getKind()) {
            case CRON -> {
                if (settings.getExpression() == null || !CronExpression.isValidExpression(settings.getExpression())) {
                    throw new IllegalStateException(where + ".expression is not a valid Quartz cron expression (seconds "
                            + "first, '?' in one of the day fields): '" + settings.getExpression() + "'");
                }
                CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule(settings.getExpression())
                        .inTimeZone(TimeZone.getTimeZone(zone));
                schedule = switch (misfire) {
                    case SKIP -> cron.withMisfireHandlingInstructionDoNothing();
                    case LATEST -> cron.withMisfireHandlingInstructionFireAndProceed();
                    case ALL -> cron.withMisfireHandlingInstructionIgnoreMisfires();
                };
                definition.add("expression=" + settings.getExpression());
            }
            case INTERVAL -> {
                Duration every = requireEvery(where, settings.getEvery());
                Instant start = settings.getStart() != null ? settings.getStart() : Instant.EPOCH;
                startAt = SlotResolver.firstSlotAfter(start, every, now);
                data.put(GRID_START, start.toString());
                data.put(GRID_EVERY, every.toString());
                SimpleScheduleBuilder simple = SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(every.toMillis()).repeatForever();
                schedule = switch (misfire) {
                    case SKIP -> simple.withMisfireHandlingInstructionNextWithRemainingCount();
                    case LATEST -> simple.withMisfireHandlingInstructionNowWithExistingCount();
                    case ALL -> simple.withMisfireHandlingInstructionIgnoreMisfires();
                };
                definition.add("every=" + every).add("start=" + start);
            }
            case DAILY -> {
                Duration every = requireEvery(where, settings.getEvery());
                if (settings.getFrom() == null || settings.getTo() == null || !settings.getFrom().isBefore(settings.getTo())) {
                    throw new IllegalStateException(where + " needs from and to, from before to");
                }
                // DailyTimeIntervalTrigger computes its window in the JVM's default zone and has no
                // zone of its own: a trigger in another zone would fire at the wrong times of day.
                if (!zone.normalized().equals(ZoneId.systemDefault().normalized())) {
                    throw new IllegalStateException(where + ".time-zone is " + zone + ", but a daily trigger runs in "
                            + "the JVM's default zone (" + ZoneId.systemDefault() + "): set the JVM's zone (TZ) to it, "
                            + "or use a cron trigger");
                }
                Set<Integer> days = new TreeSet<>();
                (settings.getDays().isEmpty() ? List.of(DayOfWeek.values()) : settings.getDays())
                        .forEach(day -> days.add(day.getValue() % 7 + 1));
                DailyTimeIntervalScheduleBuilder daily = DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                        .startingDailyAt(new TimeOfDay(settings.getFrom().getHour(), settings.getFrom().getMinute(),
                                settings.getFrom().getSecond()))
                        .endingDailyAt(new TimeOfDay(settings.getTo().getHour(), settings.getTo().getMinute(),
                                settings.getTo().getSecond()))
                        .withIntervalInSeconds(Math.toIntExact(every.toSeconds()))
                        .onDaysOfTheWeek(days);
                schedule = switch (misfire) {
                    case SKIP -> daily.withMisfireHandlingInstructionDoNothing();
                    case LATEST -> daily.withMisfireHandlingInstructionFireAndProceed();
                    case ALL -> daily.withMisfireHandlingInstructionIgnoreMisfires();
                };
                definition.add("every=" + every).add("from=" + settings.getFrom()).add("to=" + settings.getTo())
                        .add("days=" + days);
            }
            default -> throw new IllegalStateException(where + ".kind " + settings.getKind() + " is unknown");
        }
        data.put(DEFINITION, definition.toString());
        validateIdentity(where, job, settings.getParameters(), now, zone);
        TriggerBuilder<? extends Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(scheduleName + "." + settings.getName(), CONFIG_GROUP)
                .forJob(scheduleName, JOB_GROUP)
                .withPriority(settings.getPriority())
                .usingJobData(new JobDataMap(data))
                .withSchedule(schedule);
        // A cron or daily trigger starts now, by the kit's clock like every other decision of the
        // reconcile (TriggerBuilder alone would read the system's): its grid has no slot before that.
        builder.startAt(Date.from(startAt != null ? startAt : now));
        if (settings.getCalendar() != null) {
            builder.modifiedByCalendar(settings.getCalendar());
        }
        return builder.build();
    }

    private void validateIdentity(String where, Job job, Map<String, String> input, Instant now, ZoneId zone) {
        try {
            SlotIdentity.resolve(resolver, spec(job), input, now, zone);
        } catch (ParameterValidationException ex) {
            throw new IllegalStateException(where + ": the job's parameters do not resolve for a slot: "
                    + ex.getMessage(), ex);
        }
    }

    private static Duration requireEvery(String where, Duration every) {
        if (every == null || every.compareTo(Duration.ofSeconds(1)) < 0 || every.toMillis() % 1000 != 0) {
            throw new IllegalStateException(where + ".every must be whole seconds, at least 1s, was " + every);
        }
        return every;
    }

    private static String definition(Trigger trigger) {
        String definition = trigger.getJobDataMap().getString(DEFINITION);
        return definition != null ? definition : "";
    }

    private Job schedulable(String bean) {
        Job job = jobs.get(bean);
        if (job == null) {
            throw new IllegalStateException("Job [" + bean + "] cannot be scheduled: there is no Job bean of that "
                    + "name (the jobs are " + new TreeSet<>(jobs.keySet()) + ")");
        }
        if (job.getJobParametersIncrementer() != null) {
            throw new IllegalStateException("Job [" + bean + "] has a JobParametersIncrementer, so it cannot be "
                    + "scheduled: Spring Batch would ignore each slot's identity and start a new JobInstance on "
                    + "every fire. Remove the incrementer from the job.");
        }
        return job;
    }

    // ------------------------------------------------------------------
    // The public API of BatchScheduler
    // ------------------------------------------------------------------

    @Override
    public void scheduleJob(String jobName, String cronExpression) {
        String bean = beanName(jobName);
        TriggerSettings settings = springCronTrigger(cronExpression);
        synchronized (lifecycleMonitor) {
            Trigger trigger;
            try {
                trigger = configTrigger(bean, settings, clock.instant());
            } catch (IllegalStateException ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }
            if (!reconciled) {
                pendingSchedules.put(bean, cronExpression);
            } else {
                try {
                    if (scheduler.checkExists(trigger.getKey())) {
                        scheduler.rescheduleJob(trigger.getKey(), trigger);
                    } else {
                        scheduler.scheduleJob(trigger);
                    }
                } catch (SchedulerException ex) {
                    throw new IllegalStateException("Could not schedule job [" + jobName + "]: " + ex.getMessage(), ex);
                }
            }
        }
        log.info("Scheduled job [{}] with cron '{}' in {} (Quartz {})", jobName, cronExpression,
                properties.getScheduler().getTimeZone(), settings.getExpression());
    }

    @Override
    public boolean cancelScheduledJob(String jobName) {
        String bean = beanNameOrNull(jobName);
        if (bean == null) {
            return false;
        }
        synchronized (lifecycleMonitor) {
            boolean pending = pendingSchedules.remove(bean) != null;
            if (!reconciled) {
                return pending;
            }
            boolean removed = false;
            try {
                for (Trigger trigger : scheduler.getTriggersOfJob(jobKey(bean))) {
                    if (CONFIG_GROUP.equals(trigger.getKey().getGroup())) {
                        removed |= scheduler.unscheduleJob(trigger.getKey());
                    }
                }
            } catch (SchedulerException ex) {
                throw new IllegalStateException("Could not cancel the schedule of job [" + jobName + "]: "
                        + ex.getMessage(), ex);
            }
            if (removed) {
                log.info("Cancelled the schedule of job [{}]", jobName);
            }
            return removed;
        }
    }

    @Override
    public Set<String> listScheduledJobs() {
        Set<String> scheduled = new TreeSet<>(pendingSchedules.keySet());
        if (!reconciled) {
            return Collections.unmodifiableSet(scheduled);
        }
        try {
            for (TriggerKey key : scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(CONFIG_GROUP))) {
                Trigger trigger = scheduler.getTrigger(key);
                if (trigger != null) {
                    JobDetail detail = scheduler.getJobDetail(trigger.getJobKey());
                    if (detail != null && detail.getJobDataMap().getString(JOB) != null) {
                        scheduled.add(detail.getJobDataMap().getString(JOB));
                    }
                }
            }
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not list the schedules: " + ex.getMessage(), ex);
        }
        return Collections.unmodifiableSet(scheduled);
    }

    @Override
    public Optional<Instant> nextFireTime(String jobName) {
        String bean = beanNameOrNull(jobName);
        if (bean == null || !started) {
            return Optional.empty();
        }
        try {
            return scheduler.getTriggersOfJob(jobKey(bean)).stream()
                    .map(Trigger::getNextFireTime)
                    .filter(java.util.Objects::nonNull)
                    .map(Date::toInstant)
                    .min(Instant::compareTo);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not read the triggers of job [" + jobName + "]: " + ex.getMessage(),
                    ex);
        }
    }

    @Override
    public Set<String> runningJobs() {
        Set<String> names = new TreeSet<>();
        running.keySet().forEach(run -> names.add(run.bean));
        return Collections.unmodifiableSet(names);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (started) {
                return;
            }
            stopping = false;
            started = true;
        }
        log.info("Embedded batch scheduler started on Quartz: scheduler '{}', instance {}, {} job(s)",
                schedulerName(), instanceId(), jobs.size());
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!started) {
                return;
            }
            started = false;
            stopping = true;
        }
        try {
            scheduler.standby();
        } catch (SchedulerException ex) {
            log.warn("Could not put the Quartz scheduler in standby: {}", ex.getMessage());
        }
        running.keySet().forEach(run -> requestStop(run, "the application is shutting down"));
        Duration timeout = properties.getShutdown().getTimeout();
        Instant deadline = clock.instant().plus(timeout);
        while (!running.isEmpty() && clock.instant().isBefore(deadline)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!running.isEmpty()) {
            log.warn("{} scheduled run(s) did not stop within adhar.batch.shutdown.timeout ({}); another replica "
                    + "recovers them once this one stops checking in: {}", running.size(), timeout, runningJobs());
        }
        log.info("Embedded batch scheduler on Quartz stopped");
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    private void requestStop(LocalRun run, String reason) {
        Long executionId = run.executionId;
        if (executionId == null) {
            return;
        }
        try {
            JobExecution execution = repository.getJobExecution(executionId);
            if (execution != null && execution.isRunning()) {
                log.info("Stopping execution {} of job [{}] ({}): {}", executionId, run.bean, run.runName, reason);
                operator.stop(execution);
            }
        } catch (JobExecutionNotRunningException ex) {
            log.debug("Execution {} had already ended", executionId);
        } catch (RuntimeException ex) {
            log.warn("Could not stop execution {} of job [{}]: {}", executionId, run.bean, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Collaborators of BatchLaunchJob
    // ------------------------------------------------------------------

    /**
     * Stores the one-shot resume trigger of a run this replica stopped while shutting down
     * (design 0025 §5.20.5 identity rule 8): its identifying parameters verbatim, keyed by its run
     * name, so it and a runtime trigger for the same identity collapse to one.
     *
     * @param run       the run
     * @param execution its STOPPED execution
     */
    void requeue(LocalRun run, JobExecution execution) {
        TriggerKey key = new TriggerKey(run.runName, RUNTIME_GROUP);
        try {
            if (scheduler.checkExists(key)) {
                log.info("Run {} already has a trigger in {}; not storing a resume trigger", run.runName, RUNTIME_GROUP);
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            data.put(KIND, KIND_RESUME);
            data.put(IDENTITY, encodeIdentity(execution.getJobParameters()));
            data.put(RUN, run.runName);
            data.put(ZONE, run.zone.getId());
            scheduler.scheduleJob(oneShot(key, jobKey(run.bean), data, clock.instant()));
            log.info("Stored resume trigger {} for execution {} of job [{}]: another replica restarts it", key,
                    execution.getId(), run.bean);
        } catch (SchedulerException ex) {
            log.warn("Could not store the resume trigger of run {} ({}); its next fire restarts it", run.runName,
                    ex.getMessage());
        }
    }

    /**
     * @param key  the trigger's key, in {@value #RUNTIME_GROUP}
     * @param job  the JobDetail
     * @param data the trigger's data (strings only)
     * @param at   when it fires
     * @return a one-shot trigger that, misfired, still fires: it stands for an event
     */
    public static Trigger oneShot(TriggerKey key, JobKey job, Map<String, String> data, Instant at) {
        return TriggerBuilder.newTrigger()
                .withIdentity(key)
                .forJob(job)
                .usingJobData(new JobDataMap(data))
                .startAt(Date.from(at))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
    }

    /**
     * @param parameters an execution's parameters
     * @return its identifying parameters, typed, as the text a resume trigger stores
     */
    static String encodeIdentity(JobParameters parameters) {
        Map<String, JobParameter<?>> identifying = new TreeMap<>();
        parameters.getIdentifyingParameters().forEach(parameter -> identifying.put(parameter.name(), parameter));
        Properties encoded = PARAMETER_CONVERTER.getProperties(new JobParameters(new LinkedHashSet<>(identifying.values())));
        Map<String, String> sorted = new TreeMap<>();
        encoded.stringPropertyNames().forEach(name -> sorted.put(name, encoded.getProperty(name)));
        return toJson(sorted);
    }

    /**
     * @param text the text {@link #encodeIdentity} stored
     * @return the identifying parameters, as they were
     */
    static JobParameters decodeIdentity(String text) {
        Properties properties = new Properties();
        fromJson(text).forEach(properties::setProperty);
        return PARAMETER_CONVERTER.getJobParameters(properties);
    }

    /**
     * @param values string values
     * @return them as a JSON object, keys sorted
     */
    static String toJson(Map<String, String> values) {
        StringJoiner object = new StringJoiner(",", "{", "}");
        new TreeMap<>(values).forEach((name, value) -> object.add(Json.quote(name) + ":" + Json.quote(value)));
        return object.toString();
    }

    /**
     * @param text a JSON object of single values, {@code null} or blank for none
     * @return its values as strings
     */
    static Map<String, String> fromJson(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("not a JSON object: " + text);
        }
        object.forEach((name, value) -> {
            if (value != null) {
                values.put((String) name, value instanceof BigDecimal number ? number.toPlainString()
                        : String.valueOf(value));
            }
        });
        return values;
    }

    JobParameterSpec spec(Job job) {
        return specs != null ? specs.spec(job.getName()) : JobParameterSpec.none(job.getName());
    }

    Job job(String bean) {
        return jobs.get(bean);
    }

    String scheduleNameOf(String bean) {
        return scheduleNames.get(bean);
    }

    JobKey jobKey(String bean) {
        return new JobKey(scheduleNames.get(bean), JOB_GROUP);
    }

    Scheduler scheduler() {
        return scheduler;
    }

    BatchOperator operator() {
        return operator;
    }

    JobRepository repository() {
        return repository;
    }

    JobParametersResolver resolver() {
        return resolver;
    }

    Ownership ownership() {
        return ownership;
    }

    BatchProperties properties() {
        return properties;
    }

    String appVersion() {
        return appVersion;
    }

    Clock clock() {
        return clock;
    }

    boolean stopping() {
        return stopping;
    }

    void started(LocalRun run) {
        running.put(run, Boolean.TRUE);
    }

    void ended(LocalRun run) {
        running.remove(run);
    }

    long beyondCatchUp() {
        return beyondCatchUp.get();
    }

    void countBeyondCatchUp() {
        beyondCatchUp.incrementAndGet();
    }

    String instanceId() {
        try {
            return scheduler.getSchedulerInstanceId();
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not read the Quartz instance id: " + ex.getMessage(), ex);
        }
    }

    String schedulerName() {
        try {
            return scheduler.getSchedulerName();
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Could not read the Quartz scheduler name: " + ex.getMessage(), ex);
        }
    }

    /**
     * The owner check (design 0025 §5.20.5): a Quartz run's liveness is its instance's check-in in
     * {@code QRTZ_SCHEDULER_STATE}, which Quartz already maintains and which is the signal that makes
     * Quartz re-fire a dead instance's work. A check-in is fresh within one check-in interval plus
     * Quartz's own tolerance, plus one more interval of margin for clock skew between replicas:
     * skew below the margin leaves a live owner's run alone ({@code skipped(duplicate)}).
     *
     * @param instance a Quartz instance id
     * @return whether it checked in recently enough to be running
     */
    boolean instanceAlive(String instance) {
        List<long[]> rows;
        try {
            rows = jdbc.query("SELECT LAST_CHECKIN_TIME, CHECKIN_INTERVAL FROM " + tablePrefix
                            + "SCHEDULER_STATE WHERE SCHED_NAME = ? AND INSTANCE_NAME = ?",
                    (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, schedulerName(), instance);
        } catch (DataAccessException ex) {
            log.warn("Could not read the check-in of instance {}; treating it as alive: {}", instance, ex.getMessage());
            return true;
        }
        if (rows.isEmpty()) {
            return false;
        }
        long lastCheckin = rows.get(0)[0];
        long interval = rows.get(0)[1];
        long freshUntil = lastCheckin + 2 * interval + CHECKIN_TOLERANCE.toMillis();
        return clock.millis() <= freshUntil;
    }

    private String beanName(String jobName) {
        String bean = beanNameOrNull(jobName);
        if (bean == null) {
            throw new IllegalArgumentException("No job named [" + jobName + "] (the jobs are "
                    + new TreeSet<>(jobs.keySet()) + ")");
        }
        return bean;
    }

    private String beanNameOrNull(String jobName) {
        if (jobs.containsKey(jobName)) {
            return jobName;
        }
        return jobs.entrySet().stream().filter(entry -> jobName.equals(entry.getValue().getName()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    // Names and the Spring cron of adhar.batch.scheduler.jobs
    // ------------------------------------------------------------------

    /**
     * @param bean     a job's bean name
     * @param settings {@code adhar.batch.quartz.*}
     * @return its schedule name: {@code jobs.<bean>.name}, else the bean name as a DNS-1035 label
     */
    static String scheduleName(String bean, BatchProperties.Quartz settings) {
        BatchProperties.Quartz.JobSettings job = settings.getJobs().get(bean);
        if (job != null && job.getName() != null) {
            String name = job.getName();
            if (name.length() > MAX_SCHEDULE_NAME || !DNS_LABEL.matcher(name).matches()) {
                throw new IllegalStateException("adhar.batch.quartz.jobs." + bean + ".name must be a DNS-1035 label of "
                        + "at most " + MAX_SCHEDULE_NAME + " characters, was '" + name + "'");
            }
            return name;
        }
        return dnsLabel(bean);
    }

    /**
     * @param bean a bean name
     * @return it as a DNS-1035 label of at most {@value #MAX_SCHEDULE_NAME} characters:
     *         {@code importJob} becomes {@code import-job}: the default schedule name, and the key of
     *         the job's JobDetail when {@code adhar.batch.quartz.jobs.<bean>.name} is unset
     */
    public static String dnsLabel(String bean) {
        String kebab = bean.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^[-0-9]+", "");
        if (kebab.length() > MAX_SCHEDULE_NAME) {
            kebab = kebab.substring(0, MAX_SCHEDULE_NAME);
        }
        kebab = kebab.replaceAll("-+$", "");
        if (kebab.isEmpty()) {
            throw new IllegalStateException("Job bean [" + bean + "] has no letters to name its schedule with: set "
                    + "adhar.batch.quartz.jobs." + bean + ".name");
        }
        return kebab;
    }

    private static TriggerSettings springCronTrigger(String springCron) {
        TriggerSettings settings = new TriggerSettings();
        settings.setName(SCHEDULE_TRIGGER);
        settings.setKind(TriggerSettings.Kind.CRON);
        settings.setExpression(SpringCron.toQuartz(springCron));
        return settings;
    }

    /**
     * Converts a Spring cron expression (six fields, the schedules of
     * {@code adhar.batch.scheduler.jobs}) to Quartz syntax (design 0025 §5.20.6): macros expanded,
     * {@code ?} in the day field that is {@code *}, numeric days of the week renumbered (Spring 0 or
     * 7 = Sunday, 1 = Monday; Quartz 1 = Sunday).
     */
    static final class SpringCron {

        private static final Map<String, String> MACROS = Map.of(
                "@yearly", "0 0 0 1 1 *", "@annually", "0 0 0 1 1 *", "@monthly", "0 0 0 1 * *",
                "@weekly", "0 0 0 * * 0", "@daily", "0 0 0 * * *", "@midnight", "0 0 0 * * *",
                "@hourly", "0 0 * * * *");

        private SpringCron() {
        }

        static String toQuartz(String spring) {
            if (spring == null || spring.isBlank()) {
                throw new IllegalArgumentException("A cron expression is required");
            }
            String expanded = MACROS.getOrDefault(spring.strip().toLowerCase(Locale.ROOT), spring.strip());
            try {
                org.springframework.scheduling.support.CronExpression.parse(expanded);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid cron expression '" + spring + "': " + ex.getMessage()
                        + " (six fields, seconds first)", ex);
            }
            String[] fields = expanded.split("\\s+");
            String dayOfMonth = fields[3];
            String dayOfWeek = renumberDaysOfWeek(fields[5]);
            if (isAny(dayOfWeek)) {
                dayOfWeek = "?";
                dayOfMonth = isAny(dayOfMonth) ? "*" : dayOfMonth;
            } else if (isAny(dayOfMonth)) {
                dayOfMonth = "?";
            } else {
                throw new IllegalArgumentException("Cron expression '" + spring + "' restricts both the day of the month "
                        + "and the day of the week, which Quartz cannot express: write it as an "
                        + "adhar.batch.quartz.triggers cron trigger in Quartz syntax");
            }
            String quartz = String.join(" ", fields[0], fields[1], fields[2], dayOfMonth, fields[4], dayOfWeek);
            if (!CronExpression.isValidExpression(quartz)) {
                throw new IllegalArgumentException("Cron expression '" + spring + "' converts to '" + quartz
                        + "', which Quartz does not accept: write it as an adhar.batch.quartz.triggers cron trigger");
            }
            return quartz;
        }

        private static boolean isAny(String field) {
            return "*".equals(field) || "?".equals(field);
        }

        private static String renumberDaysOfWeek(String field) {
            StringBuilder out = new StringBuilder();
            java.util.regex.Matcher numbers = Pattern.compile("(?<![/#])\\b([0-7])\\b").matcher(field);
            while (numbers.find()) {
                int spring = Integer.parseInt(numbers.group(1));
                numbers.appendReplacement(out, Integer.toString(spring % 7 + 1));
            }
            numbers.appendTail(out);
            return out.toString();
        }
    }

    // ------------------------------------------------------------------
    // This replica's runs
    // ------------------------------------------------------------------

    /**
     * One run this replica is executing.
     */
    static final class LocalRun {

        final String bean;
        final String jobName;
        final String runName;
        final ZoneId zone;
        volatile Long executionId;

        LocalRun(String bean, String jobName, String runName, ZoneId zone) {
            this.bean = bean;
            this.jobName = jobName;
            this.runName = runName;
            this.zone = zone;
        }
    }

    /**
     * Writes the execution lease of the runs this replica's fires start, with
     * {@code OWNER_POD} = the Quartz instance id (design 0025 §5.20.5, the owner check), as the
     * Kubernetes runner writes its own. A bean of its own, not the engine: the kit's job listener
     * collects the run observers early, before the Quartz scheduler exists.
     */
    public static final class Ownership implements BatchRunObserver {

        private final ExecutionLease lease;
        private final ThreadLocal<Claim> claims = new ThreadLocal<>();

        /**
         * @param lease the execution leases, on the JobRepository's database
         */
        public Ownership(ExecutionLease lease) {
            this.lease = lease;
        }

        void begin(LocalRun run, ExecutionLease.Owner owner) {
            claims.set(new Claim(run, owner));
        }

        void end() {
            claims.remove();
        }

        /**
         * Claims an execution the launching thread got back without the job listener having seen it
         * start (a job run on an asynchronous task executor).
         */
        void claim(JobExecution execution) {
            Claim claim = claims.get();
            if (claim != null && claim.run.executionId == null) {
                acquire(claim, execution);
            }
        }

        /**
         * @param executionId an execution
         * @return the Quartz instance its lease names, {@code null} when it has none
         */
        String owner(long executionId) {
            try {
                return lease.find(executionId).map(found -> found.owner().pod()).orElse(null);
            } catch (RuntimeException ex) {
                log.warn("Could not read the lease of execution {}: {}", executionId, ex.getMessage());
                return null;
            }
        }

        void release(long executionId) {
            try {
                lease.release(executionId);
            } catch (RuntimeException ex) {
                log.warn("Could not release the lease of execution {}: {}", executionId, ex.getMessage());
            }
        }

        @Override
        public void jobStarted(JobExecution execution) {
            Claim claim = claims.get();
            if (claim != null && claim.run.executionId == null
                    && claim.run.jobName.equals(execution.getJobInstance().getJobName())) {
                acquire(claim, execution);
            }
        }

        private void acquire(Claim claim, JobExecution execution) {
            claim.run.executionId = execution.getId();
            try {
                lease.acquire(execution.getId(), claim.owner);
            } catch (RuntimeException ex) {
                log.warn("Could not write the lease of execution {}: {}", execution.getId(), ex.getMessage());
            }
        }

        private record Claim(LocalRun run, ExecutionLease.Owner owner) {
        }
    }
}
