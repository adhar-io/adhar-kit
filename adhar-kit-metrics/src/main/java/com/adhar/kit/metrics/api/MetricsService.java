package com.adhar.kit.metrics.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;

/**
 * Framework-agnostic Metrics Service.
 * Works with Spring Boot, Quarkus, and Micronaut.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public interface MetricsService {

    /**
     * Create or get a counter.
     *
     * @param name counter name
     * @param tags tags
     * @return counter
     */
    Counter counter(String name, String... tags);

    /**
     * Create or get a timer.
     *
     * @param name timer name
     * @param tags tags
     * @return timer
     */
    Timer timer(String name, String... tags);

    /**
     * Create or get a gauge.
     *
     * @param name gauge name
     * @param supplier value supplier
     * @param tags tags
     * @param <T> value type
     * @return gauge value
     */
    <T extends Number> T gauge(String name, Supplier<T> supplier, String... tags);

    /**
     * Record a timed operation.
     *
     * @param name timer name
     * @param operation operation to time
     * @param <T> return type
     * @return operation result
     */
    <T> T recordTime(String name, Supplier<T> operation);

    /**
     * Increment a counter.
     *
     * @param name counter name
     * @param tags tags
     */
    void increment(String name, String... tags);

    /**
     * Increment a counter by amount.
     *
     * @param name counter name
     * @param amount amount to increment
     * @param tags tags
     */
    void increment(String name, double amount, String... tags);
}

