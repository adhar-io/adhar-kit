package com.adhar.kit.commons.web.versioning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parses the deprecation and sunset dates of an API version as configuration spells
 * them: an ISO-8601 date ({@code 2026-09-01}, midnight UTC), date-time with or without
 * an offset (without one, UTC), or epoch seconds.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
final class ApiVersionDates {

    private ApiVersionDates() {
    }

    /**
     * Parses a deprecation: a date, or {@code true} for "deprecated, date not stated",
     * which RFC 9745 has no word for and is sent as the epoch ({@code @0}) - a date
     * every client reads as already past.
     *
     * @param value    the configured value; null, blank and {@code false} mean not deprecated
     * @param property the property name, for the error message
     * @return the instant, or null when not deprecated
     */
    static Instant deprecation(String value, String property) {
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("false")) {
            return null;
        }
        if (value.trim().equalsIgnoreCase("true")) {
            return Instant.EPOCH;
        }
        return instant(value, property);
    }

    /**
     * Parses a date or date-time.
     *
     * @param value    the configured value; null or blank means none
     * @param property the property name, for the error message
     * @return the instant, or null
     * @throws IllegalArgumentException when the value is not a date
     */
    static Instant instant(String value, String property) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochSecond(Long.parseLong(text));
        }
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException noOffset) {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(property + " '" + value + "' is not a date: use an ISO-8601 date"
                    + " (2026-09-01), date-time (2026-09-01T00:00:00Z) or epoch seconds", e);
        }
    }
}
