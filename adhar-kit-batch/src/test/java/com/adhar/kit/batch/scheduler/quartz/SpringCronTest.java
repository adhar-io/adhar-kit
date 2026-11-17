package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.scheduler.quartz.QuartzSchedulerEngine.SpringCron;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.CronExpression;

import java.time.Instant;
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
}
