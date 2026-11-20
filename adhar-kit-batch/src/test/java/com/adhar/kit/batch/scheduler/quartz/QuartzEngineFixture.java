package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.runtime.JdbcExecutionLease;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.FailureInjector;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.quartz.Calendar;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The engine {@code quartz} without a database server: the kit's {@link QuartzSchedulerEngine} and
 * {@link BatchLaunchJob} on a real Quartz scheduler with its in-memory store, launching real Spring
 * Batch jobs on the H2 JobRepository of a test context. Time is a clock the test moves, and a fire
 * is the {@link JobExecutionContext} Quartz itself builds ({@link JobExecutionContextImpl} over a
 * {@link TriggerFiredBundle}), so a test states exactly which trigger fired, for which scheduled
 * time, and whether Quartz is recovering.
 *
 * <p>What only a clustered JDBC store can show (one fire across replicas, Quartz's own recovery
 * scan, the misfire handler's timing) stays with the {@code *TestContainerTest} classes; the kit's
 * decisions about a fire are all reachable here.</p>
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class, QuartzJobs.class, QuartzEngineFixture.Jobs.class})
abstract class QuartzEngineFixture {

    /** A Friday noon, on the five-minute and the ten-second grid. */
    static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    /** Quartz's table prefix in the test's H2 schema. */
    static final String PREFIX = "QRTZ_";

    /** The application the fixture's engines belong to. */
    static final String APPLICATION = "svc";

    static final String DATED_JOB = "datedJob";
    static final String FLAKY_JOB = "flakyJob";
    static final String COUNTER_JOB = "counterJob";

    private static final AtomicInteger SCHEDULERS = new AtomicInteger();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    BatchOperator operator;
    @Autowired
    JobRepository repository;
    @Autowired
    ListableBeanFactory beans;
    @Autowired
    JobParameterSpecRegistry specs;
    @Autowired
    JobParametersResolver resolver;
    @Autowired
    QuartzSchedulerEngine.Ownership ownership;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    QuartzJobs.Gate gate;
    @Autowired
    FailureInjector failures;

    final MovableClock clock = new MovableClock(NOON);
    BatchProperties properties;
    Scheduler scheduler;
    QuartzSchedulerEngine engine;
    private final List<Scheduler> schedulers = new ArrayList<>();

    @BeforeEach
    void resetFixture() throws Exception {
        // Quartz's own DDL for the one table the kit reads itself (the owner check).
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + PREFIX + "SCHEDULER_STATE (SCHED_NAME VARCHAR(120) NOT NULL, "
                + "INSTANCE_NAME VARCHAR(200) NOT NULL, LAST_CHECKIN_TIME BIGINT NOT NULL, "
                + "CHECKIN_INTERVAL BIGINT NOT NULL, PRIMARY KEY (SCHED_NAME, INSTANCE_NAME))");
        // The JobRepository too: every test fires the same few slots, and a slot's identity is its
        // JobInstance.
        for (String table : List.of("SOURCE_ITEMS", "TARGET_ITEMS", "SLOT_RUNS", "ADHAR_BATCH_EXECUTION_LEASE",
                PREFIX + "SCHEDULER_STATE", "BATCH_STEP_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION", "BATCH_JOB_INSTANCE")) {
            jdbc.update("DELETE FROM " + table);
        }
        failures.clear();
        calendarSettings.clear();
        gate.release();
        Jobs.FLAKY.set(false);
        clock.set(NOON);
        properties = new BatchProperties();
        properties.getScheduler().setTimeZone(ZoneOffset.UTC);
        scheduler = newScheduler("replica-a");
    }

    @AfterEach
    void shutDownSchedulers() throws Exception {
        gate.release();
        if (engine != null) {
            engine.stop();
        }
        for (Scheduler created : schedulers) {
            created.shutdown(true);
        }
    }

    /**
     * @param instanceId the replica's Quartz instance id
     * @return a Quartz scheduler on the in-memory store, not started, whose jobs are built by the
     *         engine the test creates next
     */
    Scheduler newScheduler(String instanceId) throws SchedulerException {
        Properties settings = new Properties();
        settings.setProperty("org.quartz.scheduler.instanceName", "svc-" + SCHEDULERS.incrementAndGet());
        settings.setProperty("org.quartz.scheduler.instanceId", instanceId);
        settings.setProperty("org.quartz.threadPool.threadCount", "3");
        settings.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        settings.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        Scheduler created = new StdSchedulerFactory(settings).getScheduler();
        created.setJobFactory((bundle, quartz) -> new BatchLaunchJob(engine));
        schedulers.add(created);
        return created;
    }

    /**
     * @return a new engine on the fixture's scheduler and properties, not yet reconciled
     */
    QuartzSchedulerEngine newEngine() {
        engine = new QuartzSchedulerEngine(scheduler, operator, repository, beans.getBeansOfType(Job.class), specs,
                resolver, ownership, jdbc, PREFIX, properties, APPLICATION, "1.4.0", calendars(), clock);
        return engine;
    }

    /**
     * @return the business calendars of {@code properties}' {@code adhar.batch.quartz.calendars}
     *         entries a test put in {@link #calendarSettings}
     */
    CalendarRegistry calendars() {
        return new CalendarRegistry(calendarSettings, properties.getScheduler().getTimeZone());
    }

    /** Calendars by name, read by the next engine the test creates. */
    final Map<String, BatchProperties.Quartz.CalendarSettings> calendarSettings = new java.util.LinkedHashMap<>();

    /**
     * @return a new engine, reconciled into the store
     */
    QuartzSchedulerEngine reconciled() {
        newEngine().afterSingletonsInstantiated();
        return engine;
    }

    /**
     * Adds a configured trigger of a job to the properties, before the engine is created.
     */
    BatchProperties.Quartz.TriggerSettings configure(String bean, String name) {
        BatchProperties.Quartz.TriggerSettings settings = new BatchProperties.Quartz.TriggerSettings();
        settings.setName(name);
        properties.getQuartz().getTriggers().computeIfAbsent(bean, key -> new ArrayList<>()).add(settings);
        return settings;
    }

    BatchProperties.Quartz.TriggerSettings cron(String bean, String expression) {
        BatchProperties.Quartz.TriggerSettings settings = configure(bean, "tick");
        settings.setKind(BatchProperties.Quartz.TriggerSettings.Kind.CRON);
        settings.setExpression(expression);
        return settings;
    }

    BatchProperties.Quartz.TriggerSettings interval(String bean, String every) {
        BatchProperties.Quartz.TriggerSettings settings = configure(bean, "tick");
        settings.setKind(BatchProperties.Quartz.TriggerSettings.Kind.INTERVAL);
        settings.setEvery(java.time.Duration.parse(every));
        return settings;
    }

    /**
     * @param bean a job's bean name
     * @return its configured trigger {@code tick}, as the store holds it
     */
    Trigger stored(String bean) throws SchedulerException {
        return scheduler.getTrigger(configKey(bean));
    }

    TriggerKey configKey(String bean) {
        return new TriggerKey(engine.scheduleNameOf(bean) + ".tick", QuartzSchedulerEngine.CONFIG_GROUP);
    }

    /**
     * @param trigger   the trigger that fires
     * @param scheduled the scheduled time Quartz hands the job
     * @return the fire, as Quartz builds it
     */
    JobExecutionContext fire(Trigger trigger, Instant scheduled) throws SchedulerException {
        return fire(trigger, scheduled, null);
    }

    JobExecutionContext fire(Trigger trigger, Instant scheduled, Calendar calendar) throws SchedulerException {
        JobDetail detail = scheduler.getJobDetail(trigger.getJobKey());
        TriggerFiredBundle bundle = new TriggerFiredBundle(detail, (OperableTrigger) trigger, calendar, false,
                Date.from(clock.instant()), Date.from(scheduled), null, null);
        return new JobExecutionContextImpl(scheduler, bundle, new BatchLaunchJob(engine));
    }

    /**
     * @param original  the trigger whose fire a dead replica left in flight
     * @param scheduled that fire's scheduled time
     * @return the recovery fire, as Quartz's cluster manager builds it
     *         ({@code StdJDBCDelegate.selectTriggersForRecoveringJobs}): a one-shot trigger in the
     *         recovering group carrying the original trigger's data, its key and its times
     */
    JobExecutionContext recoveryFire(Trigger original, Instant scheduled) throws SchedulerException {
        JobDataMap data = new JobDataMap(original.getJobDataMap());
        data.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_NAME, original.getKey().getName());
        data.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_GROUP, original.getKey().getGroup());
        data.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_FIRETIME_IN_MILLISECONDS,
                String.valueOf(scheduled.toEpochMilli()));
        data.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS,
                String.valueOf(scheduled.toEpochMilli()));
        Trigger recovery = TriggerBuilder.newTrigger()
                .withIdentity("recover_replica-b_1", Scheduler.DEFAULT_RECOVERY_GROUP)
                .forJob(original.getJobKey())
                .usingJobData(data)
                .startAt(Date.from(scheduled))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        JobDetail detail = scheduler.getJobDetail(original.getJobKey());
        TriggerFiredBundle bundle = new TriggerFiredBundle(detail, (OperableTrigger) recovery, null, true,
                Date.from(clock.instant()), Date.from(scheduled), null, null);
        return new JobExecutionContextImpl(scheduler, bundle, new BatchLaunchJob(engine));
    }

    /**
     * @param bean a job's bean name
     * @param key  the one-shot trigger's name
     * @param data its data
     * @return a runtime one-shot trigger of the job
     */
    Trigger oneShot(String bean, String key, Map<String, String> data) {
        return QuartzSchedulerEngine.oneShot(new TriggerKey(key, QuartzSchedulerEngine.RUNTIME_GROUP),
                engine.jobKey(bean), data, clock.instant());
    }

    /**
     * @param job a job's name
     * @return its executions, oldest first
     */
    List<JobExecution> executions(String job) {
        List<JobExecution> all = new ArrayList<>();
        repository.getJobInstances(job, 0, 1000).forEach(instance -> all.addAll(repository.getJobExecutions(instance)));
        all.sort(java.util.Comparator.comparing(JobExecution::getId));
        return all;
    }

    /**
     * @param runner the {@code RUNNER} column: the job that recorded the slot
     * @return the slots it recorded, in order
     */
    List<String> slots(String runner) {
        return jdbc.queryForList("SELECT SCHEDULED_TIME FROM SLOT_RUNS WHERE RUNNER = ? ORDER BY SCHEDULED_TIME",
                String.class, runner);
    }

    /**
     * Records a check-in of a Quartz instance of the fixture's scheduler.
     */
    void checkIn(String instance, Instant at, long intervalMillis) throws SchedulerException {
        jdbc.update("DELETE FROM " + PREFIX + "SCHEDULER_STATE WHERE INSTANCE_NAME = ?", instance);
        jdbc.update("INSERT INTO " + PREFIX + "SCHEDULER_STATE (SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME, "
                + "CHECKIN_INTERVAL) VALUES (?, ?, ?, ?)", scheduler.getSchedulerName(), instance, at.toEpochMilli(),
                intervalMillis);
    }

    /**
     * A clock the test moves.
     */
    static final class MovableClock extends Clock {

        private volatile Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * The fixture's own jobs and the run observer a Quartz service registers.
     */
    @Configuration(proxyBeanMethods = false)
    static class Jobs {

        /** While set, {@value #FLAKY_JOB} fails. */
        static final AtomicBoolean FLAKY = new AtomicBoolean();

        @Bean
        QuartzSchedulerEngine.Ownership quartzRunOwnership(JdbcTemplate jdbc) {
            return new QuartzSchedulerEngine.Ownership(new JdbcExecutionLease(jdbc, "ADHAR_BATCH_EXECUTION_LEASE"));
        }

        /** Identified by its scheduled business date and a region, recording both. */
        @Bean
        @AdharBatchJob(description = "A report per business date", parameters = {
                @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
                @JobParam(name = "region", defaultValue = "sg")})
        Job datedJob(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return new JobBuilder(DATED_JOB, repository).start(steps.tasklet("datedStep", (contribution, context) -> {
                var parameters = context.getStepContext().getStepExecution().getJobExecution().getJobParameters();
                jdbc.update("INSERT INTO SLOT_RUNS (RUNNER, SCHEDULED_TIME) VALUES (?, ?)", DATED_JOB,
                        parameters.getParameter("businessDate").value() + "/" + parameters.getString("region"));
                return RepeatStatus.FINISHED;
            })).build();
        }

        /** Fails while {@link #FLAKY} is set. */
        @Bean
        Job flakyJob(JobRepository repository, AdharSteps steps, JdbcTemplate jdbc) {
            return new JobBuilder(FLAKY_JOB, repository).start(steps.tasklet("flakyStep", (contribution, context) -> {
                if (FLAKY.get()) {
                    throw new IllegalStateException("the feed has not arrived");
                }
                jdbc.update("INSERT INTO SLOT_RUNS (RUNNER, SCHEDULED_TIME) VALUES (?, ?)", FLAKY_JOB, "ran");
                return RepeatStatus.FINISHED;
            })).build();
        }

        /** Has an incrementer, so it cannot be scheduled. */
        @Bean
        Job counterJob(JobRepository repository, AdharSteps steps) {
            return new JobBuilder(COUNTER_JOB, repository).incrementer(new RunIdIncrementer())
                    .start(steps.tasklet("counterStep", (contribution, context) -> RepeatStatus.FINISHED)).build();
        }
    }
}
