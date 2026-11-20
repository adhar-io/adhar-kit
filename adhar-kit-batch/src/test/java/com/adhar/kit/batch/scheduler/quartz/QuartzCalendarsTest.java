package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.config.BatchProperties.Quartz.CalendarSettings;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import com.adhar.kit.batch.scheduler.SlotOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.quartz.spi.OperableTrigger;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.4 (design 0025 §5.20.6): business calendars on the engine {@code quartz}. AQ.5 (a holiday
 * never fires, a worked Saturday does, {@code next fire} agrees, a trigger in another zone than its
 * calendar fails the start) and FQ7 (an invalid calendar fails the start; on refresh it keeps the
 * previous one). Quartz computes the fire times with the calendar on its in-memory store.
 */
class QuartzCalendarsTest extends QuartzEngineFixture {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    /** Monday 2026-11-09 off, Saturday 2026-11-07 worked. NOON is Friday 2026-11-06, 20:00 in Singapore. */
    private static final String SG = """
            name: sg-business
            timeZone: Asia/Singapore
            holidays: [2026-11-09]
            businessDays: [2026-11-07]
            """;

    /**
     * The same week in 2030, for the refresh tests: Quartz recomputes a trigger's next fire for a
     * changed calendar from the system clock or the trigger's start, whichever is later, so the
     * trigger starts (by the kit's clock) after any day this test runs on.
     */
    private static final String SG_2030 = """
            name: sg-business
            timeZone: Asia/Singapore
            holidays: [2030-11-11]
            businessDays: [2030-11-09]
            """;

    private static final Instant FRIDAY_2030 = Instant.parse("2030-11-08T12:00:00Z");

    @TempDir
    Path dir;

    private Path calendarFile(String yaml) throws Exception {
        return Files.writeString(dir.resolve("calendar.yaml"), yaml);
    }

    private void useCalendarFile(Path file) {
        CalendarSettings settings = new CalendarSettings();
        settings.setFile(file.toString());
        calendarSettings.put("sg-business", settings);
    }

    /** Nine in the morning in Singapore, every day, on the calendar. */
    private BatchProperties.Quartz.TriggerSettings morningTrigger() {
        BatchProperties.Quartz.TriggerSettings trigger = cron(DATED_JOB, "0 0 9 ? * *");
        trigger.setTimeZone(SINGAPORE);
        trigger.setCalendar("sg-business");
        return trigger;
    }

    private static List<Instant> fireTimes(Trigger trigger, org.quartz.Calendar calendar, int count) {
        List<Instant> times = new ArrayList<>();
        for (Date date : TriggerUtils.computeFireTimes((OperableTrigger) trigger, calendar, count)) {
            times.add(date.toInstant());
        }
        return times;
    }

    @Test
    @DisplayName("AQ.5: the trigger never fires on the holiday, fires on the worked Saturday, and next fire agrees")
    void aHolidayNeverFiresAndAWorkedSaturdayDoes() throws Exception {
        useCalendarFile(calendarFile(SG));
        morningTrigger();
        reconciled();
        engine.start();

        Trigger stored = stored(DATED_JOB);
        assertThat(stored.getCalendarName()).isEqualTo("sg-business");
        assertThat(scheduler.getCalendar("sg-business")).isInstanceOf(AdharBusinessCalendar.class);
        assertThat(fireTimes(stored, scheduler.getCalendar("sg-business"), 4)).containsExactly(
                Instant.parse("2026-11-07T01:00:00Z"),   // the worked Saturday
                Instant.parse("2026-11-10T01:00:00Z"),   // Sunday and the Monday holiday never fire
                Instant.parse("2026-11-11T01:00:00Z"),
                Instant.parse("2026-11-12T01:00:00Z"));
        assertThat(stored.getNextFireTime().toInstant()).isEqualTo(Instant.parse("2026-11-07T01:00:00Z"));
        assertThat(engine.nextFireTime(DATED_JOB)).contains(Instant.parse("2026-11-07T01:00:00Z"));
    }

    @Test
    @DisplayName("AQ.5: a trigger whose zone differs from its calendar's fails the start, naming both")
    void aTriggerInAnotherZoneFailsTheStart() throws Exception {
        useCalendarFile(calendarFile(SG));
        cron(DATED_JOB, "0 0 9 ? * *").setCalendar("sg-business");

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runs in Z").hasMessageContaining("Asia/Singapore")
                .hasMessageContaining("must share one time zone");
        engine = null;
    }

    @Test
    @DisplayName("a calendar without a zone takes adhar.batch.scheduler.time-zone, so a trigger in that zone may use it")
    void aZonelessCalendarTakesTheSchedulersZone() throws Exception {
        CalendarSettings inline = new CalendarSettings();
        inline.setHolidays(List.of("2026-11-09"));
        calendarSettings.put("weekdays", inline);
        cron(DATED_JOB, "0 0 9 ? * *").setCalendar("weekdays");
        reconciled();

        assertThat(fireTimes(stored(DATED_JOB), scheduler.getCalendar("weekdays"), 2))
                .containsExactly(Instant.parse("2026-11-10T09:00:00Z"), Instant.parse("2026-11-11T09:00:00Z"));
        assertThat(((AdharBusinessCalendar) scheduler.getCalendar("weekdays")).zone().normalized())
                .isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    @DisplayName("a trigger naming a calendar that is not configured fails the start, listing those that are")
    void anUnknownCalendarFailsTheStart() throws Exception {
        useCalendarFile(calendarFile(SG));
        morningTrigger().setCalendar("sg-busines");

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'sg-busines', which is not configured").hasMessageContaining("[sg-business]");
        engine = null;
    }

    @Test
    @DisplayName("FQ7: an invalid calendar file fails the start, naming the property and the problem")
    void anInvalidFileFailsTheStart() throws Exception {
        Path file = calendarFile("name: sg-business\nholidays: [next-monday]\n");
        useCalendarFile(file);
        morningTrigger();

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.calendars.sg-business")
                .hasMessageContaining(file.toString())
                .hasMessageContaining("'next-monday' is not an ISO date");
        engine = null;
    }

    @Test
    @DisplayName("FQ7: on refresh an invalid file keeps the previous calendar and is counted; a changed one moves the next fire")
    void aRefreshKeepsTheOldCalendarOrAppliesTheNewOne() throws Exception {
        Path file = calendarFile(SG_2030);
        useCalendarFile(file);
        morningTrigger();
        clock.set(FRIDAY_2030);
        reconciled();
        RecordingMetrics metrics = new RecordingMetrics();
        engine.observe(metrics, null, null);

        assertThat(stored(DATED_JOB).getNextFireTime().toInstant()).isEqualTo(Instant.parse("2030-11-09T01:00:00Z"));

        Files.writeString(file, "name: sg-business\nholidays: [2030-11-11\n");
        engine.refreshCalendars();

        assertThat(metrics.calendarFailures).containsExactly("sg-business");
        assertThat(((AdharBusinessCalendar) scheduler.getCalendar("sg-business")).businessCalendar().businessDays())
                .as("the previous version stays registered").hasSize(1);
        assertThat(stored(DATED_JOB).getNextFireTime().toInstant()).isEqualTo(Instant.parse("2030-11-09T01:00:00Z"));

        Files.writeString(file, SG_2030.replace("businessDays: [2030-11-09]\n", ""));
        engine.refreshCalendars();

        assertThat(metrics.calendarFailures).hasSize(1);
        assertThat(stored(DATED_JOB).getNextFireTime().toInstant()).as("the Saturday is no longer worked")
                .isEqualTo(Instant.parse("2030-11-12T01:00:00Z"));

        engine.refreshCalendars();
        assertThat(metrics.calendarFailures).as("an unchanged file is not re-registered, nor counted").hasSize(1);
    }

    @Test
    @DisplayName("FQ7 + the zone rule: a refreshed file that moves the calendar to another zone keeps the previous one, counted")
    void aRefreshNeverMovesACalendarToAnotherZone() throws Exception {
        Path file = calendarFile(SG_2030);
        useCalendarFile(file);
        morningTrigger();
        clock.set(FRIDAY_2030);
        reconciled();
        RecordingMetrics metrics = new RecordingMetrics();
        engine.observe(metrics, null, null);

        Files.writeString(file, SG_2030.replace("Asia/Singapore", "America/New_York")
                .replace("businessDays: [2030-11-09]\n", ""));
        engine.refreshCalendars();

        assertThat(metrics.calendarFailures).containsExactly("sg-business");
        AdharBusinessCalendar registered = (AdharBusinessCalendar) scheduler.getCalendar("sg-business");
        assertThat(registered.zone()).as("the trigger runs in Singapore: so does its calendar").isEqualTo(SINGAPORE);
        assertThat(registered.businessCalendar().businessDays()).as("the previous version stays registered").hasSize(1);
        assertThat(stored(DATED_JOB).getNextFireTime().toInstant()).isEqualTo(Instant.parse("2030-11-09T01:00:00Z"));
    }

    @Test
    @DisplayName("the engine re-reads calendar files every refresh-interval while it runs, and stops when it stops")
    void refreshesWhileRunning() throws Exception {
        Path file = calendarFile(SG_2030);
        useCalendarFile(file);
        morningTrigger();
        properties.getQuartz().getCalendars().setRefreshInterval(Duration.ofMillis(50));
        clock.set(FRIDAY_2030);
        reconciled();
        engine.start();

        Files.writeString(file, SG_2030.replace("businessDays: [2030-11-09]\n", ""));

        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stored(DATED_JOB).getNextFireTime().toInstant()).isEqualTo(Instant.parse("2030-11-12T01:00:00Z")));
        engine.stop();
    }

    @Test
    @DisplayName("a calendar's file and time-zone together are refused: the zone belongs in the file")
    void fileAndZoneTogetherAreRefused() throws Exception {
        CalendarSettings settings = new CalendarSettings();
        settings.setFile(calendarFile(SG).toString());
        settings.setTimeZone("Asia/Singapore");
        calendarSettings.put("sg-business", settings);

        assertThatThrownBy(this::reconciled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sets both file and time-zone");
        engine = null;
    }

    @Test
    @DisplayName("a configured calendar's fire is launched with its slot, as any other")
    void aCalendarTriggersFireRuns() throws Exception {
        useCalendarFile(calendarFile(SG));
        morningTrigger();
        reconciled();
        Instant saturday = Instant.parse("2026-11-07T01:00:00Z");
        clock.set(saturday);

        assertThat(new BatchLaunchJob(engine).launch(fire(stored(DATED_JOB), saturday,
                scheduler.getCalendar("sg-business")))).isEqualTo(SlotOutcome.RAN);
        assertThat(slots(DATED_JOB)).containsExactly("2026-11-07/sg");
    }

    @Test
    @DisplayName("the named calendars are bound by name beside refresh-interval, which BatchProperties binds without them")
    void bindsCalendarsByName() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "adhar.batch.quartz.calendars.refresh-interval", "1m",
                "adhar.batch.quartz.calendars.sg-business.time-zone", "Asia/Singapore",
                "adhar.batch.quartz.calendars.sg-business.holidays[0]", "2026-11-09",
                "adhar.batch.quartz.calendars.sg-business.weekend-days", "FRIDAY,SATURDAY",
                "adhar.batch.quartz.calendars.weekdays.file", "/etc/adhar/batch/calendar/calendar.yaml")));

        Map<String, CalendarSettings> calendars = CalendarRegistry.bind(environment);
        BatchProperties properties = Binder.get(environment).bind("adhar.batch", BatchProperties.class).get();

        assertThat(calendars).containsOnlyKeys("sg-business", "weekdays");
        assertThat(calendars.get("sg-business").getTimeZone()).isEqualTo("Asia/Singapore");
        assertThat(calendars.get("sg-business").getHolidays()).containsExactly("2026-11-09");
        assertThat(calendars.get("sg-business").getWeekendDays()).containsExactly("FRIDAY", "SATURDAY");
        assertThat(calendars.get("weekdays").getFile()).isEqualTo("/etc/adhar/batch/calendar/calendar.yaml");
        assertThat(properties.getQuartz().getCalendars().getRefreshInterval()).isEqualTo(Duration.ofMinutes(1));

        CalendarRegistry registry = new CalendarRegistry(calendars, java.time.ZoneOffset.UTC);
        assertThat(registry.names()).containsExactly("sg-business", "weekdays");
        assertThat(registry.zone("sg-business")).isEqualTo(SINGAPORE);
        assertThat(registry.calendar("sg-business").isBusinessDay(java.time.LocalDate.of(2026, 11, 6)))
                .as("a Friday weekend").isFalse();
        assertThat(CalendarRegistry.none().names()).isEmpty();
    }

    /** Records what the engine counts. */
    static final class RecordingMetrics implements SchedulerMetrics {

        final List<String> calendarFailures = new ArrayList<>();
        final List<String> fires = new ArrayList<>();
        final Map<String, Instant> nextFires = new java.util.concurrent.ConcurrentHashMap<>();
        volatile int instances = -1;
        volatile int reportFailures;

        @Override
        public void fired(String job, String trigger, SlotOutcome outcome) {
            fires.add(job + "/" + trigger + "/" + SchedulerMetrics.outcomeTag(outcome));
        }

        @Override
        public void nextFire(String job, String trigger, Instant next) {
            if (next == null) {
                nextFires.remove(job + "/" + trigger);
            } else {
                nextFires.put(job + "/" + trigger, next);
            }
        }

        @Override
        public void instances(int count) {
            instances = count;
        }

        @Override
        public void reportFailed() {
            reportFailures++;
        }

        @Override
        public void calendarFailed(String calendar) {
            calendarFailures.add(calendar);
        }
    }
}
