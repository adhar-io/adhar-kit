package com.adhar.kit.commons.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date and time operations.
 */
public final class DateUtils {

    public static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter ISO_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateTimeFormatter ISO_DATETIME_WITH_ZONE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // Private constructor to prevent instantiation
    private DateUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Gets the current UTC date and time.
     *
     * @return current LocalDateTime in UTC
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Gets the current date in UTC.
     *
     * @return current LocalDate in UTC
     */
    public static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * Converts LocalDateTime to UTC.
     *
     * @param dateTime the local date time
     * @param zoneId the source timezone
     * @return LocalDateTime in UTC
     */
    public static LocalDateTime toUtc(LocalDateTime dateTime, ZoneId zoneId) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Converts LocalDateTime from UTC to specified timezone.
     *
     * @param utcDateTime the UTC date time
     * @param zoneId the target timezone
     * @return LocalDateTime in target timezone
     */
    public static LocalDateTime fromUtc(LocalDateTime utcDateTime, ZoneId zoneId) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneId).toLocalDateTime();
    }

    /**
     * Formats a LocalDateTime as ISO date string.
     *
     * @param dateTime the date time to format
     * @return formatted date string
     */
    public static String formatAsIsoDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATE_FORMAT) : null;
    }

    /**
     * Formats a LocalDate as ISO date string.
     *
     * @param date the date to format
     * @return formatted date string
     */
    public static String formatAsIsoDate(LocalDate date) {
        return date != null ? date.format(ISO_DATE_FORMAT) : null;
    }

    /**
     * Formats a LocalDateTime as ISO datetime string.
     *
     * @param dateTime the date time to format
     * @return formatted datetime string
     */
    public static String formatAsIsoDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATETIME_FORMAT) : null;
    }

    /**
     * Parses an ISO date string to LocalDate.
     *
     * @param dateString the date string to parse
     * @return LocalDate or null if parsing fails
     */
    public static LocalDate parseIsoDate(String dateString) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }
        try {
            return LocalDate.parse(dateString, ISO_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parses an ISO datetime string to LocalDateTime.
     *
     * @param dateTimeString the datetime string to parse
     * @return LocalDateTime or null if parsing fails
     */
    public static LocalDateTime parseIsoDateTime(String dateTimeString) {
        if (StringUtils.isBlank(dateTimeString)) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString, ISO_DATETIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Converts LocalDateTime to java.util.Date.
     *
     * @param localDateTime the LocalDateTime to convert
     * @return Date or null if input is null
     */
    public static Date toDate(LocalDateTime localDateTime) {
        return localDateTime != null ?
            Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()) : null;
    }

    /**
     * Converts java.util.Date to LocalDateTime.
     *
     * @param date the Date to convert
     * @return LocalDateTime or null if input is null
     */
    public static LocalDateTime fromDate(Date date) {
        return date != null ?
            LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()) : null;
    }

    /**
     * Checks if a date is in the past.
     *
     * @param date the date to check
     * @return true if the date is in the past
     */
    public static boolean isPast(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }

    /**
     * Checks if a datetime is in the past.
     *
     * @param dateTime the datetime to check
     * @return true if the datetime is in the past
     */
    public static boolean isPast(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Checks if a date is in the future.
     *
     * @param date the date to check
     * @return true if the date is in the future
     */
    public static boolean isFuture(LocalDate date) {
        return date != null && date.isAfter(LocalDate.now());
    }

    /**
     * Checks if a datetime is in the future.
     *
     * @param dateTime the datetime to check
     * @return true if the datetime is in the future
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * Calculates the number of days between two dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return number of days between dates
     */
    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Calculates the number of hours between two datetimes.
     *
     * @param startDateTime the start datetime
     * @param endDateTime the end datetime
     * @return number of hours between datetimes
     */
    public static long hoursBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (startDateTime == null || endDateTime == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(startDateTime, endDateTime);
    }

    /**
     * Gets the start of day for a given date.
     *
     * @param date the date
     * @return LocalDateTime at start of day
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * Gets the end of day for a given date.
     *
     * @param date the date
     * @return LocalDateTime at end of day
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date != null ? date.atTime(23, 59, 59, 999999999) : null;
    }

    /**
     * Checks if a date falls within a date range (inclusive).
     *
     * @param date the date to check
     * @param startDate the range start date
     * @param endDate the range end date
     * @return true if date is within range
     */
    public static boolean isWithinRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (date == null || startDate == null || endDate == null) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Gets the age in years from a birth date.
     *
     * @param birthDate the birth date
     * @return age in years
     */
    public static int getAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 0;
        }
        return (int) ChronoUnit.YEARS.between(birthDate, LocalDate.now());
    }
}
