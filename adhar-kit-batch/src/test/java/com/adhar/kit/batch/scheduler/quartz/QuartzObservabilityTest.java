package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEvent;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.RunIdentity;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter.Report;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import com.adhar.kit.batch.telemetry.BatchMdc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-Q.7 (design 0025 §5.20.8) on the engine {@code quartz}: what a fire records beside the run
 * (its meter, the next fire, the MDC, its {@code Skipped}/{@code Recovered} event with the run
 * name) and the schedule report built from the Quartz store and the JobRepository.
 */
class QuartzObservabilityTest extends QuartzEngineFixture {

    private static final String EVERY_FIVE_MINUTES = "0 0/5 * * * ?";
    private static final Instant FIVE_PAST = NOON.plus(Duration.ofMinutes(5));

    private final List<BatchRunEvent> events = new CopyOnWriteArrayList<>();
    private final QuartzCalendarsTest.RecordingMetrics metrics = new QuartzCalendarsTest.RecordingMetrics();

    @BeforeEach
    void noMdc() {
        MDC.clear();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private void observed() {
        StaticListableBeanFactory registry = new StaticListableBeanFactory();
        registry.addBean("specs", specs);
        engine.observe(metrics, null, new BatchRunEventPublisher(event -> events.add((BatchRunEvent) event),
                registry.getBeanProvider(JobParameterSpecRegistry.class)));
    }

    private SlotOutcome launch(org.quartz.JobExecutionContext context) {
        return new BatchLaunchJob(engine).launch(context);
    }

    private <T extends BatchRunEvent> List<T> events(Class<T> type) {
        List<T> found = new ArrayList<>();
        events.stream().filter(type::isInstance).map(type::cast).forEach(found::add);
        return found;
    }

    @Test
    @DisplayName("a fire counts its outcome under the job's schedule name and trigger, and leaves the MDC as it found it")
    void aFireIsCountedAndTheMdcRestored() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        observed();
        engine.start();
        clock.set(FIVE_PAST);
        MDC.put(BatchMdc.RUN, "the caller's");

        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.RAN);
        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.ALREADY_RAN);

        assertThat(metrics.fires).containsExactly("cron-latest/tick/ran", "cron-latest/tick/skipped-complete");
        assertThat(metrics.nextFires).as("the stored trigger's next fire, which a test's own fires do not move")
                .containsEntry("cron-latest/tick", NOON);
        assertThat(MDC.get(BatchMdc.RUN)).isEqualTo("the caller's");
        assertThat(MDC.get(BatchMdc.TRIGGER)).isNull();
        JobExecution run = executions("cronLatest").get(0);
        String runName = RunIdentity.runName("cron-latest",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T12:05:00Z"));
        assertThat(run.getJobParameters().getString(BatchScheduler.RUN_PARAMETER)).isEqualTo(runName);

        List<BatchRunEvent.Skipped> skipped = events(BatchRunEvent.Skipped.class);
        assertThat(skipped).singleElement().satisfies(event -> {
            assertThat(event.getReason()).isEqualTo("already-complete");
            assertThat(event.getRunName()).isEqualTo(runName);
            assertThat(event.getExecutionId()).isEqualTo(run.getId());
        });
        assertThat(events(BatchRunEvent.Completed.class)).as("the run's own events come from the kit's listener")
                .isEmpty();
    }

    @Test
    @DisplayName("a live owner's run is a Skipped(duplicate); a recovery fire of it is Recovered, both with the run name")
    void duplicateAndRecoveredEvents() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        observed();
        clock.set(FIVE_PAST);
        JobParameters identity = new JobParametersBuilder()
                .addString(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-06T12:05:00Z")
                .addString(BatchScheduler.RUN_PARAMETER, "cron-latest-left", false).toJobParameters();
        JobInstance instance = repository.createJobInstance("cronLatest", identity);
        JobExecution left = repository.createJobExecution(instance, identity, new ExecutionContext());

        assertThat(launch(fire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.DUPLICATE);
        assertThat(launch(recoveryFire(stored("cronLatest"), FIVE_PAST))).isEqualTo(SlotOutcome.RECOVERED);

        assertThat(events(BatchRunEvent.Skipped.class)).singleElement().satisfies(event -> {
            assertThat(event.getReason()).isEqualTo("duplicate");
            assertThat(event.getExecutionId()).isEqualTo(left.getId());
            assertThat(event.getRunName()).startsWith("cron-latest-");
        });
        assertThat(events(BatchRunEvent.Recovered.class)).singleElement().satisfies(event -> {
            assertThat(event.getExecutionId()).isEqualTo(left.getId());
            assertThat(event.getReason()).contains("Quartz re-fired");
            assertThat(event.getRunName()).isEqualTo("cron-latest-left");
        });
        assertThat(metrics.fires).containsExactly("cron-latest/tick/skipped-duplicate", "cron-latest/tick/recovered");
    }

    @Test
    @DisplayName("the report: every schedulable job with its triggers by kind, the last run, and the replicas checked in")
    void theReport() throws Exception {
        BatchProperties.Quartz.TriggerSettings nightly = configure("cronLatest", "nightly");
        nightly.setExpression("0 30 1 ? * *");
        nightly.setTimeZone(ZoneId.of("UTC"));
        BatchProperties.Quartz.TriggerSettings every = configure("intervalLatest", "every");
        every.setKind(BatchProperties.Quartz.TriggerSettings.Kind.INTERVAL);
        every.setEvery(Duration.ofMinutes(5));
        every.setMisfire(BatchProperties.Quartz.Misfire.ALL);
        BatchProperties.Quartz.TriggerSettings office = configure("cronSkip", "office");
        office.setKind(BatchProperties.Quartz.TriggerSettings.Kind.DAILY);
        office.setEvery(Duration.ofMinutes(15));
        office.setFrom(LocalTime.of(9, 0));
        office.setTo(LocalTime.of(17, 30));
        office.setDays(List.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY));
        office.setTimeZone(ZoneId.systemDefault());
        reconciled();
        observed();
        new BatchTriggers(engine).fireAt(FLAKY_JOB, NOON.plusSeconds(3600), Map.of());
        checkIn("replica-a", NOON.minusSeconds(5), 15_000);
        checkIn("replica-gone", NOON.minusSeconds(600), 15_000);
        clock.set(Instant.parse("2026-11-07T01:30:00Z"));
        launch(fire(stored("cronLatest", "nightly"), Instant.parse("2026-11-07T01:30:00Z")));
        clock.set(NOON);

        Report report = engine.buildReport();

        assertThat(report.v()).isEqualTo(1);
        assertThat(report.engine()).isEqualTo("quartz");
        assertThat(report.scheduler()).isEqualTo(scheduler.getSchedulerName());
        assertThat(report.updated()).isEqualTo(NOON);
        assertThat(report.instances()).extracting(ScheduleReportWriter.Instance::id)
                .containsExactly("replica-a", "replica-gone");
        Map<String, ScheduleReportWriter.Job> jobs = new java.util.TreeMap<>();
        report.jobs().forEach(job -> jobs.put(job.job(), job));
        assertThat(jobs).doesNotContainKey(COUNTER_JOB).containsKeys(DATED_JOB, "cronLatest", FLAKY_JOB);

        ScheduleReportWriter.Trigger cron = jobs.get("cronLatest").triggers().get(0);
        assertThat(cron.key()).isEqualTo("adhar-batch-config.cron-latest.nightly");
        assertThat(cron.kind()).isEqualTo("cron");
        assertThat(cron.expression()).isEqualTo("0 30 1 ? * *");
        assertThat(cron.timeZone()).isEqualTo("UTC");
        assertThat(cron.misfire()).isEqualTo("latest");
        assertThat(cron.state()).isEqualTo("NORMAL");
        assertThat(cron.next()).isEqualTo(Instant.parse("2026-11-07T01:30:00Z"));
        ScheduleReportWriter.Trigger interval = jobs.get("intervalLatest").triggers().get(0);
        assertThat(interval.kind()).isEqualTo("interval");
        assertThat(interval.every()).isEqualTo("PT5M");
        assertThat(interval.start()).isEqualTo(Instant.EPOCH);
        assertThat(interval.misfire()).isEqualTo("all");
        ScheduleReportWriter.Trigger daily = jobs.get("cronSkip").triggers().get(0);
        assertThat(daily.kind()).isEqualTo("daily");
        assertThat(daily.every()).isEqualTo("PT15M");
        assertThat(daily.from()).isEqualTo("09:00");
        assertThat(daily.to()).isEqualTo("17:30");
        assertThat(daily.days()).containsExactly("MON", "FRI");
        ScheduleReportWriter.Trigger runtime = jobs.get(FLAKY_JOB).triggers().get(0);
        assertThat(runtime.kind()).isEqualTo("at");
        assertThat(runtime.key()).startsWith("adhar-batch-runtime.flaky-job-");

        ScheduleReportWriter.LastRun last = jobs.get("cronLatest").lastRun();
        assertThat(last.run()).isEqualTo(RunIdentity.runName("cron-latest",
                Map.of(BatchScheduler.SCHEDULED_TIME_PARAMETER, "2026-11-07T01:30:00Z")));
        assertThat(last.status()).isEqualTo("COMPLETED");
        assertThat(last.outcome()).isEqualTo("completed");
        assertThat(last.trigger()).isEqualTo("adhar-batch-config.cron-latest.nightly");
        assertThat(last.start()).isNotNull();
        assertThat(last.end()).isNotNull();
        assertThat(jobs.get(DATED_JOB).lastRun()).as("a job that never ran").isNull();

        engine.triggersChanged();
        assertThat(metrics.instances).as("only fresh check-ins count").isEqualTo(1);
    }

    @Test
    @DisplayName("the report is on in Kubernetes, off outside it, and named after the scheduler unless named")
    void theReportWriter() {
        BatchProperties.Quartz.Report settings = new BatchProperties.Quartz.Report();
        Optional<KubernetesOwnerLookup.ApiAccess> inCluster = Optional.of(new KubernetesOwnerLookup.ApiAccess(
                URI.create("https://10.0.0.1:443"), () -> "t", "team-a", HttpClient.newHttpClient()));
        newEngine();

        ScheduleReportWriter writer = QuartzConfiguration.reportWriter(settings, "importer", "importer", engine,
                metrics, Clock.systemUTC(), inCluster);
        assertThat(writer.name()).isEqualTo("importer-batch-schedules");
        assertThat(writer.isEnabled()).isTrue();
        writer.close();

        assertThat(QuartzConfiguration.reportWriter(settings, "importer", "importer", engine, metrics,
                Clock.systemUTC(), Optional.empty())).as("outside Kubernetes, by default").isNull();
        settings.setEnabled(true);
        assertThat(QuartzConfiguration.reportWriter(settings, "importer", "importer", engine, metrics,
                Clock.systemUTC(), Optional.empty())).as("asked for, but nowhere to write it").isNull();
        settings.setName("schedules");
        ScheduleReportWriter named = QuartzConfiguration.reportWriter(settings, "importer", "importer", engine,
                metrics, Clock.systemUTC(), inCluster);
        assertThat(named.name()).isEqualTo("schedules");
        named.close();
        settings.setEnabled(false);
        assertThat(QuartzConfiguration.reportWriter(settings, "importer", "importer", engine, metrics,
                Clock.systemUTC(), inCluster)).as("switched off").isNull();
    }

    @Test
    @DisplayName("trigger changes and fires ask the writer for a report; destroying the engine closes it")
    void reportsAreRequested() throws Exception {
        cron("cronLatest", EVERY_FIVE_MINUTES);
        reconciled();
        ScheduleReportWriter writer = org.mockito.Mockito.mock(ScheduleReportWriter.class);
        engine.observe(SchedulerMetrics.none(), writer, null);

        engine.start();
        engine.scheduleJob("flakyJob", "0 0 6 * * *");
        engine.cancelScheduledJob("flakyJob");
        clock.set(FIVE_PAST);
        launch(fire(stored("cronLatest"), FIVE_PAST));
        engine.destroy();

        org.mockito.Mockito.verify(writer, org.mockito.Mockito.times(4)).request();
        org.mockito.Mockito.verify(writer).close();
    }

    private org.quartz.Trigger stored(String bean, String name) throws Exception {
        return scheduler.getTrigger(new org.quartz.TriggerKey(engine.scheduleNameOf(bean) + "." + name,
                QuartzSchedulerEngine.CONFIG_GROUP));
    }
}
