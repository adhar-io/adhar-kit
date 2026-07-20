package com.adhar.adharkit.logging.performance;

/**
 * Auto-closing timer for one operation execution, created by
 * {@link PerformanceLogger#start(String)}.
 *
 * <pre>{@code
 * try (PerformanceTimer timer = performanceLogger.start("db.orders.query")) {
 *     return repository.findAll();
 * } catch (Exception e) {
 *     // mark before rethrowing, or use timer.failure() inside the try block
 * }
 * }</pre>
 */
public class PerformanceTimer implements AutoCloseable {

    private final PerformanceLogger logger;
    private final String operation;
    private final long startNanos = System.nanoTime();
    private boolean success = true;
    private boolean closed;

    PerformanceTimer(PerformanceLogger logger, String operation) {
        this.logger = logger;
        this.operation = operation;
    }

    /**
     * Marks this execution as failed.
     *
     * @return this timer
     */
    public PerformanceTimer failure() {
        this.success = false;
        return this;
    }

    /**
     * The elapsed time so far in milliseconds.
     *
     * @return elapsed milliseconds
     */
    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Stops the timer and records the measurement (idempotent).
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        logger.record(operation, elapsedMs(), success);
    }
}
