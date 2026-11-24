package com.adhar.kit.batch.scheduler.quartz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adhar.kit.batch.scheduler.MicrometerSchedulerMetrics;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.TriggerKey;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.5 and R12 (design 0025 §5.20.6): an {@code adhar.batch.scheduler.jobs} schedule whose Quartz
 * conversion fires elsewhere only at a clock change starts, with one warning and one
 * {@value SchedulerMetrics#CRON_DST_DIFFERENCES} count per zone (decided 2026-11-22, replacing
 * "fail"); one that cannot be converted still fails the start, or is refused to a run-time caller.
 */
class SpringCronStartTest extends QuartzEngineFixture {

    private static final String NIGHTLY = "0 30 1 * * *";

    /** The schedule name of job {@code cronLatest}: its trigger's key and its meters' {@code job} tag. */
    private static final String SCHEDULE_NAME = "cron-latest";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(QuartzSchedulerEngine.class);

    @BeforeEach
    void captureTheEnginesWarnings() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseTheEnginesLogger() {
        logger.detachAppender(appender);
    }

    private QuartzSchedulerEngine reconciledWithMeters() {
        newEngine().observe(new MicrometerSchedulerMetrics(registry), null, null);
        engine.afterSingletonsInstantiated();
        return engine;
    }

    private List<String> warnings() {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).filter(message -> message.contains("daylight-saving")).toList();
    }

    private double differences(String job) {
        Counter counter = registry.find(SchedulerMetrics.CRON_DST_DIFFERENCES).tag("job", job).counter();
        return counter == null ? 0 : counter.count();
    }

    private String storedExpression(String job) throws Exception {
        return ((CronTrigger) scheduler.getTrigger(TriggerKey.triggerKey(job + "."
                + QuartzSchedulerEngine.SCHEDULE_TRIGGER, QuartzSchedulerEngine.CONFIG_GROUP))).getCronExpression();
    }

    @Test
    @DisplayName("a nightly 01:30 starts, with ONE warning naming each zone, its date, both fire times and the remedies")
    void aNightlyInTheRepeatedHourStartsWithAWarning() throws Exception {
        properties.getScheduler().getJobs().put("cronLatest", NIGHTLY);

        reconciledWithMeters();

        assertThat(storedExpression(SCHEDULE_NAME)).isEqualTo("0 30 1 * * ?");
        assertThat(warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("'0 30 1 * * *' of job [cronLatest]")
                .contains("in America/New_York on 2026-11-01 Spring fires at 2026-11-01T01:30-04:00, "
                        + "Quartz at 2026-11-01T01:30-05:00")
                .contains("in Europe/London on 2026-10-25 Spring fires at 2026-10-25T01:30+01:00, "
                        + "Quartz at 2026-10-25T01:30Z")
                .doesNotContain("Australia/Sydney")
                .contains("This service schedules in Z, which has no clock changes, so its own fires are unaffected")
                .contains("move it out of 01:00-02:59")
                .contains("adhar.batch.quartz.triggers cron trigger in Quartz syntax"));
        assertThat(differences(SCHEDULE_NAME)).as("one per zone it moves in").isEqualTo(2.0);
    }

    @Test
    @DisplayName("a nightly 02:30 starts, warning about Sydney's repeated hour; the skipped hour elsewhere agrees")
    void aNightlyInTheSkippedHourStartsWithAWarning() throws Exception {
        properties.getScheduler().getJobs().put("cronLatest", "0 30 2 * * *");

        reconciledWithMeters();

        assertThat(storedExpression(SCHEDULE_NAME)).isEqualTo("0 30 2 * * ?");
        assertThat(warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("in Australia/Sydney on 2026-04-05").doesNotContain("America/New_York"));
        assertThat(differences(SCHEDULE_NAME)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a service in a zone where the hour moves is told that switching engine moves its own fire")
    void aServiceInAnAffectedZoneIsToldItsFireMoves() {
        properties.getScheduler().setTimeZone(ZoneId.of("America/New_York"));
        properties.getScheduler().getJobs().put("cronLatest", NIGHTLY);

        reconciledWithMeters();

        assertThat(warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("This service schedules in America/New_York: the quartz engine fires it at Quartz's time")
                .contains("switching adhar.batch.scheduler.engine to simple moves that fire to Spring's"));
    }

    @Test
    @DisplayName("a service in a daylight-saving zone where the two agree is told its own fires are unaffected")
    void aServiceInAnUnaffectedDaylightSavingZone() {
        properties.getScheduler().setTimeZone(ZoneId.of("Europe/Berlin"));
        properties.getScheduler().getJobs().put("cronLatest", NIGHTLY);

        reconciledWithMeters();

        assertThat(warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("This service schedules in Europe/Berlin, where the two agree")
                .doesNotContain("in Europe/Berlin on"));
    }

    @Test
    @DisplayName("a service in Singapore, which has no clock changes, starts and is told its own fires are unaffected")
    void aServiceWithoutClockChanges() {
        properties.getScheduler().setTimeZone(ZoneId.of("Asia/Singapore"));
        properties.getScheduler().getJobs().put("cronLatest", NIGHTLY);

        reconciledWithMeters();

        assertThat(warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains("This service schedules in Asia/Singapore, which has no clock changes"));
    }

    @Test
    @DisplayName("a schedule that never fires between 01:00 and 02:59 starts without a warning or a count")
    void aScheduleOutsideTheChangeHoursIsSilent() throws Exception {
        properties.getScheduler().getJobs().put("cronLatest", "0 30 4 * * *");

        reconciledWithMeters();

        assertThat(storedExpression(SCHEDULE_NAME)).isEqualTo("0 30 4 * * ?");
        assertThat(warnings()).isEmpty();
        assertThat(registry.find(SchedulerMetrics.CRON_DST_DIFFERENCES).counters()).isEmpty();
    }

    @Test
    @DisplayName("without a meter registry the start still succeeds and still warns")
    void withoutARegistryTheStartStillWarns() throws Exception {
        properties.getScheduler().getJobs().put("cronLatest", NIGHTLY);

        reconciled();

        assertThat(storedExpression(SCHEDULE_NAME)).isEqualTo("0 30 1 * * ?");
        assertThat(warnings()).hasSize(1);
    }

    @Test
    @DisplayName("scheduled at run time, the same schedule is stored, with the same warning")
    void aClockChangeScheduleAtRunTimeIsStoredWithAWarning() throws Exception {
        reconciledWithMeters();

        engine.scheduleJob("cronLatest", NIGHTLY);

        assertThat(engine.listScheduledJobs()).hasSize(1);
        assertThat(warnings()).hasSize(1);
        assertThat(differences(SCHEDULE_NAME)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("a schedule Quartz cannot express still fails the start")
    void anUnconvertibleScheduleStillFails() {
        properties.getScheduler().getJobs().put("cronLatest", "0 0 12 13 * FRI");

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restricts both the day of the month and the day of the week");
        engine = null;
    }
}
