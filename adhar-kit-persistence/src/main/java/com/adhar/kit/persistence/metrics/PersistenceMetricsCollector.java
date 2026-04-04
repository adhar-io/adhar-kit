package com.adhar.kit.persistence.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Collects persistence-layer metrics (query latency, transaction duration, error counts)
 * and exposes them via Micrometer when a {@link MeterRegistry} is available.
 *
 * <p>Thread-safe -- all internal counters use atomic or adder primitives.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class PersistenceMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(PersistenceMetricsCollector.class);

    private final long slowQueryThresholdMs;

    // Internal accumulators
    private final LongAdder totalQueries = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final LongAdder slowQueryCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    // Micrometer instruments (nullable when no registry is present)
    private final Timer queryTimer;
    private final Timer transactionTimer;
    private final Counter errorCounter;
    private final Counter slowQueryCounter;

    /**
     * Creates a collector backed by the given Micrometer registry.
     *
     * @param registry              the meter registry (may be {@code null})
     * @param slowQueryThresholdMs  threshold in milliseconds above which a query is considered slow
     */
    public PersistenceMetricsCollector(MeterRegistry registry, long slowQueryThresholdMs) {
        this.slowQueryThresholdMs = slowQueryThresholdMs;

        if (registry != null) {
            this.queryTimer = Timer.builder("adhar.persistence.query.duration")
                    .description("Time spent executing persistence queries")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);
            this.transactionTimer = Timer.builder("adhar.persistence.transaction.duration")
                    .description("Time spent in programmatic transactions")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry);
            this.errorCounter = Counter.builder("adhar.persistence.errors")
                    .description("Total persistence operation errors")
                    .register(registry);
            this.slowQueryCounter = Counter.builder("adhar.persistence.slow.queries")
                    .description("Number of queries exceeding the slow-query threshold")
                    .register(registry);
        } else {
            this.queryTimer = null;
            this.transactionTimer = null;
            this.errorCounter = null;
            this.slowQueryCounter = null;
        }
        log.info("PersistenceMetricsCollector initialized (slowQueryThresholdMs={})", slowQueryThresholdMs);
    }

    /**
     * Records a query execution.
     *
     * @param durationNanos elapsed time in nanoseconds
     */
    public void recordQuery(long durationNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

        totalQueries.increment();
        totalLatencyNanos.add(durationNanos);

        // Update max latency using CAS loop
        long currentMax;
        do {
            currentMax = maxLatencyMs.get();
        } while (durationMs > currentMax && !maxLatencyMs.compareAndSet(currentMax, durationMs));

        if (durationMs >= slowQueryThresholdMs) {
            slowQueryCount.increment();
            if (slowQueryCounter != null) {
                slowQueryCounter.increment();
            }
            log.warn("Slow query detected: {}ms (threshold: {}ms)", durationMs, slowQueryThresholdMs);
        }

        if (queryTimer != null) {
            queryTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Records a transaction execution.
     *
     * @param durationNanos elapsed time in nanoseconds
     */
    public void recordTransaction(long durationNanos) {
        if (transactionTimer != null) {
            transactionTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Records a persistence error.
     */
    public void recordError() {
        errorCount.increment();
        if (errorCounter != null) {
            errorCounter.increment();
        }
    }

    /**
     * Executes a supplier while recording query metrics.
     *
     * @param operation the operation to execute
     * @param <T>       return type
     * @return the result of the operation
     */
    public <T> T recordQueryExecution(Supplier<T> operation) {
        long start = System.nanoTime();
        try {
            T result = operation.get();
            recordQuery(System.nanoTime() - start);
            return result;
        } catch (RuntimeException ex) {
            recordQuery(System.nanoTime() - start);
            recordError();
            throw ex;
        }
    }

    /**
     * Executes a runnable while recording query metrics.
     *
     * @param operation the operation to execute
     */
    public void recordQueryExecution(Runnable operation) {
        long start = System.nanoTime();
        try {
            operation.run();
            recordQuery(System.nanoTime() - start);
        } catch (RuntimeException ex) {
            recordQuery(System.nanoTime() - start);
            recordError();
            throw ex;
        }
    }

    /**
     * Returns a snapshot of current query statistics.
     *
     * @return immutable {@link QueryStats} record
     */
    public QueryStats getStats() {
        long total = totalQueries.sum();
        double avgMs = total == 0
                ? 0.0
                : TimeUnit.NANOSECONDS.toMillis(totalLatencyNanos.sum()) / (double) total;
        return new QueryStats(
                total,
                avgMs,
                maxLatencyMs.get(),
                slowQueryCount.sum(),
                errorCount.sum()
        );
    }

    /**
     * Resets all internal accumulators to zero.
     */
    public void reset() {
        totalQueries.reset();
        totalLatencyNanos.reset();
        maxLatencyMs.set(0);
        slowQueryCount.reset();
        errorCount.reset();
        log.debug("PersistenceMetricsCollector stats reset");
    }
}
