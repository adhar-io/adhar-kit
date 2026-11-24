package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.scheduler.quartz.QuartzSchedulerEngine.SpringCron;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.CronExpression;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringCron}: a Spring cron expression of {@code adhar.batch.scheduler.jobs}
 * becomes a Quartz one that fires at the same instants. The two dialects number the days of the
 * week differently (Spring 0 or 7 = Sunday, Quartz 1 = Sunday) and Quartz wants {@code ?} in one of
 * the day fields, so a conversion that is off by one day still parses: every case here is held to
 * the fire times, not to the text alone.
 */
class SpringCronTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

    private static final int FIRES = 60;

    private static List<Instant> springFires(String expression) {
        var cron = org.springframework.scheduling.support.CronExpression.parse(expression);
        List<Instant> fires = new ArrayList<>();
        ZonedDateTime cursor = FROM.atZone(ZoneOffset.UTC);
        for (int i = 0; i < FIRES; i++) {
            cursor = cron.next(cursor);
            fires.add(cursor.toInstant());
        }
        return fires;
    }

    private static List<Instant> quartzFires(String expression) throws Exception {
        CronExpression cron = new CronExpression(expression);
        cron.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        List<Instant> fires = new ArrayList<>();
        Date cursor = Date.from(FROM);
        for (int i = 0; i < FIRES; i++) {
            cursor = cron.getNextValidTimeAfter(cursor);
            fires.add(cursor.toInstant());
        }
        return fires;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0 0 0 * * *          | 0 0 0 * * ?",
            "0 */10 * * * MON-FRI | 0 */10 * ? * MON-FRI",
            "0 15 3 * * 1-5       | 0 15 3 ? * 2-6",
            "0 0 12 * * 0         | 0 0 12 ? * 1",
            "0 0 12 * * 7         | 0 0 12 ? * 1",
            "0 0 12 * * 6,0       | 0 0 12 ? * 7,1",
            "0 0 12 * * 5-7       | 0 0 12 ? * 6-1",
            "0 0 12 * * 1#2       | 0 0 12 ? * 2#2",
            "0 0 12 * * MON#1     | 0 0 12 ? * MON#1",
            "0 0 12 * * 5L        | 0 0 12 ? * 6L",
            "0 0 12 * * 7L        | 0 0 12 ? * 1L",
            "0 0 12 * * FRIL      | 0 0 12 ? * FRIL",
            "0 0 12 * * 1-7       | 0 0 12 ? * 2-1",
            "0 0 12 * * 0-6       | 0 0 12 ? * 1-7",
            "0 0 12 * * 1-5/2     | 0 0 12 ? * 2-6/2",
            "0 0 12 * * mon-fri   | 0 0 12 ? * mon-fri",
            "0 0 12 * * 1/2       | 0 0 12 ? * MON,WED,FRI,SUN",
            "0 0 12 * * */2       | 0 0 12 ? * MON,WED,FRI,SUN",
            "0 0 12 * * 0-7       | 0 0 12 ? * MON,TUE,WED,THU,FRI,SAT,SUN",
            "0 0 12 * * MON-FRI/2 | 0 0 12 ? * MON,WED,FRI",
            "0 0 12 1 * *         | 0 0 12 1 * ?",
            "0 0 12 L * *         | 0 0 12 L * ?",
            "0 0 12 15W * *       | 0 0 12 15W * ?",
            "0 0 12 1 JAN,JUL *   | 0 0 12 1 JAN,JUL ?",
            "0 0 12 ? * MON       | 0 0 12 ? * MON",
            "0 0 12 * * ?         | 0 0 12 * * ?",
            "0 0/5 9-17 * * 1-5   | 0 0/5 9-17 ? * 2-6",
            "@hourly              | 0 0 * * * ?",
            "@daily               | 0 0 0 * * ?",
            "@midnight            | 0 0 0 * * ?",
            "@weekly              | 0 0 0 ? * 1",
            "@monthly             | 0 0 0 1 * ?",
            "@yearly              | 0 0 0 1 1 ?",
            "@Annually            | 0 0 0 1 1 ?"})
    @DisplayName("a Spring expression converts to the Quartz expression that fires at the same instants")
    void convertsToTheSameFireTimes(String spring, String quartz) throws Exception {
        String converted = SpringCron.toQuartz(spring);

        assertThat(converted).isEqualTo(quartz);
        assertThat(quartzFires(converted)).as("the next %d fires of '%s' as '%s'", FIRES, spring, converted)
                .isEqualTo(springFires(spring.strip().startsWith("@") ? macro(spring) : spring));
    }

    private static String macro(String name) {
        return switch (name.strip().toLowerCase()) {
            case "@hourly" -> "0 0 * * * *";
            case "@daily", "@midnight" -> "0 0 0 * * *";
            case "@weekly" -> "0 0 0 * * 0";
            case "@monthly" -> "0 0 0 1 * *";
            default -> "0 0 0 1 1 *";
        };
    }

    @Test
    @DisplayName("an expression restricting both day fields is refused: Quartz cannot express it")
    void bothDayFieldsAreRefused() {
        assertThatThrownBy(() -> SpringCron.toQuartz("0 0 12 13 * FRI")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restricts both the day of the month and the day of the week")
                .hasMessageContaining("adhar.batch.quartz.triggers");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "every day", "0 0 12 * *", "0 0 25 * * *", "0 0 12 * * 3-1", "@fortnightly"})
    @DisplayName("what Spring does not parse is refused with the expression")
    void invalidExpressionsAreRefused(String expression) {
        assertThatThrownBy(() -> SpringCron.toQuartz(expression)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("(?s)A cron expression is required|Invalid cron expression '.*");
    }

    @Test
    @DisplayName("no expression at all is refused")
    void nullIsRefused() {
        assertThatThrownBy(() -> SpringCron.toQuartz(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A cron expression is required");
    }

    // ------------------------------------------------------------------
    // The 400-slot agreement check (KIT-Q.5, design 0025 §5.20.6, R12)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a conversion that would fire elsewhere fails, naming the first slot, the zone and both instants")
    void aDivergentConversionFailsNamingTheSlot() {
        // Monday in Spring, Sunday in Quartz: both parse, and 2026-01-01 is a Thursday.
        assertThatThrownBy(() -> SpringCron.verify("0 0 12 * * 1", "0 0 12 * * 1", "0 0 12 ? * 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disagree at slot 1 after 2026-01-01T00:00:00Z in Z")
                .hasMessageContaining("Spring fires at 2026-01-05T12:00Z, Quartz at 2026-01-04T12:00Z")
                .hasMessageContaining("adhar.batch.quartz.triggers cron trigger in Quartz syntax");
    }

    // ------------------------------------------------------------------
    // Clock changes warn, they do not fail (R12, decided 2026-11-22)
    // ------------------------------------------------------------------

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Test
    @DisplayName("01:30 daily converts: it differs only in the hour New York and London repeat, one difference each")
    void theRepeatedHourOfAClockChangeIsReportedNotRefused() {
        SpringCron.Conversion conversion = SpringCron.convert("0 30 1 * * *", ZoneOffset.UTC);

        assertThat(conversion.quartz()).isEqualTo("0 30 1 * * ?");
        assertThat(conversion.clockChanges()).extracting(SpringCron.ClockChange::zone).containsExactly(NEW_YORK, LONDON);
        SpringCron.ClockChange newYork = conversion.clockChanges().get(0);
        assertThat(newYork.date()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(newYork.spring()).as("Spring: the earlier offset").isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(newYork.quartz()).as("Quartz: the later offset").isEqualTo(Instant.parse("2026-11-01T06:30:00Z"));
        assertThat(conversion.clockChanges().get(1).date()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(SpringCron.toQuartz("0 30 1 * * *")).isEqualTo("0 30 1 * * ?");
    }

    @Test
    @DisplayName("02:30 daily: the hour New York and London skip is skipped by both parsers; Sydney's repeated hour differs")
    void theSkippedHourAgreesAndSydneysRepeatedHourIsReported() {
        SpringCron.Conversion conversion = SpringCron.convert("0 30 2 * * *", ZoneOffset.UTC);

        assertThat(conversion.quartz()).isEqualTo("0 30 2 * * ?");
        assertThat(conversion.clockChanges()).singleElement().satisfies(change -> {
            assertThat(change.zone()).isEqualTo(SYDNEY);
            assertThat(change.date()).isEqualTo(LocalDate.of(2026, 4, 5));
        });
        // Three fires a day: its 400 slots end in May, after Sydney's April change and before the northern ones.
        assertThat(SpringCron.convert("0 15 1-3 * * *", ZoneOffset.UTC).clockChanges())
                .extracting(SpringCron.ClockChange::zone).containsExactly(SYDNEY);
    }

    @Test
    @DisplayName("every 30 minutes through the repeated hour: one difference, then the two resynchronise and agree")
    void theIteratorsResynchroniseAfterADoubledFire() {
        Instant beforeTheChange = Instant.parse("2026-10-31T00:00:00Z");

        List<SpringCron.ClockChange> changes = SpringCron.verify("0 0/30 * * * *", "0 0/30 * * * *", "0 0/30 * * * ?",
                beforeTheChange, List.of(NEW_YORK));

        // Spring fires 01:00 EDT, Quartz 01:00 EST; 400 half-hours run eight days past the change, all agreeing.
        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.spring()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
            assertThat(change.quartz()).isEqualTo(Instant.parse("2026-11-01T06:00:00Z"));
        });
    }

    @Test
    @DisplayName("an expression that never fires between 01:00 and 02:59 has no clock-change difference anywhere")
    void anExpressionOutsideTheChangeHoursHasNone() {
        assertThat(SpringCron.convert("0 30 4 * * *", ZoneOffset.UTC).clockChanges()).isEmpty();
        assertThat(SpringCron.convert("0 0 12 * * MON-FRI", ZoneOffset.UTC).clockChanges()).isEmpty();
    }

    @Test
    @DisplayName("a wrong conversion still fails, even one that is off by exactly the hour a clock change moves")
    void aWrongConversionStillFails() {
        assertThatThrownBy(() -> SpringCron.verify("0 30 1 * * *", "0 30 1 * * *", "0 30 2 * * ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disagree at slot 1 after 2026-01-01T00:00:00Z in Z")
                .hasMessageContaining("Spring fires at 2026-01-01T01:30Z, Quartz at 2026-01-01T02:30Z");
        // Wrong on Saturdays only (Quartz 1-6 is Sunday to Friday), and clock changes fall on Sundays.
        assertThatThrownBy(() -> SpringCron.verify("0 30 1 * * *", "0 30 1 * * *", "0 30 1 ? * 1-6"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disagree at slot");
        // Wrong only in a daylight-saving zone, beside its change: a day apart is not a clock change.
        assertThatThrownBy(() -> SpringCron.verify("0 30 1 * * *", "0 30 1 * * *", "0 30 1 ? * 1-6",
                Instant.parse("2026-10-31T00:00:00Z"), List.of(NEW_YORK)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("in America/New_York");
    }

    @Test
    @DisplayName("a clock-change difference is two fire times within one transition's shift of it, and nothing wider")
    void whatCountsAsAClockChange() {
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-11-01T06:00:00Z"),
                NEW_YORK)).as("the repeated hour's two 01:00").isTrue();
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:30:00Z"), Instant.parse("2026-11-01T06:30:00Z"),
                NEW_YORK)).isTrue();
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:30:00Z"), Instant.parse("2026-11-01T07:30:00Z"),
                NEW_YORK)).as("two hours apart").isFalse();
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:30:00Z"), Instant.parse("2026-11-02T06:30:00Z"),
                NEW_YORK)).as("a day apart").isFalse();
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:30:00Z"), Instant.parse("2026-11-01T06:30:00Z"),
                ZoneOffset.UTC)).as("a zone without that change").isFalse();
        assertThat(SpringCron.atClockChange(Instant.parse("2026-11-01T05:30:00Z"), null, NEW_YORK))
                .as("one parser never fires").isFalse();
    }

    @Test
    @DisplayName("the service's own zone is checked too, when no agreement zone shares its rules")
    void theServicesZoneIsChecked() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        assertThat(SpringCron.zones(ZoneOffset.UTC)).isEqualTo(SpringCron.AGREEMENT_ZONES);
        assertThat(SpringCron.zones(ZoneId.of("UTC"))).isEqualTo(SpringCron.AGREEMENT_ZONES);
        assertThat(SpringCron.zones(ZoneId.of("US/Eastern"))).isEqualTo(SpringCron.AGREEMENT_ZONES);
        assertThat(SpringCron.zones(berlin)).endsWith(berlin).hasSize(5);
        // Berlin repeats 02:00-02:59 on 25 October, as Sydney does in April: both are named.
        assertThat(SpringCron.convert("0 30 2 * * *", berlin).clockChanges()).extracting(SpringCron.ClockChange::zone)
                .containsExactly(SYDNEY, berlin);
    }

    @Test
    @DisplayName("the check compares 400 slots in UTC and three daylight-saving zones, and stops at Quartz's last year")
    void theCheckCoversFourZonesAndQuartzsHorizon() {
        assertThat(SpringCron.AGREEMENT_SLOTS).isEqualTo(400);
        assertThat(SpringCron.AGREEMENT_ZONES).extracting(java.time.ZoneId::getId)
                .containsExactly("Z", "America/New_York", "Europe/London", "Australia/Sydney");
        // Five fires a second: 400 slots end long before a clock change, so only the conversion is judged.
        assertThat(SpringCron.toQuartz("*/5 * * * * *")).isEqualTo("*/5 * * * * ?");
        // A yearly expression's 400 slots run past the hundred years Quartz computes: not a disagreement.
        assertThat(SpringCron.toQuartz("0 0 6 15 6 *")).isEqualTo("0 0 6 15 6 ?");
    }
}
