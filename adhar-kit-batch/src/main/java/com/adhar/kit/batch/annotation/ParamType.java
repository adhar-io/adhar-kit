package com.adhar.kit.batch.annotation;

/**
 * The type of a {@link JobParam} value, and of the job parameter it becomes.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum ParamType {
    /** Any text ({@code String}). */
    STRING,
    /** A whole number ({@code Long}). */
    LONG,
    /** A decimal number ({@code Double}). */
    DOUBLE,
    /** {@code true} or {@code false} ({@code Boolean}). */
    BOOLEAN,
    /** An ISO date, {@code 2026-11-03} ({@code LocalDate}). */
    DATE,
    /** An ISO date-time, {@code 2026-11-03T17:30:00} or with an offset ({@code LocalDateTime}). */
    DATETIME
}
