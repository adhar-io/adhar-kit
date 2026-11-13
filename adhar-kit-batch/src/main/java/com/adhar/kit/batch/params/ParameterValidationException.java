package com.adhar.kit.batch.params;

import com.adhar.kit.batch.runtime.ExitCodes;
import org.springframework.boot.ExitCodeGenerator;

import java.util.List;

/**
 * A run's parameters are invalid: retrying cannot help, so the run exits with
 * {@link ExitCodes#INVALID_REQUEST} (2) and the message is what the Console and the CLI show.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class ParameterValidationException extends IllegalArgumentException implements ExitCodeGenerator {

    /** The termination summary's {@code reason} for invalid parameters. */
    public static final String PARAMETERS = "parameters";

    /** The termination summary's {@code reason} for a scheduled run whose business date is unknown. */
    public static final String BUSINESS_DATE = "business-date";

    private final transient List<String> problems;
    private final String reason;

    /**
     * @param jobName  the job
     * @param problems what is wrong, one entry per problem
     * @param reason   {@link #PARAMETERS} or {@link #BUSINESS_DATE}
     */
    public ParameterValidationException(String jobName, List<String> problems, String reason) {
        super("Invalid parameters for job [" + jobName + "]: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
        this.reason = reason;
    }

    /**
     * @return what is wrong, one entry per problem
     */
    public List<String> getProblems() {
        return problems;
    }

    /**
     * @return the termination summary's {@code reason}
     */
    public String getReason() {
        return reason;
    }

    @Override
    public int getExitCode() {
        return ExitCodes.INVALID_REQUEST;
    }
}
