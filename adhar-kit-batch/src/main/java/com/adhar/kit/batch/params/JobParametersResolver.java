package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.params.JobParameterSpec.Param;
import com.adhar.kit.batch.runtime.Json;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Turns a run's input into job parameters, validated against the job's {@link JobParameterSpec}.
 *
 * <p>A value comes from the run's input ({@code ADHAR_BATCH_PARAMS}) first, then from the
 * schedule ({@link ParamSource#SCHEDULE}: the business date is the nominal scheduled time in the
 * job's time zone, never the current time), then from the declared default. A schedule parameter
 * that none of the three supply is refused with reason {@value ParameterValidationException#BUSINESS_DATE}:
 * the runner would rather fail than invent an identity. Undeclared and credential-like names, a
 * missing required value, and a value of the wrong type, outside its pattern or outside its
 * allowed list are refused with reason {@value ParameterValidationException#PARAMETERS}. Every
 * problem is reported at once.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class JobParametersResolver {

    /** The longest value {@code BATCH_JOB_EXECUTION_PARAMS.PARAMETER_VALUE} holds. */
    static final int MAX_VALUE_LENGTH = 2500;

    /**
     * @param spec          the job's declared parameters
     * @param input         the run's values, as given
     * @param scheduledTime the run's nominal scheduled time, empty for an unscheduled run
     * @param zone          the job's time zone
     * @return the job parameters and the identity they make
     * @throws ParameterValidationException listing every problem
     */
    public ResolvedParameters resolve(JobParameterSpec spec, Map<String, String> input,
                                      Optional<OffsetDateTime> scheduledTime, ZoneId zone) {
        List<String> problems = new ArrayList<>();
        boolean businessDateMissing = false;
        for (String name : new TreeSet<>(input.keySet())) {
            if (ParameterMasking.isSecretName(name)) {
                problems.add(SecretParameterGuard.message(name));
            } else if (spec.param(name).isEmpty()) {
                problems.add(name + " is not a parameter of this job; it accepts "
                        + spec.parameters().stream().map(Param::name).toList());
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Param param : spec.parameters()) {
            String value = input.get(param.name());
            if (value == null && param.source() == ParamSource.SCHEDULE && scheduledTime.isPresent()) {
                value = fromSchedule(param, scheduledTime.get(), zone);
            }
            if (value == null) {
                value = param.defaultValue();
            }
            if (value == null) {
                if (param.source() == ParamSource.SCHEDULE) {
                    businessDateMissing = true;
                    problems.add(param.name() + " comes from the schedule, but this run has no scheduled time: "
                            + "give it explicitly");
                } else if (param.required()) {
                    problems.add(param.name() + " is required");
                }
                continue;
            }
            List<String> invalid = JobParameterValidator.problems(param, value);
            if (invalid.isEmpty() && !isText(value)) {
                invalid = List.of(param.name() + " is not valid text (it holds a lone surrogate)");
            }
            if (invalid.isEmpty()) {
                invalid = storable(param.name(), value);
            }
            problems.addAll(invalid);
            values.put(param.name(), value);
        }
        if (!problems.isEmpty()) {
            boolean onlyDate = businessDateMissing && problems.size() == 1;
            throw new ParameterValidationException(spec.jobName(), problems,
                    onlyDate ? ParameterValidationException.BUSINESS_DATE : ParameterValidationException.PARAMETERS);
        }
        return build(spec, values);
    }

    private static ResolvedParameters build(JobParameterSpec spec, Map<String, String> values) {
        JobParametersBuilder builder = new JobParametersBuilder();
        Map<String, String> identifying = new LinkedHashMap<>();
        LocalDate businessDate = null;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Param param = spec.param(entry.getKey()).orElseThrow();
            Object typed = JobParameterValidator.typed(param, entry.getValue()).orElseThrow();
            add(builder, param.name(), typed, param.identifying());
            if (param.identifying()) {
                identifying.put(param.name(), entry.getValue());
            }
            if (param.type() == ParamType.DATE && (businessDate == null || param.source() == ParamSource.SCHEDULE)) {
                businessDate = (LocalDate) typed;
            }
        }
        return new ResolvedParameters(builder.toJobParameters(), identifying, Optional.ofNullable(businessDate));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(JobParametersBuilder builder, String name, Object value, boolean identifying) {
        builder.addJobParameter(name, value, (Class) value.getClass(), identifying);
    }

    private static String fromSchedule(Param param, OffsetDateTime scheduledTime, ZoneId zone) {
        var local = scheduledTime.atZoneSameInstant(zone);
        return param.type() == ParamType.DATE ? local.toLocalDate().toString()
                : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(local.toLocalDateTime());
    }

    /**
     * PostgreSQL text holds no U+0000 and {@code PARAMETER_VALUE} is VARCHAR(2500): such a value
     * fails the execution's insert, exit 1, which the Job retries to no end but its backoff limit (2026-11-14).
     */
    private static List<String> storable(String name, String value) {
        if (value.indexOf('\u0000') >= 0) {
            return List.of(name + " holds a NUL character, which the JobRepository cannot store");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return List.of(name + " is longer than " + MAX_VALUE_LENGTH + " characters, the JobRepository's limit");
        }
        return List.of();
    }

    private static boolean isText(String value) {
        try {
            Json.quote(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * A run's resolved parameters.
     *
     * @param parameters   the declared parameters as typed job parameters
     * @param identifying  the identifying values as given, which name the run ({@link RunIdentity})
     * @param businessDate the run's business date: the schedule-sourced {@code DATE}, else the
     *                     first {@code DATE} parameter
     */
    public record ResolvedParameters(JobParameters parameters, Map<String, String> identifying,
                                     Optional<LocalDate> businessDate) {

        /**
         * @return the run's hash per naming v1
         */
        public String hash8() {
            return RunIdentity.hash8(identifying);
        }
    }
}
