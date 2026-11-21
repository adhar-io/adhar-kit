package com.adhar.kit.batch.ops;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The ops commands a run pod carries out instead of its business job when
 * {@code ADHAR_BATCH_COMMAND} names one (design 0025 §3.4, §5.2, §8.1). The labels are the
 * values the CLI ({@code adhar batch abandon|recover|dlq --replay}) and the Console put in that
 * variable.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public enum BatchCommand {

    /** Mark an ended execution ABANDONED, so its identity is never restarted (exit 6 on a rerun). */
    ABANDON("abandon", true),

    /** Mark an execution left STARTED by a dead owner FAILED, so its identity can run again. */
    RECOVER("recover", true),

    /** Ask a running execution to stop at its next chunk boundary (its run then exits 4). */
    STOP("stop", true),

    /** Report an execution, the given one or the job's latest, and change nothing. */
    STATUS("status", false),

    /** Delete the job's JobRepository metadata the retention no longer keeps (D4). */
    HOUSEKEEPING("housekeeping", false),

    /** Send the items an execution dead-lettered through its step's processor and writer again (A3.6). */
    DLQ_REPLAY("dlq-replay", true);

    private final String label;
    private final boolean needsExecution;

    BatchCommand(String label, boolean needsExecution) {
        this.label = label;
        this.needsExecution = needsExecution;
    }

    /**
     * @return the {@code ADHAR_BATCH_COMMAND} value
     */
    public String label() {
        return label;
    }

    /**
     * @return whether the command acts on {@code ADHAR_BATCH_EXECUTION_ID}, which must then be set
     */
    public boolean needsExecution() {
        return needsExecution;
    }

    /**
     * @param value an {@code ADHAR_BATCH_COMMAND} value
     * @return the command, when the value names one (ignoring case and surrounding blanks)
     */
    public static Optional<BatchCommand> parse(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(command -> command.label.equals(normalized)).findFirst();
    }
}
