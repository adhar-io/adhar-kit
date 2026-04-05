package com.adhar.kit.metrics.helidon;

import com.adhar.kit.metrics.api.MetricsService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Helidon-specific adapter for metrics collection using Micrometer.
 *
 * <p>This adapter provides a standalone metrics implementation for Helidon SE and MP
 * applications. It uses Micrometer's {@link MeterRegistry} for metrics collection,
 * which is natively supported by Helidon 4.x.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>Micrometer Integration:</b> Uses Micrometer for vendor-neutral metrics</li>
 *   <li><b>Pluggable Registry:</b> Accepts any MeterRegistry (Prometheus, JMX, etc.)</li>
 *   <li><b>Thread Safe:</b> Micrometer's MeterRegistry is inherently thread-safe</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * HelidonMetricsAdapter metrics = new HelidonMetricsAdapter(registry);
 *
 * // Register with Helidon WebServer
 * WebServer.builder()
 *     .routing(routing -> routing
 *         .get("/metrics", (req, res) -> res.send(registry.scrape()))
 *     )
 *     .build()
 *     .start();
 *
 * // Use metrics
 * metrics.increment("requests.total", "method", "GET");
 * }</pre>
 *
 * <p><b>Usage with Helidon MP:</b></p>
 * <pre>{@code
 * // In a CDI producer or startup observer
 * HelidonMetricsAdapter metrics = new HelidonMetricsAdapter(registry);
 * metrics.increment("orders.created", "region", "us-east-1");
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Micrometer's {@link MeterRegistry} handles
 * concurrent access internally, making this adapter safe for use across
 * Helidon's virtual-thread-based request handling.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see MetricsService
 * @see FrameworkAdapter
 * @see MeterRegistry
 */
public class HelidonMetricsAdapter implements FrameworkAdapter<MetricsService>, MetricsService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(HelidonMetricsAdapter.class);

    /**
     * Micrometer MeterRegistry used for all metrics operations.
     * <p>
     * In Helidon SE, this is typically provided by the application at construction time.
     * In Helidon MP, it can be sourced from the MicroProfile Metrics bridge or
     * created standalone.
     * </p>
     */
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a HelidonMetricsAdapter with the provided MeterRegistry.
     *
     * @param meterRegistry the Micrometer MeterRegistry to use for metrics registration
     */
    public HelidonMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.debug("Initialized HelidonMetricsAdapter with provided MeterRegistry");
    }

    /**
     * Constructs a HelidonMetricsAdapter with a default SimpleMeterRegistry.
     * <p>
     * This constructor is suitable for testing or when no specific registry
     * is required. For production use, prefer the constructor that accepts
     * a MeterRegistry (e.g., PrometheusMeterRegistry).
     * </p>
     */
    public HelidonMetricsAdapter() {
        this(new SimpleMeterRegistry());
        log.debug("Initialized HelidonMetricsAdapter with default SimpleMeterRegistry");
    }

    /**
     * Gets the framework supported by this adapter.
     *
     * @return {@link Framework#HELIDON}
     */
    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    /**
     * Gets the metrics service instance (this adapter itself).
     *
     * @return this adapter as a MetricsService
     */
    @Override
    public MetricsService getService() {
        return this;
    }

    /**
     * Creates or retrieves a Counter metric.
     *
     * @param name the counter name
     * @param tags optional tag key-value pairs
     * @return Counter instance registered with MeterRegistry
     */
    @Override
    public Counter counter(String name, String... tags) {
        log.trace("Creating counter: {} with tags: {}", name, tags);
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * Creates or retrieves a Timer metric.
     *
     * @param name the timer name
     * @param tags optional tag key-value pairs
     * @return Timer instance registered with MeterRegistry
     */
    @Override
    public Timer timer(String name, String... tags) {
        log.trace("Creating timer: {} with tags: {}", name, tags);
        return Timer.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    /**
     * Creates or updates a Gauge metric.
     *
     * @param name the gauge name
     * @param supplier function that returns the current value
     * @param tags optional tag key-value pairs
     * @param <T> the numeric type
     * @return the current value from the supplier
     */
    @Override
    public <T extends Number> T gauge(String name, Supplier<T> supplier, String... tags) {
        log.trace("Creating gauge: {} with tags: {}", name, tags);
        Gauge.builder(name, supplier, obj -> obj.get().doubleValue())
                .tags(tags)
                .register(meterRegistry);
        return supplier.get();
    }

    /**
     * Records the execution time of an operation.
     *
     * @param name the timer name
     * @param operation the operation to time
     * @param <T> the return type
     * @return the result of the operation
     * @throws RuntimeException if the operation throws an exception
     */
    @Override
    public <T> T recordTime(String name, Supplier<T> operation) {
        log.trace("Recording time for: {}", name);
        Timer timer = timer(name);
        try {
            return timer.recordCallable(operation::get);
        } catch (Exception e) {
            log.error("Error during timed operation: {}", name, e);
            throw new RuntimeException("Timed operation failed: " + name, e);
        }
    }

    /**
     * Increments a counter by 1.
     *
     * @param name the counter name
     * @param tags optional tag key-value pairs
     */
    @Override
    public void increment(String name, String... tags) {
        log.trace("Incrementing counter: {} with tags: {}", name, tags);
        counter(name, tags).increment();
    }

    /**
     * Increments a counter by a specific amount.
     *
     * @param name the counter name
     * @param amount the amount to increment by
     * @param tags optional tag key-value pairs
     */
    @Override
    public void increment(String name, double amount, String... tags) {
        log.trace("Incrementing counter: {} by {} with tags: {}", name, amount, tags);
        counter(name, tags).increment(amount);
    }
}
