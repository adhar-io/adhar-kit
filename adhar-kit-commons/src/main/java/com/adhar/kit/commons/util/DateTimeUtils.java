package com.adhar.kit.commons.util;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date and time operations.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Current timestamps
 * LocalDateTime now = DateTimeUtils.now();
 * String formatted = DateTimeUtils.formatDateTime(now);
 *
 * // Conversions
 * Date date = DateTimeUtils.toDate(localDateTime);
 * LocalDateTime ldt = DateTimeUtils.fromDate(date);
 *
 * // Calculations
 * LocalDateTime future = DateTimeUtils.addDays(now, 7);
 * long days = DateTimeUtils.daysBetween(start, end);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class DateTimeUtils {

    /**
     * ISO date-time formatter (yyyy-MM-dd'T'HH:mm:ss).
     */
    public static final DateTimeFormatter ISO_DATETIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * ISO date formatter (yyyy-MM-dd).
     */
    public static final DateTimeFormatter ISO_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * ISO time formatter (HH:mm:ss).
     */
    public static final DateTimeFormatter ISO_TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private DateTimeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== CURRENT TIME ====================

    /**
     * Gets current LocalDateTime.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Gets current LocalDateTime in UTC.
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Gets current LocalDate.
     */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Gets current LocalTime.
     */
    public static LocalTime currentTime() {
        return LocalTime.now();
    }

    /**
     * Gets current epoch milliseconds.
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Gets current epoch seconds.
     */
    public static long currentTimeSeconds() {
        return Instant.now().getEpochSecond();
    }

    // ==================== FORMATTING ====================

    /**
     * Formats LocalDateTime to ISO format.
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ISO_DATETIME);
    }

    /**
     * Formats LocalDate to ISO format.
     */
    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(ISO_DATE);
    }

    /**
     * Formats LocalTime to ISO format.
     */
    public static String formatTime(LocalTime time) {
        return time == null ? null : time.format(ISO_TIME);
    }

    /**
     * Formats with custom pattern.
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime == null ? null :
            dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== PARSING ====================

    /**
     * Parses ISO date-time string.
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        return dateTimeString == null ? null :
            LocalDateTime.parse(dateTimeString, ISO_DATETIME);
    }

    /**
     * Parses ISO date string.
     */
    public static LocalDate parseDate(String dateString) {
        return dateString == null ? null :
            LocalDate.parse(dateString, ISO_DATE);
    }

    /**
     * Parses ISO time string.
     */
    public static LocalTime parseTime(String timeString) {
        return timeString == null ? null :
            LocalTime.parse(timeString, ISO_TIME);
    }

    // ==================== CONVERSIONS ====================

    /**
     * Converts LocalDateTime to Date.
     */
    public static Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null :
            Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Converts Date to LocalDateTime.
     */
    public static LocalDateTime fromDate(Date date) {
        return date == null ? null :
            LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Converts LocalDateTime to epoch milliseconds.
     */
    public static long toEpochMillis(LocalDateTime dateTime) {
        return dateTime == null ? 0 :
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Converts epoch milliseconds to LocalDateTime.
     */
    public static LocalDateTime fromEpochMillis(long epochMillis) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
        );
    }

    // ==================== CALCULATIONS ====================

    /**
     * Adds days to date-time.
     */
    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime == null ? null : dateTime.plusDays(days);
    }

    /**
     * Adds hours to date-time.
     */
    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        return dateTime == null ? null : dateTime.plusHours(hours);
    }

    /**
     * Adds minutes to date-time.
     */
    public static LocalDateTime addMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime == null ? null : dateTime.plusMinutes(minutes);
    }

    /**
     * Adds seconds to date-time.
     */
    public static LocalDateTime addSeconds(LocalDateTime dateTime, long seconds) {
        return dateTime == null ? null : dateTime.plusSeconds(seconds);
    }

    /**
     * Calculates days between two dates.
     */
    public static long daysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * Calculates hours between two date-times.
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * Calculates minutes between two date-times.
     */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.MINUTES.between(start, end);
    }

    // ==================== COMPARISONS ====================

    /**
     * Checks if date is in the past.
     */
    public static boolean isPast(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(now());
    }

    /**
     * Checks if date is in the future.
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isAfter(now());
    }

    /**
     * Checks if date is today.
     */
    public static boolean isToday(LocalDate date) {
        return date != null && date.isEqual(today());
    }

    /**
     * Checks if two date-times are on the same day.
     */
    public static boolean isSameDay(LocalDateTime dt1, LocalDateTime dt2) {
        if (dt1 == null || dt2 == null) return false;
        return dt1.toLocalDate().isEqual(dt2.toLocalDate());
    }
}

