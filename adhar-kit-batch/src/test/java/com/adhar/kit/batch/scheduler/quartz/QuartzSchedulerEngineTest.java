package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.config.BatchProperties.Quartz.Misfire;
import com.adhar.kit.batch.config.BatchProperties.Quartz.TriggerSettings;
import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.DailyTimeIntervalTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KIT-Q.3 (design 0025 §5.20.5, §5.20.6): the engine {@code quartz} around the launch. The
 * reconcile of JobDetails and configured triggers, the trigger kinds and their misfire
 * instructions, the public API of {@code BatchScheduler}, the owner check, and the shutdown that
 * stops a run and re-queues it as a resume trigger. A real Quartz scheduler on its in-memory store
 * and real Spring Batch jobs on H2 (see {@link QuartzEngineFixture}).
 */
class QuartzSchedulerEngineTest extends QuartzEngineFixture {

    private static final String EVERY_FIVE_MINUTES = "0 0/5 * * * ?";
    private static final Instant FIVE_PAST = NOON.plus(Duration.ofMinutes(5));

    private TriggerSettings settings(TriggerSettings.Kind kind) {
        TriggerSettings settings = new TriggerSettings();
        settings.setName("tick");
        settings.setKind(kind);
        return settings;
    }

    // ------------------------------------------------------------------
    // The reconcile
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every job without an incrementer gets one durable, recoverable JobDetail named by its schedule name")
    void aJobDetailPerJob() throws Exception {
        properties.getQuartz().getJobs().put("cronSkip", jobNamed("nightly-skip"));
        reconciled();

        JobDetail detail = scheduler.getJobDetail(new JobKey("import-job", QuartzSchedulerEngine.JOB_GROUP));
        assertThat(detail.getJobClass()).isEqualTo(BatchLaunchJob.class);
        assertThat(detail.isDurable()).isTrue();
        assertThat(detail.requestsRecovery()).isTrue();
        assertThat(detail.isConcurrentExecutionDisallowed()).isTrue();
        assertThat(detail.getJobDataMap().getString(QuartzSchedulerEngine.JOB)).isEqualTo(TestJobs.IMPORT_JOB);
        assertThat(scheduler.checkExists(new JobKey("nightly-skip", QuartzSchedulerEngine.JOB_GROUP))).isTrue();
        assertThat(scheduler.checkExists(new JobKey("cron-skip", QuartzSchedulerEngine.JOB_GROUP))).isFalse();
        assertThat(scheduler.checkExists(new JobKey("counter-job", QuartzSchedulerEngine.JOB_GROUP)))
                .as("a job with an incrementer is not schedulable").isFalse();
        assertThat(engine.name()).isEqualTo("quartz");
    }

    private static BatchProperties.Quartz.JobSettings jobNamed(String name) {
        BatchProperties.Quartz.JobSettings settings = new BatchProperties.Quartz.JobSettings();
        settings.setName(name);
        return settings;
    }

    @Test
    @DisplayName("configured triggers and the simple engine's cron schedules land in the config group")
    void configuredTriggersAreStored() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        properties.getScheduler().getJobs().put(TestJobs.IMPORT_JOB, "0 */10 * * * MON-FRI");
        reconciled();

        assertThat(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.CONFIG_GROUP)))
                .containsExactlyInAnyOrder(new TriggerKey("cron-latest.tick", QuartzSchedulerEngine.CONFIG_GROUP),
                        new TriggerKey("import-job.schedule", QuartzSchedulerEngine.CONFIG_GROUP));
        CronTrigger converted = (CronTrigger) scheduler.getTrigger(
                new TriggerKey("import-job.schedule", QuartzSchedulerEngine.CONFIG_GROUP));
        assertThat(converted.getCronExpression()).isEqualTo("0 */10 * ? * MON-FRI");
        assertThat(converted.getJobKey()).isEqualTo(new JobKey("import-job", QuartzSchedulerEngine.JOB_GROUP));
        assertThat(engine.listScheduledJobs()).containsExactly("cronLatest", TestJobs.IMPORT_JOB);
    }

    @Test
    @DisplayName("a restart keeps an unchanged trigger as stored, replaces a changed one and removes one no longer configured")
    void theReconcileFollowsTheProperties() throws Exception {
        interval("intervalLatest", "PT5M");
        interval("intervalSkip", "PT5M");
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        scheduler.scheduleJob(QuartzSchedulerEngine.oneShot(new TriggerKey("pending", QuartzSchedulerEngine.RUNTIME_GROUP),
                engine.jobKey("cronSkip"), data, NOON.plus(Duration.ofDays(1))));

        // The next start, 32 minutes later: one trigger as it was, one with another period, one gone.
        clock.set(NOON.plus(Duration.ofMinutes(32)));
        properties.getQuartz().getTriggers().clear();
        interval("intervalLatest", "PT5M");
        interval("intervalSkip", "PT10M");
        reconciled();

        assertThat(stored("intervalLatest").getStartTime().toInstant())
                .as("kept: a misfire waiting in the store survives the restart that handles it").isEqualTo(FIVE_PAST);
        assertThat(stored("intervalSkip").getStartTime().toInstant()).isEqualTo(NOON.plus(Duration.ofMinutes(40)));
        assertThat(((SimpleTrigger) stored("intervalSkip")).getRepeatInterval()).isEqualTo(600_000);
        assertThat(scheduler.checkExists(configKey("cronLatest"))).isFalse();
        assertThat(scheduler.checkExists(new TriggerKey("pending", QuartzSchedulerEngine.RUNTIME_GROUP)))
                .as("the reconcile never touches the runtime group").isTrue();
    }

    @Test
    @DisplayName("a trigger without a name, or two of one name, fail the start naming the job")
    void triggerNamesAreRequiredAndUnique() {
        configure("cronSkip", " ").setExpression(EVERY_FIVE_MINUTES);
        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.triggers.cronSkip").hasMessageContaining("needs a name");

        properties.getQuartz().getTriggers().clear();
        cron("cronSkip", EVERY_FIVE_MINUTES);
        cron("cronSkip", EVERY_FIVE_MINUTES);
        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names trigger 'tick' twice");
    }

    @Test
    @DisplayName("two jobs with one schedule name, or a name that is no DNS label, fail the start")
    void scheduleNamesAreDistinctLabels() {
        properties.getQuartz().getJobs().put("cronSkip", jobNamed("cron-latest"));
        assertThatThrownBy(this::newEngine).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cronSkip").hasMessageContaining("cronLatest")
                .hasMessageContaining("same schedule name 'cron-latest'");

        properties.getQuartz().getJobs().put("cronSkip", jobNamed("Nightly_Import"));
        assertThatThrownBy(this::newEngine).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.jobs.cronSkip.name").hasMessageContaining("DNS-1035");

        properties.getQuartz().getJobs().put("cronSkip", jobNamed("a".repeat(41)));
        assertThatThrownBy(this::newEngine).isInstanceOf(IllegalStateException.class).hasMessageContaining("at most 40");
    }

    @Test
    @DisplayName("the default schedule name is the bean name as a DNS label of at most 40 characters")
    void beanNamesBecomeDnsLabels() {
        assertThat(QuartzSchedulerEngine.dnsLabel("importJob")).isEqualTo("import-job");
        assertThat(QuartzSchedulerEngine.dnsLabel("daily_Report2Job")).isEqualTo("daily-report2-job");
        assertThat(QuartzSchedulerEngine.dnsLabel("9livesJob")).isEqualTo("lives-job");
        String longName = QuartzSchedulerEngine.dnsLabel("reconcileEveryLedgerAccountOfTheGroup-Nightly");
        assertThat(longName).hasSizeLessThanOrEqualTo(40).doesNotEndWith("-").startsWith("reconcile-every-ledger");
        assertThatThrownBy(() -> QuartzSchedulerEngine.dnsLabel("2026")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.jobs.2026.name");
    }

    @Test
    @DisplayName("a store that refuses the reconcile fails the start")
    void aFailingStoreFailsTheStart() throws Exception {
        Scheduler broken = mock(Scheduler.class);
        doThrow(new SchedulerException("the job store is down")).when(broken).addJob(any(), anyBoolean());
        scheduler = broken;

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not reconcile").hasMessageContaining("the job store is down");
        engine = null;
    }

    // ------------------------------------------------------------------
    // Trigger kinds and misfire policies
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a cron trigger carries its zone, priority, calendar and the misfire instruction of its policy")
    void cronTriggers() {
        newEngine();
        TriggerSettings settings = settings(TriggerSettings.Kind.CRON);
        settings.setExpression("0 30 6 ? * MON-FRI");
        settings.setTimeZone(ZoneId.of("Asia/Singapore"));
        settings.setPriority(9);
        settings.setCalendar("sg-business-days");

        CronTrigger latest = (CronTrigger) engine.configTrigger("cronLatest", settings, NOON);
        settings.setMisfire(Misfire.SKIP);
        Trigger skip = engine.configTrigger("cronLatest", settings, NOON);
        settings.setMisfire(Misfire.ALL);
        Trigger all = engine.configTrigger("cronLatest", settings, NOON);

        assertThat(latest.getKey()).isEqualTo(new TriggerKey("cron-latest.tick", QuartzSchedulerEngine.CONFIG_GROUP));
        assertThat(latest.getTimeZone().getID()).isEqualTo("Asia/Singapore");
        assertThat(latest.getPriority()).isEqualTo(9);
        assertThat(latest.getCalendarName()).isEqualTo("sg-business-days");
        assertThat(latest.getStartTime().toInstant()).isEqualTo(NOON);
        assertThat(latest.getJobDataMap().getString(QuartzSchedulerEngine.KIND)).isEqualTo("config");
        assertThat(latest.getJobDataMap().getString(QuartzSchedulerEngine.ZONE)).isEqualTo("Asia/Singapore");
        assertThat(latest.getJobDataMap().getString(QuartzSchedulerEngine.MISFIRE)).isEqualTo("latest");
        assertThat(latest.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(skip.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        assertThat(all.getMisfireInstruction()).isEqualTo(Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
    }

    @Test
    @DisplayName("an interval trigger starts on the first slot of its grid after now and records the grid, never startTime")
    void intervalTriggers() {
        newEngine();
        TriggerSettings settings = settings(TriggerSettings.Kind.INTERVAL);
        settings.setEvery(Duration.ofMinutes(15));
        settings.setStart(Instant.parse("2026-01-01T00:07:00Z"));

        SimpleTrigger latest = (SimpleTrigger) engine.configTrigger("intervalLatest", settings, NOON);
        settings.setMisfire(Misfire.SKIP);
        Trigger skip = engine.configTrigger("intervalLatest", settings, NOON);
        settings.setMisfire(Misfire.ALL);
        Trigger all = engine.configTrigger("intervalLatest", settings, NOON);

        assertThat(latest.getStartTime().toInstant()).isEqualTo(Instant.parse("2026-11-06T12:07:00Z"));
        assertThat(latest.getRepeatInterval()).isEqualTo(900_000);
        assertThat(latest.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
        assertThat(latest.getJobDataMap().getString(QuartzSchedulerEngine.GRID_START)).isEqualTo("2026-01-01T00:07:00Z");
        assertThat(latest.getJobDataMap().getString(QuartzSchedulerEngine.GRID_EVERY)).isEqualTo("PT15M");
        assertThat(latest.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT);
        assertThat(skip.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        assertThat(all.getMisfireInstruction()).isEqualTo(Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
    }

    @Test
    @DisplayName("a daily trigger maps its window, period and days of the week (Quartz numbers Sunday 1)")
    void dailyTriggers() {
        properties.getScheduler().setTimeZone(ZoneId.systemDefault());
        newEngine();
        TriggerSettings settings = settings(TriggerSettings.Kind.DAILY);
        settings.setEvery(Duration.ofMinutes(30));
        settings.setFrom(LocalTime.of(9, 0));
        settings.setTo(LocalTime.of(17, 30, 15));
        settings.setDays(List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

        DailyTimeIntervalTrigger latest = (DailyTimeIntervalTrigger) engine.configTrigger("cronLatest", settings, NOON);
        settings.setMisfire(Misfire.SKIP);
        Trigger skip = engine.configTrigger("cronLatest", settings, NOON);
        settings.setMisfire(Misfire.ALL);
        settings.setDays(List.of());
        DailyTimeIntervalTrigger all = (DailyTimeIntervalTrigger) engine.configTrigger("cronLatest", settings, NOON);

        assertThat(latest.getDaysOfWeek()).containsExactlyInAnyOrder(1, 2, 7);
        assertThat(latest.getStartTimeOfDay().getHour()).isEqualTo(9);
        assertThat(latest.getEndTimeOfDay().getMinute()).isEqualTo(30);
        assertThat(latest.getEndTimeOfDay().getSecond()).isEqualTo(15);
        assertThat(latest.getRepeatInterval()).isEqualTo(1800);
        assertThat(latest.getMisfireInstruction()).isEqualTo(DailyTimeIntervalTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(skip.getMisfireInstruction()).isEqualTo(DailyTimeIntervalTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        assertThat(all.getMisfireInstruction()).isEqualTo(Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
        assertThat(all.getDaysOfWeek()).as("no days means every day").containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("a daily trigger in a zone other than the JVM's is refused: Quartz computes its window in the JVM's zone")
    void aDailyTriggerRunsInTheJvmZone() {
        newEngine();
        TriggerSettings settings = settings(TriggerSettings.Kind.DAILY);
        settings.setEvery(Duration.ofMinutes(30));
        settings.setFrom(LocalTime.of(9, 0));
        settings.setTo(LocalTime.of(17, 0));
        settings.setTimeZone(ZoneId.of(ZoneId.systemDefault().getId().equals("Asia/Kolkata") ? "America/New_York"
                : "Asia/Kolkata"));

        assertThatThrownBy(() -> engine.configTrigger("cronLatest", settings, NOON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.triggers.cronLatest[tick].time-zone")
                .hasMessageContaining("JVM's default zone");

        settings.setTimeZone(ZoneId.systemDefault());
        settings.setTo(LocalTime.of(9, 0));
        assertThatThrownBy(() -> engine.configTrigger("cronLatest", settings, NOON))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("from before to");
    }

    @Test
    @DisplayName("an invalid expression, period, job or parameter set fails naming the trigger")
    void invalidDefinitionsAreRefused() {
        newEngine();
        TriggerSettings cron = settings(TriggerSettings.Kind.CRON);
        cron.setExpression("0 */5 * * * *");
        assertThatThrownBy(() -> engine.configTrigger("cronLatest", cron, NOON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.triggers.cronLatest[tick].expression")
                .hasMessageContaining("'0 */5 * * * *'");
        cron.setExpression(null);
        assertThatThrownBy(() -> engine.configTrigger("cronLatest", cron, NOON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid Quartz cron expression");

        TriggerSettings interval = settings(TriggerSettings.Kind.INTERVAL);
        assertThatThrownBy(() -> engine.configTrigger("intervalLatest", interval, NOON))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(".every must be whole seconds");
        interval.setEvery(Duration.ofMillis(500));
        assertThatThrownBy(() -> engine.configTrigger("intervalLatest", interval, NOON))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("was PT0.5S");
        interval.setEvery(Duration.ofMillis(1500));
        assertThatThrownBy(() -> engine.configTrigger("intervalLatest", interval, NOON))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("was PT1.5S");

        TriggerSettings valid = settings(TriggerSettings.Kind.CRON);
        valid.setExpression(EVERY_FIVE_MINUTES);
        assertThatThrownBy(() -> engine.configTrigger("ghostJob", valid, NOON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no Job bean of that name").hasMessageContaining("importJob");
        assertThatThrownBy(() -> engine.configTrigger(COUNTER_JOB, valid, NOON)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JobParametersIncrementer");
        assertThatThrownBy(() -> engine.configTrigger(QuartzJobs.CHUNK_JOB, valid, NOON))
                .as("chunkJob requires 'run', which this trigger does not give")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("do not resolve for a slot").hasMessageContaining("run");

        valid.getParameters().put("run", "nightly");
        Trigger trigger = engine.configTrigger(QuartzJobs.CHUNK_JOB, valid, NOON);
        assertThat(trigger.getJobDataMap().getString(QuartzSchedulerEngine.PARAMS)).isEqualTo("{\"run\":\"nightly\"}");
    }

    // ------------------------------------------------------------------
    // The public API of BatchScheduler
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a schedule added before the reconcile is stored by it; one added later is stored at once, and replaced when repeated")
    void scheduleJob() throws Exception {
        newEngine();
        engine.scheduleJob(TestJobs.IMPORT_JOB, "@hourly");
        assertThat(engine.listScheduledJobs()).containsExactly(TestJobs.IMPORT_JOB);
        assertThat(scheduler.getTriggerKeys(GroupMatcher.anyTriggerGroup())).isEmpty();

        engine.afterSingletonsInstantiated();
        TriggerKey key = new TriggerKey("import-job.schedule", QuartzSchedulerEngine.CONFIG_GROUP);
        assertThat(((CronTrigger) scheduler.getTrigger(key)).getCronExpression()).isEqualTo("0 0 * * * ?");

        engine.scheduleJob("cronSkip", "0 15 3 * * 1-5");
        assertThat(((CronTrigger) scheduler.getTrigger(new TriggerKey("cron-skip.schedule",
                QuartzSchedulerEngine.CONFIG_GROUP))).getCronExpression()).isEqualTo("0 15 3 ? * 2-6");
        engine.scheduleJob(TestJobs.IMPORT_JOB, "0 30 * * * *");
        assertThat(((CronTrigger) scheduler.getTrigger(key)).getCronExpression()).isEqualTo("0 30 * * * ?");
        assertThat(engine.listScheduledJobs()).containsExactly("cronSkip", TestJobs.IMPORT_JOB);
    }

    @Test
    @DisplayName("scheduling an unknown job, an unschedulable job or an invalid expression is the caller's error")
    void scheduleJobRefusals() {
        reconciled();

        assertThatThrownBy(() -> engine.scheduleJob("ghostJob", "@daily")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No job named [ghostJob]");
        assertThatThrownBy(() -> engine.scheduleJob(COUNTER_JOB, "@daily")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobParametersIncrementer");
        assertThatThrownBy(() -> engine.scheduleJob(TestJobs.IMPORT_JOB, "every day")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cron expression 'every day'");
    }

    @Test
    @DisplayName("cancelling removes a job's configured triggers, pending or stored, and only those")
    void cancelScheduledJob() throws Exception {
        newEngine();
        engine.scheduleJob(TestJobs.IMPORT_JOB, "@hourly");
        assertThat(engine.cancelScheduledJob(TestJobs.IMPORT_JOB)).isTrue();
        assertThat(engine.cancelScheduledJob(TestJobs.IMPORT_JOB)).isFalse();
        assertThat(engine.cancelScheduledJob("ghostJob")).isFalse();

        engine.afterSingletonsInstantiated();
        engine.scheduleJob(TestJobs.IMPORT_JOB, "@hourly");
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        scheduler.scheduleJob(QuartzSchedulerEngine.oneShot(new TriggerKey("pending", QuartzSchedulerEngine.RUNTIME_GROUP),
                engine.jobKey(TestJobs.IMPORT_JOB), data, NOON.plus(Duration.ofDays(1))));

        assertThat(engine.cancelScheduledJob(TestJobs.IMPORT_JOB)).isTrue();
        assertThat(engine.cancelScheduledJob(TestJobs.IMPORT_JOB)).isFalse();

        assertThat(engine.listScheduledJobs()).isEmpty();
        assertThat(scheduler.checkExists(new TriggerKey("pending", QuartzSchedulerEngine.RUNTIME_GROUP))).isTrue();
    }

    @Test
    @DisplayName("the next fire time is the earliest of the job's triggers, once the engine runs")
    void nextFireTime() {
        cron("cronLatest", "0 4/5 * * * ?");
        reconciled();
        assertThat(engine.nextFireTime("cronLatest")).as("not started").isEmpty();
        assertThat(engine.isRunning()).isFalse();

        engine.start();
        engine.start();
        engine.scheduleJob("cronLatest", "0 3 * * * *");

        assertThat(engine.isRunning()).isTrue();
        assertThat(engine.nextFireTime("cronLatest")).contains(NOON.plus(Duration.ofMinutes(3)));
        assertThat(engine.nextFireTime("cronSkip")).as("no trigger").isEmpty();
        assertThat(engine.nextFireTime("ghostJob")).isEmpty();

        engine.stop();
        engine.stop();
        assertThat(engine.isRunning()).isFalse();
    }

    @Test
    @DisplayName("a store that fails turns the API's calls into IllegalStateExceptions naming what was asked")
    void aFailingStoreFailsTheCalls() throws Exception {
        reconciled();
        engine.start();
        Scheduler broken = mock(Scheduler.class);
        SchedulerException down = new SchedulerException("the job store is down");
        when(broken.checkExists(any(TriggerKey.class))).thenThrow(down);
        when(broken.getTriggersOfJob(any())).thenThrow(down);
        when(broken.getTriggerKeys(any())).thenThrow(down);
        when(broken.getSchedulerInstanceId()).thenThrow(down);
        when(broken.getSchedulerName()).thenThrow(down);
        doThrow(down).when(broken).standby();
        QuartzSchedulerEngine healthy = engine;
        scheduler = broken;
        newEngine();
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "reconciled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "started", true);

        assertThatThrownBy(() -> engine.scheduleJob(TestJobs.IMPORT_JOB, "@daily")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not schedule job [importJob]");
        assertThatThrownBy(() -> engine.cancelScheduledJob(TestJobs.IMPORT_JOB)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not cancel the schedule of job [importJob]");
        assertThatThrownBy(() -> engine.listScheduledJobs()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not list the schedules");
        assertThatThrownBy(() -> engine.nextFireTime(TestJobs.IMPORT_JOB)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not read the triggers of job [importJob]");
        assertThatThrownBy(() -> engine.instanceId()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Quartz instance id");
        assertThatThrownBy(() -> engine.schedulerName()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Quartz scheduler name");
        // Neither a failing standby nor a failing re-queue may stop the shutdown.
        JobExecution execution = new JobExecution(1L, new org.springframework.batch.core.job.JobInstance(1L, "importJob"),
                new JobParameters());
        assertThatCode(() -> engine.requeue(new QuartzSchedulerEngine.LocalRun(TestJobs.IMPORT_JOB, "importJob", "run-1",
                ZoneId.of("UTC")), execution)).doesNotThrowAnyException();
        assertThatCode(() -> engine.stop()).doesNotThrowAnyException();
        assertThat(engine.isRunning()).isFalse();
        engine = healthy;
    }

    // ------------------------------------------------------------------
    // The owner check
    // ------------------------------------------------------------------

    @Test
    @DisplayName("an instance is alive within two check-in intervals plus Quartz's tolerance of its last check-in")
    void theOwnerCheck() throws Exception {
        reconciled();
        clock.set(NOON);
        checkIn("replica-b", NOON.minusMillis(37_500), 15_000);
        assertThat(engine.instanceAlive("replica-b")).as("at the margin").isTrue();

        checkIn("replica-b", NOON.minusMillis(37_501), 15_000);
        assertThat(engine.instanceAlive("replica-b")).as("one millisecond beyond it").isFalse();

        checkIn("replica-b", NOON.plusSeconds(20), 15_000);
        assertThat(engine.instanceAlive("replica-b")).as("a replica whose clock runs ahead").isTrue();
        assertThat(engine.instanceAlive("replica-c")).as("never checked in").isFalse();

        // Another service's scheduler on the same database is not this cluster.
        jdbc.update("UPDATE " + PREFIX + "SCHEDULER_STATE SET SCHED_NAME = 'another-service'");
        assertThat(engine.instanceAlive("replica-b")).isFalse();
    }

    @Test
    @DisplayName("when the check-in cannot be read the owner is treated as alive: a duplicate skip, never a double run")
    void anUnreadableCheckInMeansAlive() {
        engine = new QuartzSchedulerEngine(scheduler, operator, repository, beans.getBeansOfType(Job.class), specs,
                resolver, ownership, jdbc, "NO_SUCH_SCHEMA.QRTZ_", properties, null, clock);

        assertThat(engine.instanceAlive("replica-b")).isTrue();
    }

    // ------------------------------------------------------------------
    // Identities and trigger data
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a resume trigger's identity survives the store as text: typed, identifying only, in a stable order")
    void identitiesRoundTrip() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("region", "sg")
                .addLocalDate("businessDate", LocalDate.of(2026, 11, 6))
                .addLong("batch", 42L)
                .addString("adhar.run", "import-job-0a1b2c3d", false)
                .toJobParameters();

        String encoded = QuartzSchedulerEngine.encodeIdentity(parameters);
        JobParameters decoded = QuartzSchedulerEngine.decodeIdentity(encoded);

        assertThat(encoded).startsWith("{\"batch\":").doesNotContain("adhar.run");
        assertThat(decoded.parameters()).hasSize(3).allMatch(parameter -> parameter.identifying());
        assertThat(decoded.getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(decoded.getLong("batch")).isEqualTo(42L);
        assertThat(decoded.getString("region")).isEqualTo("sg");
        assertThat(new JobParameters(decoded.getIdentifyingParameters()))
                .as("the same JobInstance key").isEqualTo(new JobParameters(parameters.getIdentifyingParameters()));
    }

    @Test
    @DisplayName("trigger data is JSON of strings: sorted keys, numbers as written, nulls dropped")
    void triggerDataIsJsonOfStrings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("region", "s\"g");
        values.put("batch", "42");

        assertThat(QuartzSchedulerEngine.toJson(values)).isEqualTo("{\"batch\":\"42\",\"region\":\"s\\\"g\"}");
        assertThat(QuartzSchedulerEngine.fromJson("{\"amount\":1250.50,\"dryRun\":true,\"region\":\"sg\",\"note\":null}"))
                .containsExactly(Map.entry("amount", "1250.50"), Map.entry("dryRun", "true"), Map.entry("region", "sg"));
        assertThat(QuartzSchedulerEngine.fromJson(null)).isEmpty();
        assertThat(QuartzSchedulerEngine.fromJson("  ")).isEmpty();
        assertThatThrownBy(() -> QuartzSchedulerEngine.fromJson("[1,2]")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    @DisplayName("a one-shot trigger fires even when it misfired: it stands for an event")
    void oneShotTriggersAlwaysFire() {
        Trigger trigger = QuartzSchedulerEngine.oneShot(new TriggerKey("run-1", QuartzSchedulerEngine.RUNTIME_GROUP),
                new JobKey("import-job", QuartzSchedulerEngine.JOB_GROUP), Map.of(QuartzSchedulerEngine.KIND, "now"), NOON);

        assertThat(trigger.getMisfireInstruction()).isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        assertThat(((SimpleTrigger) trigger).getRepeatCount()).isZero();
        assertThat(trigger.getStartTime().toInstant()).isEqualTo(NOON);
        assertThat(trigger.getJobDataMap().getString(QuartzSchedulerEngine.KIND)).isEqualTo("now");
    }

    // ------------------------------------------------------------------
    // Fires through Quartz, and the shutdown
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a configured trigger fired by Quartz runs its job with the slot it fired for")
    void quartzFiresAConfiguredTrigger() throws Exception {
        clock.set(Instant.now());
        cron("cronLatest", "* * * * * ?");
        reconciled();
        engine.start();

        scheduler.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> !slots("cronLatest").isEmpty());
        scheduler.standby();
        Instant slot = Instant.parse(slots("cronLatest").get(0));
        assertThat(slot).isEqualTo(slot.truncatedTo(ChronoUnit.SECONDS)).isAfterOrEqualTo(
                clock.instant().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("shutdown: standby, stop the run at its chunk boundary, re-queue it as a resume trigger that another replica completes")
    void aShutdownStopsAndRequeuesTheRun() throws Exception {
        reconciled();
        engine.start();
        scheduler.start();
        TestJobs.seed(jdbc, 35);
        gate.holdAt(15);
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        data.put(QuartzSchedulerEngine.PARAMS, "{\"run\":\"r1\"}");
        scheduler.scheduleJob(oneShot(QuartzJobs.CHUNK_JOB, "adhoc-r1", data));
        assertThat(gate.awaitHeld(60)).isTrue();
        assertThat(engine.runningJobs()).containsExactly(QuartzJobs.CHUNK_JOB);
        assertThat(jdbc.queryForList("SELECT OWNER_POD FROM ADHAR_BATCH_EXECUTION_LEASE", String.class))
                .as("the run's lease names this Quartz instance").containsExactly("replica-a");

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(engine::stop);
        // Spring Batch records the stop request at once (STOPPING with an end time is stored as
        // STOPPED); the run itself is still held in its chunk.
        await().atMost(60, TimeUnit.SECONDS).until(() -> executions(QuartzJobs.CHUNK_JOB).get(0).getStatus()
                != BatchStatus.STARTED);
        assertThat(engine.runningJobs()).as("still in its chunk").containsExactly(QuartzJobs.CHUNK_JOB);
        gate.release();
        shutdown.get(60, TimeUnit.SECONDS);

        JobExecution stopped = executions(QuartzJobs.CHUNK_JOB).get(0);
        String runName = RunIdentity.runName("chunk-job", Map.of("run", "r1"));
        assertThat(stopped.getStatus()).isEqualTo(BatchStatus.STOPPED);
        assertThat(scheduler.isInStandbyMode()).isTrue();
        assertThat(engine.runningJobs()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(20);
        Trigger resume = scheduler.getTrigger(new TriggerKey(runName, QuartzSchedulerEngine.RUNTIME_GROUP));
        assertThat(resume).as("the resume trigger, keyed by the run's name").isNotNull();
        assertThat(resume.getJobDataMap().getString(QuartzSchedulerEngine.KIND)).isEqualTo("resume");
        assertThat(resume.getJobDataMap().getString(QuartzSchedulerEngine.RUN)).isEqualTo(runName);
        assertThat(resume.getJobDataMap().getString(QuartzSchedulerEngine.IDENTITY)).isEqualTo("{\"run\":\"r1,java.lang.String,true\"}");
        assertThat(resume.getMisfireInstruction()).isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);

        // Storing it again (a second stop of the same run) keeps the one trigger.
        engine.requeue(new QuartzSchedulerEngine.LocalRun(QuartzJobs.CHUNK_JOB, QuartzJobs.CHUNK_JOB, runName,
                ZoneId.of("UTC")), stopped);
        assertThat(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.RUNTIME_GROUP)))
                .containsExactly(resume.getKey());

        // Another replica acquires the resume trigger.
        scheduler = newScheduler("replica-b");
        reconciled();
        assertThat(new BatchLaunchJob(engine).launch(fire(resume, clock.instant()))).isEqualTo(SlotOutcome.RAN);

        List<JobExecution> all = executions(QuartzJobs.CHUNK_JOB);
        assertThat(all).extracting(JobExecution::getStatus).containsExactly(BatchStatus.STOPPED, BatchStatus.COMPLETED);
        assertThat(all.get(1).getJobInstance().getId()).isEqualTo(stopped.getJobInstance().getId());
        assertThat(all.get(1).getJobParameters().getString(QuartzSchedulerEngine.INSTANCE_PARAMETER)).isEqualTo("replica-b");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(35);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class)).isEqualTo(35);
    }

    @Test
    @DisplayName("a run that does not stop within the shutdown timeout is left to Quartz's recovery, and the stop returns")
    void aRunThatDoesNotStopIsLeftToRecovery() throws Exception {
        properties.getShutdown().setTimeout(Duration.ofMillis(300));
        reconciled();
        engine.start();
        scheduler.start();
        TestJobs.seed(jdbc, 35);
        gate.holdAt(15);
        Map<String, String> data = new LinkedHashMap<>();
        data.put(QuartzSchedulerEngine.KIND, QuartzSchedulerEngine.KIND_NOW);
        data.put(QuartzSchedulerEngine.PARAMS, "{\"run\":\"r2\"}");
        scheduler.scheduleJob(oneShot(QuartzJobs.CHUNK_JOB, "adhoc-r2", data));
        assertThat(gate.awaitHeld(60)).isTrue();
        // The clock runs for this one: the timeout is the kit's own decision.
        AtomicBoolean stopped = new AtomicBoolean();
        CompletableFuture<Void> ticking = CompletableFuture.runAsync(() -> {
            while (!stopped.get()) {
                clock.set(clock.instant().plusMillis(100));
                Thread.onSpinWait();
            }
        });

        engine.stop();
        stopped.set(true);
        ticking.get(60, TimeUnit.SECONDS);

        assertThat(engine.runningJobs()).as("still held in its chunk").containsExactly(QuartzJobs.CHUNK_JOB);
        assertThat(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzSchedulerEngine.RUNTIME_GROUP)))
                .as("no resume trigger: the run did not record STOPPED")
                .doesNotContain(new TriggerKey(RunIdentity.runName("chunk-job", Map.of("run", "r2")),
                        QuartzSchedulerEngine.RUNTIME_GROUP));
        gate.release();
        await().atMost(60, TimeUnit.SECONDS).until(() -> engine.runningJobs().isEmpty());
    }

    @Test
    @DisplayName("the lease is written when the job starts on the launching thread, for this run's job only")
    void ownershipFollowsTheLaunchingThread() {
        FakeLease lease = new FakeLease();
        QuartzSchedulerEngine.Ownership own = new QuartzSchedulerEngine.Ownership(lease);
        QuartzSchedulerEngine.LocalRun run = new QuartzSchedulerEngine.LocalRun("importJob", "importJob", "run-1",
                ZoneId.of("UTC"));
        var owner = new com.adhar.kit.batch.runtime.ExecutionLease.Owner("replica-a", "", "");
        JobExecution other = new JobExecution(7L, new org.springframework.batch.core.job.JobInstance(1L, "otherJob"),
                new JobParameters());
        JobExecution mine = new JobExecution(8L, new org.springframework.batch.core.job.JobInstance(2L, "importJob"),
                new JobParameters());

        own.jobStarted(mine);
        assertThat(lease.owners).as("no launch on this thread").isEmpty();

        own.begin(run, owner);
        own.jobStarted(other);
        assertThat(lease.owners).as("another job started inside this run (a nested launch)").isEmpty();
        own.jobStarted(mine);
        own.claim(mine);
        assertThat(lease.owners).containsExactly(Map.entry(8L, "replica-a"));
        assertThat(run.executionId).isEqualTo(8L);
        assertThat(own.owner(8L)).isEqualTo("replica-a");
        assertThat(own.owner(99L)).isNull();

        own.release(8L);
        own.end();
        own.claim(other);
        assertThat(lease.owners).isEmpty();
    }

    @Test
    @DisplayName("a lease table that fails never fails the run: the owner reads as unknown")
    void aFailingLeaseNeverFailsTheRun() {
        FakeLease lease = new FakeLease();
        lease.broken = true;
        QuartzSchedulerEngine.Ownership own = new QuartzSchedulerEngine.Ownership(lease);
        QuartzSchedulerEngine.LocalRun run = new QuartzSchedulerEngine.LocalRun("importJob", "importJob", "run-1",
                ZoneId.of("UTC"));
        JobExecution mine = new JobExecution(8L, new org.springframework.batch.core.job.JobInstance(2L, "importJob"),
                new JobParameters());
        own.begin(run, new com.adhar.kit.batch.runtime.ExecutionLease.Owner("replica-a", "", ""));

        assertThatCode(() -> own.claim(mine)).doesNotThrowAnyException();
        assertThatCode(() -> own.release(8L)).doesNotThrowAnyException();
        assertThat(own.owner(8L)).isNull();
        assertThat(run.executionId).as("the run is still known, so a shutdown can stop it").isEqualTo(8L);
        own.end();
    }

    /** An execution lease in memory. */
    private static final class FakeLease implements com.adhar.kit.batch.runtime.ExecutionLease {

        final Map<Long, String> owners = new LinkedHashMap<>();
        boolean broken;

        private void check() {
            if (broken) {
                throw new IllegalStateException("the lease table is gone");
            }
        }

        @Override
        public void acquire(long executionId, Owner owner) {
            check();
            owners.put(executionId, owner.pod());
        }

        @Override
        public boolean heartbeat(long executionId, Owner owner, Instant lastCommit) {
            return true;
        }

        @Override
        public void release(long executionId) {
            check();
            owners.remove(executionId);
        }

        @Override
        public Optional<Lease> find(long executionId) {
            check();
            return Optional.ofNullable(owners.get(executionId)).map(pod -> new Lease(executionId, new Owner(pod, "", ""),
                    NOON, NOON, null, Duration.ZERO));
        }
    }
}
