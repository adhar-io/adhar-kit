package com.adhar.kit.core.util;

import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * Date and Time utility class for common temporal operations.
 *
 * <p>Provides utilities for date/time manipulation, formatting, and conversion
 * using Java 8+ time API.</p>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Current timestamps
 * LocalDateTime now = AdharDateTimeUtils.now();
 * Instant instant = AdharDateTimeUtils.nowInstant();
 * long epochMillis = AdharDateTimeUtils.currentTimeMillis();
 *
 * // Formatting
 * String formatted = AdharDateTimeUtils.format(now, "yyyy-MM-dd HH:mm:ss");
 * String iso = AdharDateTimeUtils.toIsoString(now);
 *
 * // Parsing
 * LocalDateTime parsed = AdharDateTimeUtils.parse("2025-11-02", "yyyy-MM-dd");
 *
 * // Calculations
 * LocalDateTime tomorrow = AdharDateTimeUtils.addDays(now, 1);
 * long daysBetween = AdharDateTimeUtils.daysBetween(start, end);
 *
 * // Business days
 * LocalDate nextBusinessDay = AdharDateTimeUtils.nextBusinessDay(date);
 * boolean isBusiness = AdharDateTimeUtils.isBusinessDay(date);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@UtilityClass
public class AdharDateTimeUtils {

    public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;
    public static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ==================== Current Time ====================

    /**
     * Gets the current LocalDateTime.
     *
     * @return current LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Gets the current Instant.
     *
     * @return current Instant
     */
    public static Instant nowInstant() {
        return Instant.now();
    }

    /**
     * Gets the current epoch milliseconds.
     *
     * @return current time in milliseconds
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Gets the current LocalDate.
     *
     * @return current LocalDate
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Gets tomorrow's date.
     *
     * @return tomorrow's LocalDate
     */
    public static LocalDate tomorrow() {
        return LocalDate.now().plusDays(1);
    }

    /**
     * Gets yesterday's date.
     *
     * @return yesterday's LocalDate
     */
    public static LocalDate yesterday() {
        return LocalDate.now().minusDays(1);
    }

    // ==================== Formatting ====================

    /**
     * Formats a LocalDateTime with the given pattern.
     *
     * @param dateTime the date time to format
     * @param pattern the pattern
     * @return formatted string
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Formats a LocalDate with the given pattern.
     *
     * @param date the date to format
     * @param pattern the pattern
     * @return formatted string
     */
    public static String format(LocalDate date, String pattern) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Converts LocalDateTime to ISO 8601 string.
     *
     * @param dateTime the date time
     * @return ISO 8601 string
     */
    public static String toIsoString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(ISO_DATE_TIME);
    }

    /**
     * Converts LocalDate to ISO 8601 string.
     *
     * @param date the date
     * @return ISO 8601 date string
     */
    public static String toIsoString(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(ISO_DATE);
    }

    // ==================== Parsing ====================

    /**
     * Parses a string to LocalDateTime using the given pattern.
     *
     * @param dateTimeStr the date time string
     * @param pattern the pattern
     * @return parsed LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Parses a string to LocalDate using the given pattern.
     *
     * @param dateStr the date string
     * @param pattern the pattern
     * @return parsed LocalDate
     */
    public static LocalDate parseDate(String dateStr, String pattern) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Parses an ISO 8601 string to LocalDateTime.
     *
     * @param isoString the ISO string
     * @return parsed LocalDateTime
     */
    public static LocalDateTime parseIso(String isoString) {
        if (isoString == null || isoString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(isoString, ISO_DATE_TIME);
    }

    // ==================== Conversion ====================

    /**
     * Converts LocalDateTime to Date.
     *
     * @param dateTime the LocalDateTime
     * @return Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Converts Date to LocalDateTime.
     *
     * @param date the Date
     * @return LocalDateTime
     */
    public static LocalDateTime fromDate(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Converts epoch milliseconds to LocalDateTime.
     *
     * @param epochMillis epoch milliseconds
     * @return LocalDateTime
     */
    public static LocalDateTime fromEpochMillis(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    /**
     * Converts LocalDateTime to epoch milliseconds.
     *
     * @param dateTime the LocalDateTime
     * @return epoch milliseconds
     */
    public static long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ==================== Date Arithmetic ====================

    /**
     * Adds days to a LocalDateTime.
     *
     * @param dateTime the date time
     * @param days number of days to add
     * @return new LocalDateTime
     */
    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.plusDays(days) : null;
    }

    /**
     * Adds hours to a LocalDateTime.
     *
     * @param dateTime the date time
     * @param hours number of hours to add
     * @return new LocalDateTime
     */
    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        return dateTime != null ? dateTime.plusHours(hours) : null;
    }

    /**
     * Adds minutes to a LocalDateTime.
     *
     * @param dateTime the date time
     * @param minutes number of minutes to add
     * @return new LocalDateTime
     */
    public static LocalDateTime addMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime != null ? dateTime.plusMinutes(minutes) : null;
    }

    /**
     * Subtracts days from a LocalDateTime.
     *
     * @param dateTime the date time
     * @param days number of days to subtract
     * @return new LocalDateTime
     */
    public static LocalDateTime subtractDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.minusDays(days) : null;
    }

    // ==================== Date Comparison ====================

    /**
     * Checks if a date is before another date.
     *
     * @param date1 first date
     * @param date2 second date
     * @return true if date1 is before date2
     */
    public static boolean isBefore(LocalDateTime date1, LocalDateTime date2) {
        return date1 != null && date2 != null && date1.isBefore(date2);
    }

    /**
     * Checks if a date is after another date.
     *
     * @param date1 first date
     * @param date2 second date
     * @return true if date1 is after date2
     */
    public static boolean isAfter(LocalDateTime date1, LocalDateTime date2) {
        return date1 != null && date2 != null && date1.isAfter(date2);
    }

    /**
     * Checks if a date is between two dates (inclusive).
     *
     * @param date the date to check
     * @param start start date
     * @param end end date
     * @return true if date is between start and end
     */
    public static boolean isBetween(LocalDateTime date, LocalDateTime start, LocalDateTime end) {
        return date != null && start != null && end != null &&
               !date.isBefore(start) && !date.isAfter(end);
    }

    // ==================== Date Calculations ====================

    /**
     * Calculates days between two dates.
     *
     * @param start start date
     * @param end end date
     * @return number of days
     */
    public static long daysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Calculates hours between two dates.
     *
     * @param start start date
     * @param end end date
     * @return number of hours
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * Calculates minutes between two dates.
     *
     * @param start start date
     * @param end end date
     * @return number of minutes
     */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(start, end);
    }

    // ==================== Business Day Operations ====================

    /**
     * Checks if a date is a business day (Monday-Friday).
     *
     * @param date the date to check
     * @return true if business day
     */
    public static boolean isBusinessDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * Gets the next business day.
     *
     * @param date the starting date
     * @return next business day
     */
    public static LocalDate nextBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate next = date.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Gets the previous business day.
     *
     * @param date the starting date
     * @return previous business day
     */
    public static LocalDate previousBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate prev = date.minusDays(1);
        while (!isBusinessDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    // ==================== Start/End of Period ====================

    /**
     * Gets the start of the day (midnight).
     *
     * @param date the date
     * @return start of day
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * Gets the end of the day (23:59:59.999).
     *
     * @param date the date
     * @return end of day
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date != null ? date.atTime(23, 59, 59, 999_999_999) : null;
    }

    /**
     * Gets the start of the month.
     *
     * @param date the date
     * @return start of month
     */
    public static LocalDate startOfMonth(LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.firstDayOfMonth()) : null;
    }

    /**
     * Gets the end of the month.
     *
     * @param date the date
     * @return end of month
     */
    public static LocalDate endOfMonth(LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.lastDayOfMonth()) : null;
    }

    /**
     * Gets the start of the year.
     *
     * @param date the date
     * @return start of year
     */
    public static LocalDate startOfYear(LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.firstDayOfYear()) : null;
    }

    /**
     * Gets the end of the year.
     *
     * @param date the date
     * @return end of year
     */
    public static LocalDate endOfYear(LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.lastDayOfYear()) : null;
    }
}

