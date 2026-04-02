package com.adhar.kit.batch.metrics;

import java.time.Instant;

/**
 * Immutable record holding statistics for a batch job.
 *
 * @param totalExecutions  total number of times the job has been executed
 * @param successCount     number of successful executions
 * @param failureCount     number of failed executions
 * @param avgDurationMs    average execution duration in milliseconds
 * @param lastExecutionTime the timestamp of the most recent execution
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record BatchJobStats(
        long totalExecutions,
        long successCount,
        long failureCount,
        double avgDurationMs,
        Instant lastExecutionTime
) {
}
