package com.adhar.kit.metrics.micronaut;

import com.adhar.kit.metrics.api.MetricsService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Micronaut-specific adapter for metrics collection using Micrometer.
 *
 * <p>This adapter provides seamless integration between Adhar Kit metrics and Micronaut's
 * dependency injection and metrics infrastructure. It leverages Micronaut's built-in
 * {@link MeterRegistry} and automatically registers all metrics with the registry.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Automatic Registration:</b> Metrics automatically registered with Micronaut's MeterRegistry</li>
 *   <li><b>Dependency Injection:</b> MeterRegistry injected via Micronaut DI</li>
 *   <li><b>Singleton Scope:</b> Single adapter instance shared across application</li>
 *   <li><b>Conditional Loading:</b> Only loaded when Micronaut is detected</li>
 *   <li><b>Micrometer Integration:</b> Full Micrometer API support</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * # application.yml
 * micronaut:
 *   metrics:
 *     enabled: true
 *     export:
 *       prometheus:
 *         enabled: true
 *         step: PT1M
 * }</pre>
 *
 * <p><b>Basic Usage in Micronaut:</b></p>
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *
 *     @Inject
 *     private MicronautMetricsAdapter metrics;
 *
 *     public Order createOrder(OrderRequest request) {
 *         metrics.increment("orders.created");
 *         return processOrder(request);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Using MetricsFacade (Recommended):</b></p>
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *
 *     private final MetricsFacade metrics = MetricsFacade.getInstance();
 *
 *     public Order createOrder(OrderRequest request) {
 *         return metrics.recordTime("order.creation", () -> {
 *             Order order = processOrder(request);
 *             metrics.increment("orders.created", "region", order.getRegion());
 *             return order;
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Advanced Metrics with Tags:</b></p>
 * <pre>{@code
 * @Singleton
 * public class ApiController {
 *
 *     @Inject
 *     private MicronautMetricsAdapter metrics;
 *
 *     @Get("/orders/{id}")
 *     public Order getOrder(String id) {
 *         return metrics.recordTime("api.request.duration", () -> {
 *             metrics.increment("api.requests.total",
 *                 "endpoint", "/orders",
 *                 "method", "GET",
 *                 "status", "200"
 *             );
 *             return orderService.findById(id);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Custom Gauges:</b></p>
 * <pre>{@code
 * @Singleton
 * public class ConnectionPoolMonitor {
 *
 *     @Inject
 *     private MicronautMetricsAdapter metrics;
 *
 *     @Inject
 *     private DataSource dataSource;
 *
 *     @PostConstruct
 *     public void registerGauges() {
 *         metrics.gauge("db.connections.active",
 *             () -> dataSource.getActiveConnectionCount()
 *         );
 *
 *         metrics.gauge("db.connections.idle",
 *             () -> dataSource.getIdleConnectionCount()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Integration with Micronaut Metrics:</b></p>
 * <p>This adapter works alongside Micronaut's built-in metrics:</p>
 * <ul>
 *   <li>HTTP request/response metrics</li>
 *   <li>JVM metrics (memory, GC, threads)</li>
 *   <li>Database connection pool metrics</li>
 *   <li>Custom application metrics (via this adapter)</li>
 * </ul>
 *
 * <p><b>Prometheus Export Example:</b></p>
 * <pre>{@code
 * # All metrics (built-in + custom) available at:
 * GET http://localhost:8080/metrics
 *
 * # Sample output:
 * orders_created_total{region="us-east-1"} 1250
 * api_request_duration_seconds_sum 45.2
 * api_request_duration_seconds_count 1000
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Micronaut's {@link MeterRegistry} is thread-safe,
 * and all operations delegate to it.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see MetricsService
 * @see FrameworkAdapter
 * @see MeterRegistry
 */
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautMetricsAdapter implements FrameworkAdapter<MetricsService>, MetricsService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(MicronautMetricsAdapter.class);

    /**
     * Micronaut's MeterRegistry instance.
     * <p>
     * This is injected by Micronaut DI and is shared across the application.
     * All metrics registered via this adapter are added to this registry.
     * </p>
     */
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a new MicronautMetricsAdapter with the provided MeterRegistry.
     * <p>
     * This constructor is called by Micronaut's DI container. The MeterRegistry
     * is automatically injected by Micronaut.
     * </p>
     *
     * @param meterRegistry the Micronaut MeterRegistry instance
     * @throws IllegalArgumentException if meterRegistry is null
     */
    public MicronautMetricsAdapter(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry cannot be null");
        }
        this.meterRegistry = meterRegistry;
        log.info("Initialized MicronautMetricsAdapter with MeterRegistry: {}",
                 meterRegistry.getClass().getSimpleName());
    }

    /**
     * Gets the framework supported by this adapter.
     *
     * @return {@link Framework#MICRONAUT}
     */
    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
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
     * <p>
     * Counters are registered with Micronaut's MeterRegistry and can be
     * viewed via the /metrics endpoint or exported to Prometheus.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Counter counter = metrics.counter("orders.created", "region", "us-east-1");
     * counter.increment();
     * }</pre>
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
     * <p>
     * Timers are registered with Micronaut's MeterRegistry and track both
     * count and total time of events.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Timer timer = metrics.timer("api.response.time", "endpoint", "/orders");
     * timer.record(Duration.ofMillis(150));
     * }</pre>
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
     * <p>
     * Gauges are sampled periodically by Micronaut and exported to monitoring
     * systems. The supplier is called each time the gauge value is needed.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * metrics.gauge("jvm.memory.used",
     *     () -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
     * );
     * }</pre>
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
     * <p>
     * This method creates a timer, executes the operation, and records
     * the execution time. The timer is registered with Micronaut's MeterRegistry.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<Order> orders = metrics.recordTime("db.query.orders", () -> {
     *     return orderRepository.findAll();
     * });
     * }</pre>
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
     * <p>
     * Creates the counter if it doesn't exist and increments it.
     * The counter is registered with Micronaut's MeterRegistry.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * metrics.increment("orders.created", "region", "us-east-1");
     * }</pre>
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
     * <p>
     * Creates the counter if it doesn't exist and increments it by the specified amount.
     * The counter is registered with Micronaut's MeterRegistry.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * metrics.increment("records.processed", 100);
     * metrics.increment("revenue.total", 1250.50, "currency", "USD");
     * }</pre>
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

