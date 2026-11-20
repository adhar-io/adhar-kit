package com.adhar.kit.batch.calendar;

import com.adhar.kit.batch.calendar.BusinessCalendarParser.InvalidCalendarException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.4: design 0025 D5's calendar shape, the one parser of the run pod's calendar file and the
 * in-service engine's calendars.
 */
class BusinessCalendarParserTest {

    /** A team calendar: Christmas off, the Saturday after it worked, in Singapore. */
    private static final String SG = """
            name: sg-business
            timeZone: Asia/Singapore
            weekendDays: [SATURDAY, SUNDAY]
            holidays: [2026-12-25, "2027-01-01"]
            businessDays: [2026-12-26]
            """;

    @ParameterizedTest(name = "{0} ({1}) is a business day: {2}")
    @CsvSource({
            "2026-12-24, Thursday, true",
            "2026-12-25, a Friday holiday, false",
            "2026-12-26, a worked Saturday, true",
            "2026-12-27, Sunday, false",
            "2026-12-28, Monday, true",
            "2027-01-01, a Friday holiday quoted in the file, false",
            "2027-01-02, an ordinary Saturday, false"})
    @DisplayName("holiday, weekend and business day: a business day is listed in businessDays, or neither a weekend day nor a holiday")
    void businessDays(LocalDate date, String what, boolean business) {
        assertThat(BusinessCalendarParser.parse(SG, "sg.yaml").isBusinessDay(date)).as(what).isEqualTo(business);
    }

    @Test
    @DisplayName("the file's keys become the calendar; unquoted YAML dates are dates, not timestamps")
    void parsesTheFile() {
        BusinessCalendar calendar = BusinessCalendarParser.parse(SG, "sg.yaml");

        assertThat(calendar.name()).isEqualTo("sg-business");
        assertThat(calendar.timeZone()).contains(ZoneId.of("Asia/Singapore"));
        assertThat(calendar.weekendDays()).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(calendar.holidays()).containsExactly(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1));
        assertThat(calendar.businessDays()).containsExactly(LocalDate.of(2026, 12, 26));
    }

    @Test
    @DisplayName("a business day wins over a holiday of the same date, as D5's rule reads")
    void aBusinessDayWinsOverAHoliday() {
        BusinessCalendar calendar = BusinessCalendarParser.parse("""
                name: odd
                holidays: [2026-12-26]
                businessDays: [2026-12-26]
                """, "odd.yaml");

        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 26))).isTrue();
    }

    @Test
    @DisplayName("the platform's weekdays calendar: absent zone, D5's default weekend, empty lists")
    void thePlatformsWeekdays() {
        BusinessCalendar calendar = BusinessCalendarParser.parse("""
                name: weekdays
                weekendDays: [SATURDAY, SUNDAY]
                holidays: []
                businessDays: []
                """, "weekdays");

        assertThat(calendar.timeZone()).isEmpty();
        assertThat(calendar.zoneOr(ZoneOffset.UTC)).isEqualTo(ZoneOffset.UTC);
        assertThat(BusinessCalendarParser.parse("name: minimal", "m").weekendDays())
                .as("weekendDays absent is D5's default").isEqualTo(BusinessCalendar.DEFAULT_WEEKEND);
        assertThat(BusinessCalendarParser.parse("name: none\nweekendDays: []", "n").weekendDays())
                .as("an explicit empty list is no weekend").isEmpty();
        assertThat(BusinessCalendarParser.parse("name: low\nweekendDays: [friday]", "l").weekendDays())
                .containsExactly(DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("a date is taken in the calendar's zone, else the caller's")
    void datesAreTakenInTheZone() {
        // 2026-12-25T17:00Z is already the 26th (the worked Saturday) in Singapore, still the
        // 25th (a holiday) in UTC.
        Instant lateChristmasUtc = Instant.parse("2026-12-25T17:00:00Z");
        BusinessCalendar singapore = BusinessCalendarParser.parse(SG, "sg.yaml");
        BusinessCalendar zoneless = BusinessCalendarParser.parse(SG.replace("timeZone: Asia/Singapore\n", ""), "sg.yaml");

        assertThat(singapore.includes(lateChristmasUtc, ZoneOffset.UTC)).isTrue();
        assertThat(zoneless.includes(lateChristmasUtc, ZoneOffset.UTC)).isFalse();
        assertThat(zoneless.includes(lateChristmasUtc, ZoneId.of("Asia/Singapore"))).isTrue();
    }

    @Test
    @DisplayName("every problem of an invalid file is named at once, with the source")
    void invalidFilesNameEveryProblem() {
        assertThatThrownBy(() -> BusinessCalendarParser.parse("""
                name: broken
                timeZone: Mars/Olympus
                weekendDays: [SATURDAY, Caturday]
                holiday: [2026-12-25]
                holidays: [2026-13-01]
                businessDays: 2026-12-26
                """, "broken.yaml"))
                .isInstanceOf(InvalidCalendarException.class)
                .hasMessageStartingWith("Business calendar broken.yaml is invalid")
                .hasMessageContaining("unknown key 'holiday'")
                .hasMessageContaining("timeZone 'Mars/Olympus'")
                .hasMessageContaining("'Caturday' is not a day of the week")
                .hasMessageContaining("holidays: '2026-13-01' is not an ISO date")
                .hasMessageContaining("businessDays must be a list")
                .satisfies(ex -> {
                    InvalidCalendarException invalid = (InvalidCalendarException) ex;
                    assertThat(invalid.getSource()).isEqualTo("broken.yaml");
                    assertThat(invalid.getProblems()).hasSize(5);
                });
    }

    @Test
    @DisplayName("not YAML, not a mapping, no name, or no day ever included: refused")
    void unusableCalendars() {
        assertThatThrownBy(() -> BusinessCalendarParser.parse("name: [unclosed", "a.yaml"))
                .isInstanceOf(InvalidCalendarException.class).hasMessageContaining("is not YAML");
        assertThatThrownBy(() -> BusinessCalendarParser.parse("- a list", "b.yaml"))
                .isInstanceOf(InvalidCalendarException.class).hasMessageContaining("is not a mapping");
        assertThatThrownBy(() -> BusinessCalendarParser.parse("holidays: []", "c.yaml"))
                .isInstanceOf(InvalidCalendarException.class).hasMessageContaining("name is required");
        assertThatThrownBy(() -> BusinessCalendarParser.parse("name: never\nweekendDays: [MONDAY, TUESDAY, "
                + "WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY]", "d.yaml"))
                .isInstanceOf(InvalidCalendarException.class).hasMessageContaining("no time is ever included");
        assertThat(BusinessCalendarParser.parse("name: rare\nweekendDays: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, "
                + "FRIDAY, SATURDAY, SUNDAY]\nbusinessDays: [2026-12-31]", "e.yaml").hasBusinessDays())
                .as("a whole-week weekend with one business day still includes that day").isTrue();
    }

    @Test
    @DisplayName("a file is read from disk; one that cannot be read is invalid, naming the path")
    void readsFiles(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("calendar.yaml"), SG);

        assertThat(BusinessCalendarParser.parse(file).name()).isEqualTo("sg-business");
        assertThatThrownBy(() -> BusinessCalendarParser.parse(dir.resolve("missing.yaml")))
                .isInstanceOf(InvalidCalendarException.class).hasMessageContaining("missing.yaml")
                .hasMessageContaining("cannot be read");
    }

    @Test
    @DisplayName("properties with the file's keys parse the same way; a null weekend is the default")
    void propertiesWithTheFilesKeys() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("name", "sg-business");
        keys.put("timeZone", "Asia/Singapore");
        keys.put("weekendDays", null);
        keys.put("holidays", List.of("2026-12-25"));
        keys.put("businessDays", List.of("2026-12-26"));

        assertThat(BusinessCalendarParser.parse(keys, "adhar.batch.quartz.calendars.sg-business"))
                .isEqualTo(new BusinessCalendar("sg-business", Optional.of(ZoneId.of("Asia/Singapore")),
                        BusinessCalendar.DEFAULT_WEEKEND, new java.util.TreeSet<>(List.of(LocalDate.of(2026, 12, 25))),
                        new java.util.TreeSet<>(List.of(LocalDate.of(2026, 12, 26)))));
    }
}
