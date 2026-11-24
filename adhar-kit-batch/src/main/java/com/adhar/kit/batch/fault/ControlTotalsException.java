package com.adhar.kit.batch.fault;

import org.springframework.batch.core.job.JobExecution;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A step's control totals did not balance (design 0025 §5.4, §5.7): an item it read is not
 * accounted for as written, filtered or skipped, or a count differs from the one the job declared
 * it expects. Running the same step again cannot change what it already committed, so the run
 * fails without a retry (exit 3) and its termination summary carries the reason
 * {@code control-totals}.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class ControlTotalsException extends NonRetryableBatchException {

    /** The message, as {@link #recorded(String)} reads it back from a step's exit description. */
    private static final Pattern MESSAGE = Pattern.compile("Control totals of step \\[(.+?)] do not balance: (.+)");

    private final String stepName;
    private final List<String> mismatches;

    /**
     * @param stepName   the step whose totals did not balance
     * @param mismatches each total that differs, in words
     */
    public ControlTotalsException(String stepName, List<String> mismatches) {
        super("Control totals of step [" + stepName + "] do not balance: " + String.join("; ", mismatches));
        this.stepName = stepName;
        this.mismatches = List.copyOf(mismatches);
    }

    /**
     * @return the step whose totals did not balance
     */
    public String getStepName() {
        return stepName;
    }

    /**
     * @return each total that differs, in words
     */
    public List<String> getMismatches() {
        return mismatches;
    }

    /**
     * The mismatch a step recorded in another process: a remote partition's manager has only the
     * partition's exit description, the stack trace of its failure.
     *
     * @param exitDescription a failed step's exit description
     * @return the mismatch it records, empty when it records none
     */
    public static Optional<ControlTotalsException> recorded(String exitDescription) {
        if (exitDescription == null) {
            return Optional.empty();
        }
        Matcher matcher = MESSAGE.matcher(exitDescription);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new ControlTotalsException(matcher.group(1),
                Arrays.stream(matcher.group(2).strip().split("; ")).toList()));
    }

    /**
     * @param execution a finished execution
     * @return whether one of its failures, anywhere in a cause chain, is a control-totals mismatch
     */
    public static boolean isCause(JobExecution execution) {
        for (Throwable failure : execution.getAllFailureExceptions()) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
                if (cause instanceof ControlTotalsException) {
                    return true;
                }
            }
        }
        return false;
    }
}
