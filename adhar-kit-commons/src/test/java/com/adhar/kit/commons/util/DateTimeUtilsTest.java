package com.adhar.kit.commons.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateTimeUtilsTest {

    @Test
    void now_shouldReturnCurrentDateTime() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime result = DateTimeUtils.now();
        LocalDateTime after = LocalDateTime.now();
        assertThat(result).isBetween(before, after);
    }

    @Test
    void nowUtc_shouldReturnUTCDateTime() {
        LocalDateTime result = DateTimeUtils.nowUtc();
        assertThat(result).isNotNull();
    }

    @Test
    void today_shouldReturnCurrentDate() {
        assertThat(DateTimeUtils.today()).isEqualTo(LocalDate.now());
    }

    @Test
    void currentTime_shouldReturnCurrentTime() {
        assertThat(DateTimeUtils.currentTime()).isNotNull();
    }

    @Test
    void currentTimeMillis_shouldReturnPositive() {
        assertThat(DateTimeUtils.currentTimeMillis()).isPositive();
    }

    @Test
    void currentTimeSeconds_shouldReturnPositive() {
        assertThat(DateTimeUtils.currentTimeSeconds()).isPositive();
    }

    @Test
    void formatDateTime_shouldFormatCorrectly() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        assertThat(DateTimeUtils.formatDateTime(dt)).isEqualTo("2025-03-15T10:30:45");
    }

    @Test
    void formatDateTime_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.formatDateTime(null)).isNull();
    }

    @Test
    void formatDate_shouldFormatCorrectly() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        assertThat(DateTimeUtils.formatDate(date)).isEqualTo("2025-03-15");
    }

    @Test
    void formatDate_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.formatDate(null)).isNull();
    }

    @Test
    void formatTime_shouldFormatCorrectly() {
        LocalTime time = LocalTime.of(10, 30, 45);
        assertThat(DateTimeUtils.formatTime(time)).isEqualTo("10:30:45");
    }

    @Test
    void formatTime_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.formatTime(null)).isNull();
    }

    @Test
    void format_shouldFormatWithCustomPattern() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        assertThat(DateTimeUtils.format(dt, "dd/MM/yyyy")).isEqualTo("15/03/2025");
    }

    @Test
    void format_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.format(null, "yyyy")).isNull();
    }

    @Test
    void parseDateTime_shouldParseCorrectly() {
        LocalDateTime result = DateTimeUtils.parseDateTime("2025-03-15T10:30:45");
        assertThat(result).isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 30, 45));
    }

    @Test
    void parseDateTime_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.parseDateTime(null)).isNull();
    }

    @Test
    void parseDate_shouldParseCorrectly() {
        assertThat(DateTimeUtils.parseDate("2025-03-15")).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    void parseDate_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.parseDate(null)).isNull();
    }

    @Test
    void parseTime_shouldParseCorrectly() {
        assertThat(DateTimeUtils.parseTime("10:30:45")).isEqualTo(LocalTime.of(10, 30, 45));
    }

    @Test
    void parseTime_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.parseTime(null)).isNull();
    }

    @Test
    void toDate_andFromDate_shouldRoundTrip() {
        LocalDateTime original = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        Date date = DateTimeUtils.toDate(original);
        assertThat(date).isNotNull();
        LocalDateTime converted = DateTimeUtils.fromDate(date);
        assertThat(converted).isEqualToIgnoringNanos(original);
    }

    @Test
    void toDate_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.toDate(null)).isNull();
    }

    @Test
    void fromDate_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.fromDate(null)).isNull();
    }

    @Test
    void toEpochMillis_shouldReturnZeroForNull() {
        assertThat(DateTimeUtils.toEpochMillis(null)).isZero();
    }

    @Test
    void fromEpochMillis_shouldConvertCorrectly() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        long millis = DateTimeUtils.toEpochMillis(dt);
        LocalDateTime result = DateTimeUtils.fromEpochMillis(millis);
        assertThat(result).isEqualToIgnoringNanos(dt);
    }

    @Test
    void addDays_shouldAddDays() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        assertThat(DateTimeUtils.addDays(dt, 5)).isEqualTo(LocalDateTime.of(2025, 3, 20, 10, 0, 0));
    }

    @Test
    void addDays_shouldReturnNullForNull() {
        assertThat(DateTimeUtils.addDays(null, 5)).isNull();
    }

    @Test
    void addHours_shouldAddHours() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        assertThat(DateTimeUtils.addHours(dt, 3)).isEqualTo(LocalDateTime.of(2025, 3, 15, 13, 0, 0));
    }

    @Test
    void addMinutes_shouldAddMinutes() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        assertThat(DateTimeUtils.addMinutes(dt, 30)).isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 30, 0));
    }

    @Test
    void addSeconds_shouldAddSeconds() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        assertThat(DateTimeUtils.addSeconds(dt, 45)).isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 0, 45));
    }

    @Test
    void daysBetween_shouldCalculateCorrectly() {
        LocalDateTime start = LocalDateTime.of(2025, 3, 10, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 3, 15, 0, 0, 0);
        assertThat(DateTimeUtils.daysBetween(start, end)).isEqualTo(5);
    }

    @Test
    void daysBetween_shouldReturnZeroForNull() {
        assertThat(DateTimeUtils.daysBetween(null, LocalDateTime.now())).isZero();
        assertThat(DateTimeUtils.daysBetween(LocalDateTime.now(), null)).isZero();
    }

    @Test
    void hoursBetween_shouldCalculateCorrectly() {
        LocalDateTime start = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 3, 15, 15, 0, 0);
        assertThat(DateTimeUtils.hoursBetween(start, end)).isEqualTo(5);
    }

    @Test
    void minutesBetween_shouldCalculateCorrectly() {
        LocalDateTime start = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 3, 15, 10, 45, 0);
        assertThat(DateTimeUtils.minutesBetween(start, end)).isEqualTo(45);
    }

    @Test
    void isPast_shouldReturnTrueForPastDate() {
        assertThat(DateTimeUtils.isPast(LocalDateTime.of(2020, 1, 1, 0, 0))).isTrue();
    }

    @Test
    void isPast_shouldReturnFalseForFutureDate() {
        assertThat(DateTimeUtils.isPast(LocalDateTime.of(2099, 1, 1, 0, 0))).isFalse();
    }

    @Test
    void isPast_shouldReturnFalseForNull() {
        assertThat(DateTimeUtils.isPast(null)).isFalse();
    }

    @Test
    void isFuture_shouldReturnTrueForFutureDate() {
        assertThat(DateTimeUtils.isFuture(LocalDateTime.of(2099, 1, 1, 0, 0))).isTrue();
    }

    @Test
    void isFuture_shouldReturnFalseForPastDate() {
        assertThat(DateTimeUtils.isFuture(LocalDateTime.of(2020, 1, 1, 0, 0))).isFalse();
    }

    @Test
    void isFuture_shouldReturnFalseForNull() {
        assertThat(DateTimeUtils.isFuture(null)).isFalse();
    }

    @Test
    void isToday_shouldReturnTrueForToday() {
        assertThat(DateTimeUtils.isToday(LocalDate.now())).isTrue();
    }

    @Test
    void isToday_shouldReturnFalseForOtherDate() {
        assertThat(DateTimeUtils.isToday(LocalDate.of(2020, 1, 1))).isFalse();
    }

    @Test
    void isToday_shouldReturnFalseForNull() {
        assertThat(DateTimeUtils.isToday(null)).isFalse();
    }

    @Test
    void isSameDay_shouldReturnTrueForSameDay() {
        LocalDateTime dt1 = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime dt2 = LocalDateTime.of(2025, 3, 15, 22, 0, 0);
        assertThat(DateTimeUtils.isSameDay(dt1, dt2)).isTrue();
    }

    @Test
    void isSameDay_shouldReturnFalseForDifferentDays() {
        LocalDateTime dt1 = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime dt2 = LocalDateTime.of(2025, 3, 16, 10, 0, 0);
        assertThat(DateTimeUtils.isSameDay(dt1, dt2)).isFalse();
    }

    @Test
    void isSameDay_shouldReturnFalseForNull() {
        assertThat(DateTimeUtils.isSameDay(null, LocalDateTime.now())).isFalse();
        assertThat(DateTimeUtils.isSameDay(LocalDateTime.now(), null)).isFalse();
    }

    @Test
    void constructor_shouldThrowUnsupportedOperationException() throws Exception {
        var constructor = DateTimeUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
