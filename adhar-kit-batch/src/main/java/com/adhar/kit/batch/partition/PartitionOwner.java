package com.adhar.kit.batch.partition;

import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.Optional;

/**
 * The worker pod that claimed a partition, recorded in the partition's execution context before it
 * runs (design 0025 §5.10), so the manager can tell a partition that is slow from one whose worker is
 * gone ({@link WorkerLivenessWatch}).
 *
 * <p>The record names the step execution it was written for: a restart copies a partition's context
 * into a new step execution, and the copy of an earlier attempt's owner says nothing about who runs
 * the new one.</p>
 *
 * @param stepExecutionId the partition's step execution the worker claimed
 * @param pod             the worker pod's name
 * @param uid             the worker pod's uid, empty when unknown
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record PartitionOwner(long stepExecutionId, String pod, String uid) {

    /** Execution-context key: the step execution the owner claimed. */
    public static final String STEP_EXECUTION = "adhar.partition.owner.step-execution";

    /** Execution-context key: the owner pod's name. */
    public static final String POD = "adhar.partition.owner.pod";

    /** Execution-context key: the owner pod's uid. */
    public static final String UID = "adhar.partition.owner.uid";

    /**
     * @param partition the partition to record this owner in (its context, in memory)
     */
    public void writeTo(StepExecution partition) {
        ExecutionContext context = partition.getExecutionContext();
        context.putLong(STEP_EXECUTION, stepExecutionId);
        context.putString(POD, pod);
        context.putString(UID, uid);
    }

    /**
     * @param partition a partition
     * @return who claimed THIS step execution, empty when nobody has (or only an earlier attempt's copy says so)
     */
    public static Optional<PartitionOwner> of(StepExecution partition) {
        ExecutionContext context = partition.getExecutionContext();
        if (!context.containsKey(POD) || context.getLong(STEP_EXECUTION, -1L) != partition.getId()) {
            return Optional.empty();
        }
        String pod = context.getString(POD, "");
        return pod.isBlank() ? Optional.empty()
                : Optional.of(new PartitionOwner(partition.getId(), pod, context.getString(UID, "")));
    }
}
