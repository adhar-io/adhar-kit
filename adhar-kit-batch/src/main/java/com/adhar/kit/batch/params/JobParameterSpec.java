package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * The declared parameters of one job: what {@link AdharBatchJob} says, or a bean of this type for
 * a job whose parameters are computed. Construction checks the declaration itself (names unique
 * and not credential-like, defaults and allowed values of the declared type, patterns that
 * compile), so a broken declaration fails the application's startup, not its first run.
 *
 * @param jobName          the job's name
 * @param description      what the job does
 * @param parameters       the parameters, in declaration order
 * @param allowIncrementer whether the job may carry an incrementer
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record JobParameterSpec(String jobName, String description, List<Param> parameters, boolean allowIncrementer) {

    /**
     * @throws IllegalArgumentException naming what is wrong with the declaration
     */
    public JobParameterSpec {
        Assert.hasText(jobName, "a job parameter spec needs the job's name");
        description = description == null ? "" : description;
        parameters = List.copyOf(parameters);
        Set<String> names = new HashSet<>();
        for (Param param : parameters) {
            if (!names.add(param.name())) {
                throw new IllegalArgumentException("Job [" + jobName + "] declares parameter " + param.name()
                        + " twice");
            }
        }
    }

    /**
     * @param jobName    the job's name
     * @param annotation its declaration
     * @return the spec
     */
    public static JobParameterSpec of(String jobName, AdharBatchJob annotation) {
        List<Param> params = new ArrayList<>();
        for (JobParam declared : annotation.parameters()) {
            params.add(new Param(declared.name(), declared.type(), declared.identifying(), declared.required(),
                    declared.defaultValue().isEmpty() ? null : declared.defaultValue(),
                    declared.pattern().isEmpty() ? null : declared.pattern(), List.of(declared.allowed()),
                    declared.source(), declared.sensitive(), declared.description()));
        }
        return new JobParameterSpec(jobName, annotation.description(), params, annotation.allowIncrementer());
    }

    /**
     * @param jobName the job's name
     * @return a job that accepts no parameters
     */
    public static JobParameterSpec none(String jobName) {
        return new JobParameterSpec(jobName, "", List.of(), false);
    }

    /**
     * @param name a parameter name
     * @return its declaration
     */
    public Optional<Param> param(String name) {
        return parameters.stream().filter(param -> param.name().equals(name)).findFirst();
    }

    /**
     * @return the names whose values must never be shown: the sensitive ones
     */
    public Set<String> sensitiveNames() {
        return parameters.stream().filter(Param::sensitive).map(Param::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * One declared parameter.
     *
     * @param name         the name
     * @param type         the value's type
     * @param identifying  whether it is part of the run's identity
     * @param required     whether a run must have a value
     * @param defaultValue the value when a run gives none, {@code null} for none
     * @param pattern      a regular expression the whole value must match, {@code null} for any
     * @param allowed      the only values accepted, empty for any
     * @param source       where the value comes from when a run gives none
     * @param sensitive    whether the value is masked wherever it is shown
     * @param description  what it means
     */
    public record Param(String name, ParamType type, boolean identifying, boolean required, String defaultValue,
                        String pattern, List<String> allowed, ParamSource source, boolean sensitive,
                        String description) {

        /** A parameter name: what the CLI's {@code -p name=value} and the run form can carry. */
        static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,99}");

        /** The kit's own non-identifying parameters ({@code adhar.trigger}, ...) start with it. */
        public static final String RESERVED_PREFIX = "adhar.";

        /**
         * @throws IllegalArgumentException naming what is wrong with the declaration
         */
        public Param {
            Assert.hasText(name, "a parameter needs a name");
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Parameter name '" + name + "' must start with a letter and "
                        + "contain only letters, digits, '.', '_' and '-' (at most 100 characters)");
            }
            if (name.startsWith(RESERVED_PREFIX)) {
                throw new IllegalArgumentException("Parameter name '" + name + "' uses the prefix '"
                        + RESERVED_PREFIX + "', which the kit reserves for a run's provenance");
            }
            SecretParameterGuard.check(name);
            type = type == null ? ParamType.STRING : type;
            source = source == null ? ParamSource.INPUT : source;
            allowed = allowed == null ? List.of() : List.copyOf(allowed);
            description = description == null ? "" : description;
            if (pattern != null) {
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException ex) {
                    throw new IllegalArgumentException("Parameter " + name + " has an invalid pattern: "
                            + ex.getDescription(), ex);
                }
            }
            if (source == ParamSource.SCHEDULE && type != ParamType.DATE && type != ParamType.DATETIME) {
                throw new IllegalArgumentException("Parameter " + name + " comes from the schedule, so it must be "
                        + "a DATE or DATETIME, not " + type);
            }
            for (String value : allowed) {
                if (JobParameterValidator.typed(type, value).isEmpty()) {
                    throw new IllegalArgumentException("Parameter " + name + " allows '" + value
                            + "', which is not a " + type);
                }
            }
            if (defaultValue != null) {
                List<String> problems = JobParameterValidator.problems(name, type, pattern, allowed, defaultValue,
                        sensitive);
                if (!problems.isEmpty()) {
                    throw new IllegalArgumentException("The default of parameter " + name + " is invalid: "
                            + String.join("; ", problems));
                }
            }
        }

        /**
         * @param name the name
         * @param type the type
         * @return an optional identifying input parameter without a default
         */
        public static Param of(String name, ParamType type) {
            return new Param(name, type, true, false, null, null, List.of(), ParamSource.INPUT, false, "");
        }

        @Override
        public String toString() {
            return name + ":" + type + (identifying ? "" : "(n)") + (required ? " required" : "")
                    + (allowed.isEmpty() ? "" : " " + Arrays.toString(allowed.toArray()));
        }
    }
}
