package com.adhar.kit.commons.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

    @Test
    void nowUtc_shouldReturnCurrentUTCDateTime() {
        LocalDateTime result = DateUtils.nowUtc();
        assertThat(result).isNotNull();
    }

    @Test
    void todayUtc_shouldReturnCurrentUTCDate() {
        LocalDate result = DateUtils.todayUtc();
        assertThat(result).isNotNull();
    }

    @Test
    void toUtc_shouldConvertToUTC() {
        LocalDateTime local = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime utc = DateUtils.toUtc(local, ZoneId.of("Asia/Tokyo"));
        assertThat(utc).isNotNull();
        assertThat(utc).isBefore(local); // Tokyo is ahead of UTC
    }

    @Test
    void toUtc_shouldReturnNullForNull() {
        assertThat(DateUtils.toUtc(null, ZoneOffset.UTC)).isNull();
    }

    @Test
    void fromUtc_shouldConvertFromUTC() {
        LocalDateTime utc = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime local = DateUtils.fromUtc(utc, ZoneId.of("Asia/Tokyo"));
        assertThat(local).isNotNull();
        assertThat(local).isAfter(utc); // Tokyo is ahead of UTC
    }

    @Test
    void fromUtc_shouldReturnNullForNull() {
        assertThat(DateUtils.fromUtc(null, ZoneOffset.UTC)).isNull();
    }

    @Test
    void formatAsIsoDate_dateTime_shouldFormatCorrectly() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        assertThat(DateUtils.formatAsIsoDate(dt)).isEqualTo("2025-03-15");
    }

    @Test
    void formatAsIsoDate_dateTime_shouldReturnNullForNull() {
        assertThat(DateUtils.formatAsIsoDate((LocalDateTime) null)).isNull();
    }

    @Test
    void formatAsIsoDate_date_shouldFormatCorrectly() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        assertThat(DateUtils.formatAsIsoDate(date)).isEqualTo("2025-03-15");
    }

    @Test
    void formatAsIsoDate_date_shouldReturnNullForNull() {
        assertThat(DateUtils.formatAsIsoDate((LocalDate) null)).isNull();
    }

    @Test
    void formatAsIsoDateTime_shouldFormatCorrectly() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        assertThat(DateUtils.formatAsIsoDateTime(dt)).isEqualTo("2025-03-15T10:30:45");
    }

    @Test
    void formatAsIsoDateTime_shouldReturnNullForNull() {
        assertThat(DateUtils.formatAsIsoDateTime(null)).isNull();
    }

    @Test
    void parseIsoDate_shouldParseValidDate() {
        assertThat(DateUtils.parseIsoDate("2025-03-15")).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    @Test
    void parseIsoDate_shouldReturnNullForInvalidDate() {
        assertThat(DateUtils.parseIsoDate("not-a-date")).isNull();
        assertThat(DateUtils.parseIsoDate(null)).isNull();
        assertThat(DateUtils.parseIsoDate("")).isNull();
    }

    @Test
    void parseIsoDateTime_shouldParseValidDateTime() {
        assertThat(DateUtils.parseIsoDateTime("2025-03-15T10:30:45"))
                .isEqualTo(LocalDateTime.of(2025, 3, 15, 10, 30, 45));
    }

    @Test
    void parseIsoDateTime_shouldReturnNullForInvalid() {
        assertThat(DateUtils.parseIsoDateTime("invalid")).isNull();
        assertThat(DateUtils.parseIsoDateTime(null)).isNull();
    }

    @Test
    void toDate_andFromDate_shouldRoundTrip() {
        LocalDateTime original = LocalDateTime.of(2025, 3, 15, 10, 30, 45);
        Date date = DateUtils.toDate(original);
        assertThat(date).isNotNull();
        LocalDateTime converted = DateUtils.fromDate(date);
        assertThat(converted).isEqualToIgnoringNanos(original);
    }

    @Test
    void toDate_shouldReturnNullForNull() {
        assertThat(DateUtils.toDate(null)).isNull();
    }

    @Test
    void fromDate_shouldReturnNullForNull() {
        assertThat(DateUtils.fromDate(null)).isNull();
    }

    @Test
    void isPast_date_shouldCheckCorrectly() {
        assertThat(DateUtils.isPast(LocalDate.of(2020, 1, 1))).isTrue();
        assertThat(DateUtils.isPast(LocalDate.of(2099, 1, 1))).isFalse();
        assertThat(DateUtils.isPast((LocalDate) null)).isFalse();
    }

    @Test
    void isPast_dateTime_shouldCheckCorrectly() {
        assertThat(DateUtils.isPast(LocalDateTime.of(2020, 1, 1, 0, 0))).isTrue();
        assertThat(DateUtils.isPast(LocalDateTime.of(2099, 1, 1, 0, 0))).isFalse();
        assertThat(DateUtils.isPast((LocalDateTime) null)).isFalse();
    }

    @Test
    void isFuture_date_shouldCheckCorrectly() {
        assertThat(DateUtils.isFuture(LocalDate.of(2099, 1, 1))).isTrue();
        assertThat(DateUtils.isFuture(LocalDate.of(2020, 1, 1))).isFalse();
        assertThat(DateUtils.isFuture((LocalDate) null)).isFalse();
    }

    @Test
    void isFuture_dateTime_shouldCheckCorrectly() {
        assertThat(DateUtils.isFuture(LocalDateTime.of(2099, 1, 1, 0, 0))).isTrue();
        assertThat(DateUtils.isFuture(LocalDateTime.of(2020, 1, 1, 0, 0))).isFalse();
        assertThat(DateUtils.isFuture((LocalDateTime) null)).isFalse();
    }

    @Test
    void daysBetween_shouldCalculateCorrectly() {
        LocalDate start = LocalDate.of(2025, 3, 10);
        LocalDate end = LocalDate.of(2025, 3, 15);
        assertThat(DateUtils.daysBetween(start, end)).isEqualTo(5);
    }

    @Test
    void daysBetween_shouldReturnZeroForNull() {
        assertThat(DateUtils.daysBetween(null, LocalDate.now())).isZero();
        assertThat(DateUtils.daysBetween(LocalDate.now(), null)).isZero();
    }

    @Test
    void hoursBetween_shouldCalculateCorrectly() {
        LocalDateTime start = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 3, 15, 15, 0, 0);
        assertThat(DateUtils.hoursBetween(start, end)).isEqualTo(5);
    }

    @Test
    void hoursBetween_shouldReturnZeroForNull() {
        assertThat(DateUtils.hoursBetween(null, LocalDateTime.now())).isZero();
    }

    @Test
    void startOfDay_shouldReturnStartOfDay() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        assertThat(DateUtils.startOfDay(date)).isEqualTo(LocalDateTime.of(2025, 3, 15, 0, 0, 0));
    }

    @Test
    void startOfDay_shouldReturnNullForNull() {
        assertThat(DateUtils.startOfDay(null)).isNull();
    }

    @Test
    void endOfDay_shouldReturnEndOfDay() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        LocalDateTime end = DateUtils.endOfDay(date);
        assertThat(end).isNotNull();
        assertThat(end.getHour()).isEqualTo(23);
        assertThat(end.getMinute()).isEqualTo(59);
        assertThat(end.getSecond()).isEqualTo(59);
    }

    @Test
    void endOfDay_shouldReturnNullForNull() {
        assertThat(DateUtils.endOfDay(null)).isNull();
    }

    @Test
    void isWithinRange_shouldCheckCorrectly() {
        LocalDate date = LocalDate.of(2025, 3, 15);
        LocalDate start = LocalDate.of(2025, 3, 10);
        LocalDate end = LocalDate.of(2025, 3, 20);
        assertThat(DateUtils.isWithinRange(date, start, end)).isTrue();
        assertThat(DateUtils.isWithinRange(start, start, end)).isTrue();
        assertThat(DateUtils.isWithinRange(end, start, end)).isTrue();
        assertThat(DateUtils.isWithinRange(LocalDate.of(2025, 1, 1), start, end)).isFalse();
    }

    @Test
    void isWithinRange_shouldReturnFalseForNull() {
        assertThat(DateUtils.isWithinRange(null, LocalDate.now(), LocalDate.now())).isFalse();
        assertThat(DateUtils.isWithinRange(LocalDate.now(), null, LocalDate.now())).isFalse();
        assertThat(DateUtils.isWithinRange(LocalDate.now(), LocalDate.now(), null)).isFalse();
    }

    @Test
    void getAge_shouldCalculateCorrectly() {
        LocalDate birthDate = LocalDate.now().minusYears(25);
        assertThat(DateUtils.getAge(birthDate)).isEqualTo(25);
    }

    @Test
    void getAge_shouldReturnZeroForNull() {
        assertThat(DateUtils.getAge(null)).isZero();
    }

    @Test
    void constructor_shouldThrowUnsupportedOperationException() throws Exception {
        var constructor = DateUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
