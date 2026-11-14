package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.params.JobParameterSpec.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks one value against its declared {@link Param}: the type, the pattern and the allowed
 * values. Values are compared as given: the pattern and the allowed list see the text the run
 * supplied, which is also the text the run's name was hashed from.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class JobParameterValidator {

    private JobParameterValidator() {
    }

    /**
     * @param param the declaration
     * @param value the value as given
     * @return what is wrong with it, empty when it is valid
     */
    public static List<String> problems(Param param, String value) {
        return problems(param.name(), param.type(), param.pattern(), param.allowed(), value, param.sensitive());
    }

    /** A sensitive value is never echoed: the message reaches the log and the termination summary. */
    static List<String> problems(String name, ParamType type, String pattern, List<String> allowed, String value,
                                 boolean sensitive) {
        List<String> problems = new ArrayList<>();
        String shown = sensitive ? ParameterMasking.MASK : value;
        if (typed(type, value).isEmpty()) {
            problems.add(name + " must be " + describe(type) + ", was '" + shown + "'");
            return problems;
        }
        if (pattern != null && !value.matches(pattern)) {
            problems.add(name + " must match " + pattern + ", was '" + shown + "'");
        }
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            problems.add(name + " must be one of " + allowed + ", was '" + shown + "'");
        }
        return problems;
    }

    /**
     * @param param the declaration
     * @param value the value as given
     * @return the value as the job parameter's type, empty when it is not of that type
     */
    public static Optional<Object> typed(Param param, String value) {
        return typed(param.type(), value);
    }

    static Optional<Object> typed(ParamType type, String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(switch (type) {
                case STRING -> value;
                case LONG -> Long.parseLong(value);
                case DOUBLE -> {
                    double number = Double.parseDouble(value);
                    if (!Double.isFinite(number) || value.strip().length() != value.length()) {
                        throw new NumberFormatException(value);
                    }
                    yield number;
                }
                case BOOLEAN -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IllegalArgumentException(value);
                    }
                    yield Boolean.valueOf(value);
                }
                case DATE -> LocalDate.parse(value);
                case DATETIME -> dateTime(value);
            });
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /**
     * A date-time with an offset becomes the same instant in UTC; one without an offset is taken
     * as written.
     */
    private static LocalDateTime dateTime(String value) {
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return LocalDateTime.parse(value);
        }
    }

    private static String describe(ParamType type) {
        return switch (type) {
            case STRING -> "text";
            case LONG -> "a whole number";
            case DOUBLE -> "a finite number";
            case BOOLEAN -> "true or false";
            case DATE -> "an ISO date (2026-11-03)";
            case DATETIME -> "an ISO date-time (2026-11-03T17:30:00, optionally with an offset)";
        };
    }
}
