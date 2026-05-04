package com.adhar.kit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdharDateTimeUtils Tests")
class AdharDateTimeUtilsTest {

    @Nested
    @DisplayName("Current Time")
    class CurrentTime {

        @Test
        void now_returnsNonNull() {
            assertThat(AdharDateTimeUtils.now()).isNotNull();
        }

        @Test
        void nowInstant_returnsNonNull() {
            assertThat(AdharDateTimeUtils.nowInstant()).isNotNull();
        }

        @Test
        void currentTimeMillis_returnsPositive() {
            assertThat(AdharDateTimeUtils.currentTimeMillis()).isPositive();
        }

        @Test
        void today_returnsToday() {
            assertThat(AdharDateTimeUtils.today()).isEqualTo(LocalDate.now());
        }

        @Test
        void tomorrow_returnsTomorrow() {
            assertThat(AdharDateTimeUtils.tomorrow()).isEqualTo(LocalDate.now().plusDays(1));
        }

        @Test
        void yesterday_returnsYesterday() {
            assertThat(AdharDateTimeUtils.yesterday()).isEqualTo(LocalDate.now().minusDays(1));
        }
    }

    @Nested
    @DisplayName("Formatting")
    class Formatting {

        @Test
        void format_localDateTime() {
            LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 0);
            assertThat(AdharDateTimeUtils.format(dt, "yyyy-MM-dd HH:mm:ss"))
                .isEqualTo("2025-03-15 10:30:00");
            assertThat(AdharDateTimeUtils.format((LocalDateTime) null, "yyyy-MM-dd")).isNull();
        }

        @Test
        void format_localDate() {
            LocalDate d = LocalDate.of(2025, 3, 15);
            assertThat(AdharDateTimeUtils.format(d, "dd/MM/yyyy")).isEqualTo("15/03/2025");
            assertThat(AdharDateTimeUtils.format((LocalDate) null, "yyyy-MM-dd")).isNull();
        }

        @Test
        void toIsoString_localDateTime() {
            LocalDateTime dt = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
            assertThat(AdharDateTimeUtils.toIsoString(dt)).contains("2025-01-01");
            assertThat(AdharDateTimeUtils.toIsoString((LocalDateTime) null)).isNull();
        }

        @Test
        void toIsoString_localDate() {
            LocalDate d = LocalDate.of(2025, 6, 15);
            assertThat(AdharDateTimeUtils.toIsoString(d)).isEqualTo("2025-06-15");
            assertThat(AdharDateTimeUtils.toIsoString((LocalDate) null)).isNull();
        }
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        void parse_validString() {
            LocalDateTime dt = AdharDateTimeUtils.parse("2025-03-15 10:30:00", "yyyy-MM-dd HH:mm:ss");
            assertThat(dt).isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 30, 0));
        }

        @Test
        void parse_nullReturnsNull() {
            assertThat(AdharDateTimeUtils.parse(null, "yyyy-MM-dd")).isNull();
            assertThat(AdharDateTimeUtils.parse("", "yyyy-MM-dd")).isNull();
        }

        @Test
        void parseDate_validString() {
            LocalDate d = AdharDateTimeUtils.parseDate("15/03/2025", "dd/MM/yyyy");
            assertThat(d).isEqualTo(LocalDate.of(2025, 3, 15));
            assertThat(AdharDateTimeUtils.parseDate(null, "dd/MM/yyyy")).isNull();
        }

        @Test
        void parseIso_validString() {
            LocalDateTime dt = AdharDateTimeUtils.parseIso("2025-03-15T10:30:00");
            assertThat(dt).isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 30, 0));
            assertThat(AdharDateTimeUtils.parseIso(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Conversion")
    class Conversion {

        @Test
        void toDate_and_fromDate() {
            LocalDateTime dt = LocalDateTime.of(2025, 6, 1, 12, 0, 0);
            Date date = AdharDateTimeUtils.toDate(dt);
            assertThat(date).isNotNull();

            LocalDateTime back = AdharDateTimeUtils.fromDate(date);
            assertThat(back).isEqualTo(dt);

            assertThat(AdharDateTimeUtils.toDate(null)).isNull();
            assertThat(AdharDateTimeUtils.fromDate(null)).isNull();
        }

        @Test
        void epochMillis_roundTrip() {
            LocalDateTime dt = LocalDateTime.of(2025, 6, 1, 12, 0, 0);
            long millis = AdharDateTimeUtils.toEpochMillis(dt);
            assertThat(millis).isPositive();

            LocalDateTime back = AdharDateTimeUtils.fromEpochMillis(millis);
            assertThat(back).isEqualTo(dt);

            assertThat(AdharDateTimeUtils.toEpochMillis(null)).isZero();
        }
    }

    @Nested
    @DisplayName("Date Arithmetic")
    class Arithmetic {

        private final LocalDateTime base = LocalDateTime.of(2025, 6, 1, 12, 0, 0);

        @Test
        void addDays() {
            assertThat(AdharDateTimeUtils.addDays(base, 5)).isEqualTo(base.plusDays(5));
            assertThat(AdharDateTimeUtils.addDays(null, 1)).isNull();
        }

        @Test
        void addHours() {
            assertThat(AdharDateTimeUtils.addHours(base, 3)).isEqualTo(base.plusHours(3));
            assertThat(AdharDateTimeUtils.addHours(null, 1)).isNull();
        }

        @Test
        void addMinutes() {
            assertThat(AdharDateTimeUtils.addMinutes(base, 30)).isEqualTo(base.plusMinutes(30));
            assertThat(AdharDateTimeUtils.addMinutes(null, 1)).isNull();
        }

        @Test
        void subtractDays() {
            assertThat(AdharDateTimeUtils.subtractDays(base, 2)).isEqualTo(base.minusDays(2));
            assertThat(AdharDateTimeUtils.subtractDays(null, 1)).isNull();
        }
    }

    @Nested
    @DisplayName("Date Comparison")
    class Comparison {

        private final LocalDateTime earlier = LocalDateTime.of(2025, 1, 1, 0, 0);
        private final LocalDateTime later = LocalDateTime.of(2025, 12, 31, 23, 59);

        @Test
        void isBefore_isAfter() {
            assertThat(AdharDateTimeUtils.isBefore(earlier, later)).isTrue();
            assertThat(AdharDateTimeUtils.isBefore(later, earlier)).isFalse();
            assertThat(AdharDateTimeUtils.isBefore(null, later)).isFalse();

            assertThat(AdharDateTimeUtils.isAfter(later, earlier)).isTrue();
            assertThat(AdharDateTimeUtils.isAfter(null, earlier)).isFalse();
        }

        @Test
        void isBetween() {
            LocalDateTime mid = LocalDateTime.of(2025, 6, 15, 12, 0);
            assertThat(AdharDateTimeUtils.isBetween(mid, earlier, later)).isTrue();
            assertThat(AdharDateTimeUtils.isBetween(earlier, mid, later)).isFalse();
            assertThat(AdharDateTimeUtils.isBetween(null, earlier, later)).isFalse();
        }
    }

    @Nested
    @DisplayName("Date Calculations")
    class Calculations {

        @Test
        void daysBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 11, 0, 0);
            assertThat(AdharDateTimeUtils.daysBetween(start, end)).isEqualTo(10);
            assertThat(AdharDateTimeUtils.daysBetween(null, end)).isZero();
        }

        @Test
        void hoursBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 15, 0);
            assertThat(AdharDateTimeUtils.hoursBetween(start, end)).isEqualTo(5);
            assertThat(AdharDateTimeUtils.hoursBetween(null, end)).isZero();
        }

        @Test
        void minutesBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 45);
            assertThat(AdharDateTimeUtils.minutesBetween(start, end)).isEqualTo(45);
            assertThat(AdharDateTimeUtils.minutesBetween(null, end)).isZero();
        }
    }

    @Nested
    @DisplayName("Business Days")
    class BusinessDays {

        @Test
        void isBusinessDay() {
            // Monday
            assertThat(AdharDateTimeUtils.isBusinessDay(LocalDate.of(2025, 6, 2))).isTrue();
            // Saturday
            assertThat(AdharDateTimeUtils.isBusinessDay(LocalDate.of(2025, 6, 7))).isFalse();
            // Sunday
            assertThat(AdharDateTimeUtils.isBusinessDay(LocalDate.of(2025, 6, 8))).isFalse();
            assertThat(AdharDateTimeUtils.isBusinessDay(null)).isFalse();
        }

        @Test
        void nextBusinessDay() {
            // Friday -> Monday
            assertThat(AdharDateTimeUtils.nextBusinessDay(LocalDate.of(2025, 6, 6)))
                .isEqualTo(LocalDate.of(2025, 6, 9));
            assertThat(AdharDateTimeUtils.nextBusinessDay(null)).isNull();
        }

        @Test
        void previousBusinessDay() {
            // Monday -> Friday
            assertThat(AdharDateTimeUtils.previousBusinessDay(LocalDate.of(2025, 6, 9)))
                .isEqualTo(LocalDate.of(2025, 6, 6));
            assertThat(AdharDateTimeUtils.previousBusinessDay(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Start/End of Period")
    class StartEndPeriod {

        @Test
        void startOfDay_endOfDay() {
            LocalDate d = LocalDate.of(2025, 6, 15);
            assertThat(AdharDateTimeUtils.startOfDay(d)).isEqualTo(d.atStartOfDay());
            assertThat(AdharDateTimeUtils.endOfDay(d).getHour()).isEqualTo(23);
            assertThat(AdharDateTimeUtils.startOfDay(null)).isNull();
            assertThat(AdharDateTimeUtils.endOfDay(null)).isNull();
        }

        @Test
        void startOfMonth_endOfMonth() {
            LocalDate d = LocalDate.of(2025, 6, 15);
            assertThat(AdharDateTimeUtils.startOfMonth(d)).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(AdharDateTimeUtils.endOfMonth(d)).isEqualTo(LocalDate.of(2025, 6, 30));
            assertThat(AdharDateTimeUtils.startOfMonth(null)).isNull();
            assertThat(AdharDateTimeUtils.endOfMonth(null)).isNull();
        }

        @Test
        void startOfYear_endOfYear() {
            LocalDate d = LocalDate.of(2025, 6, 15);
            assertThat(AdharDateTimeUtils.startOfYear(d)).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(AdharDateTimeUtils.endOfYear(d)).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(AdharDateTimeUtils.startOfYear(null)).isNull();
            assertThat(AdharDateTimeUtils.endOfYear(null)).isNull();
        }
    }
}
