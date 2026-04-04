package com.adhar.kit.persistence.metrics;

/**
 * Immutable snapshot of persistence query statistics.
 *
 * @param totalQueries   total number of queries executed
 * @param avgLatencyMs   average query latency in milliseconds
 * @param maxLatencyMs   maximum query latency observed in milliseconds
 * @param slowQueryCount number of queries exceeding the slow-query threshold (default 500ms)
 * @param errorCount     number of queries that resulted in errors
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record QueryStats(
        long totalQueries,
        double avgLatencyMs,
        long maxLatencyMs,
        long slowQueryCount,
        long errorCount
) {
    /**
     * Returns an empty QueryStats with all values zeroed.
     */
    public static QueryStats empty() {
        return new QueryStats(0, 0.0, 0, 0, 0);
    }
}
