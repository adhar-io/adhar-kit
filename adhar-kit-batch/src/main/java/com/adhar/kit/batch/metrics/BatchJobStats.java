package com.adhar.kit.batch.metrics;

import java.time.Instant;

/**
 * Statistics of a batch job, computed from the JobRepository
 * ({@code BatchOperator#getJobStats(String)}), so every instance of a service and every restart
 * sees the same numbers.
 *
 * @param totalExecutions   executions of the job's recent JobInstances
 * @param successCount      executions that COMPLETED
 * @param failureCount      executions that ended unsuccessfully (FAILED, ABANDONED, UNKNOWN)
 * @param avgDurationMs     mean duration of the executions that ended, in milliseconds
 * @param lastExecutionTime start of the most recent execution, {@code null} when it never ran
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record BatchJobStats(
        long totalExecutions,
        long successCount,
        long failureCount,
        double avgDurationMs,
        Instant lastExecutionTime
) {
}
