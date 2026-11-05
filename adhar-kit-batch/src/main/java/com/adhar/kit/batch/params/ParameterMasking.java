package com.adhar.kit.batch.params;

import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;

import java.util.Comparator;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Renders job parameters for logs without their secrets.
 *
 * <p>Spring Batch persists parameters in clear text and its own launchers print them at INFO, so
 * a credential passed as a parameter would land in the JobRepository and the logs. Secrets
 * belong in Kubernetes Secrets, not parameters; until a run refuses such names outright, every
 * value whose parameter name looks like a credential is shown as {@value #MASK}. The kit never
 * logs parameters at INFO; it logs them, masked, at DEBUG.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class ParameterMasking {

    /** Parameter names that look like credentials (design 0025 §5.6). */
    public static final Pattern SECRET_NAME =
            Pattern.compile("(?i)pass(word)?|secret|token|credential|api[-_]?key|private[-_]?key");

    /** What a masked value is shown as. */
    public static final String MASK = "****";

    private ParameterMasking() {
    }

    /**
     * @param name a parameter name
     * @return whether the name contains a credential-like word
     */
    public static boolean isSecretName(String name) {
        return name != null && SECRET_NAME.matcher(name).find();
    }

    /**
     * Renders parameters, masking the values of credential-like names.
     *
     * @param parameters the parameters, may be {@code null}
     * @return {@code {name=value, ...}} sorted by name; identifying parameters unmarked,
     *         non-identifying ones suffixed with {@code (n)}
     */
    public static String describe(JobParameters parameters) {
        return describe(parameters, Set.of());
    }

    /**
     * Renders parameters, masking credential-like names and the given sensitive ones.
     *
     * @param parameters the parameters, may be {@code null}
     * @param sensitive  further names whose values must never be shown
     * @return {@code {name=value, ...}} sorted by name
     */
    public static String describe(JobParameters parameters, Set<String> sensitive) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        parameters.parameters().stream()
                .sorted(Comparator.comparing(JobParameter::name))
                .forEach(parameter -> joiner.add(parameter.name() + (parameter.identifying() ? "" : "(n)") + "="
                        + (isSecretName(parameter.name()) || sensitive.contains(parameter.name())
                        ? MASK : String.valueOf(parameter.value()))));
        return joiner.toString();
    }
}
