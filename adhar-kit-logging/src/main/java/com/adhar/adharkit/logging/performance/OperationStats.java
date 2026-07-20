package com.adhar.adharkit.logging.performance;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe accumulated timing statistics for one named operation.
 */
public class OperationStats {

    private final LongAdder count = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder totalMs = new LongAdder();
    private final AtomicLong minMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxMs = new AtomicLong(Long.MIN_VALUE);

    /**
     * Records one execution.
     *
     * @param durationMs elapsed time in milliseconds
     * @param success    whether the execution succeeded
     */
    public void record(long durationMs, boolean success) {
        count.increment();
        if (!success) {
            failures.increment();
        }
        totalMs.add(durationMs);
        minMs.accumulateAndGet(durationMs, Math::min);
        maxMs.accumulateAndGet(durationMs, Math::max);
    }

    /**
     * Takes a consistent-enough snapshot of the accumulated values.
     *
     * @return the current statistics
     */
    public Snapshot snapshot() {
        long n = count.sum();
        long total = totalMs.sum();
        return new Snapshot(
                n,
                failures.sum(),
                total,
                n > 0 ? minMs.get() : 0,
                n > 0 ? maxMs.get() : 0,
                n > 0 ? Math.round((double) total / n * 100.0) / 100.0 : 0.0);
    }

    /**
     * Immutable view of the statistics at one point in time.
     *
     * @param count    number of recorded executions
     * @param failures number of failed executions
     * @param totalMs  total elapsed milliseconds
     * @param minMs    fastest execution in milliseconds
     * @param maxMs    slowest execution in milliseconds
     * @param avgMs    average execution time in milliseconds
     */
    public record Snapshot(long count, long failures, long totalMs, long minMs, long maxMs, double avgMs) {
    }
}
