package com.adhar.kit.metrics.quarkus;

import com.adhar.kit.metrics.api.MetricsService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Quarkus-specific adapter for metrics collection using Micrometer.
 *
 * <p>This adapter provides seamless integration between Adhar Kit metrics and Quarkus's
 * CDI and metrics infrastructure. It leverages Quarkus's built-in {@link MeterRegistry}
 * and automatically registers all metrics with Quarkus's metrics endpoints.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>CDI Integration:</b> ApplicationScoped bean managed by Quarkus Arc</li>
 *   <li><b>Prometheus Export:</b> Metrics available at /q/metrics endpoint</li>
 *   <li><b>Native Support:</b> Works in both JVM and native modes</li>
 *   <li><b>Zero Configuration:</b> Auto-discovered by Quarkus extensions</li>
 *   <li><b>Performance:</b> Optimized for Quarkus's fast startup</li>
 * </ul>
 *
 * <p><b>Dependencies (pom.xml):</b></p>
 * <pre>{@code
 * <dependency>
 *     <groupId>io.quarkus</groupId>
 *     <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p><b>Configuration (application.properties):</b></p>
 * <pre>{@code
 * # Enable Prometheus metrics
 * quarkus.micrometer.enabled=true
 * quarkus.micrometer.registry-enabled-default=false
 * quarkus.micrometer.export.prometheus.enabled=true
 * quarkus.micrometer.export.prometheus.path=/q/metrics
 *
 * # JVM metrics
 * quarkus.micrometer.binder.jvm=true
 * quarkus.micrometer.binder.system=true
 * }</pre>
 *
 * <p><b>Basic Usage in Quarkus:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class OrderService {
 *
 *     @Inject
 *     QuarkusMetricsAdapter metrics;
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
 * @ApplicationScoped
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
 * <p><b>With JAX-RS and @Timed:</b></p>
 * <pre>{@code
 * @Path("/orders")
 * @ApplicationScoped
 * public class OrderResource {
 *
 *     @Inject
 *     QuarkusMetricsAdapter metrics;
 *
 *     @POST
 *     @Timed(value = "api.orders.create", percentiles = {0.5, 0.95, 0.99})
 *     public Order createOrder(OrderRequest request) {
 *         metrics.increment("api.requests.total",
 *             "endpoint", "/orders",
 *             "method", "POST"
 *         );
 *         return orderService.createOrder(request);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Custom Gauges:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class ConnectionPoolMonitor {
 *
 *     @Inject
 *     QuarkusMetricsAdapter metrics;
 *
 *     @Inject
 *     AgroalDataSource dataSource;
 *
 *     void onStart(@Observes StartupEvent event) {
 *         metrics.gauge("db.connections.active",
 *             () -> dataSource.getConfiguration()
 *                 .connectionPoolConfiguration()
 *                 .maxSize() - dataSource.getConfiguration()
 *                 .connectionPoolConfiguration()
 *                 .minSize()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Integration with Quarkus Metrics:</b></p>
 * <p>This adapter works alongside Quarkus's built-in metrics:</p>
 * <ul>
 *   <li>HTTP request/response metrics (via quarkus-micrometer)</li>
 *   <li>JVM metrics (memory, GC, threads)</li>
 *   <li>Database metrics (Agroal connection pool)</li>
 *   <li>Custom application metrics (via this adapter)</li>
 * </ul>
 *
 * <p><b>Prometheus Export Example:</b></p>
 * <pre>{@code
 * # All metrics available at:
 * GET http://localhost:8080/q/metrics
 *
 * # Sample output:
 * # HELP orders_created_total
 * # TYPE orders_created_total counter
 * orders_created_total{region="us-east-1"} 1250.0
 *
 * # HELP api_request_duration_seconds
 * # TYPE api_request_duration_seconds summary
 * api_request_duration_seconds_sum 45.2
 * api_request_duration_seconds_count 1000.0
 * }</pre>
 *
 * <p><b>Native Image Support:</b></p>
 * <p>This adapter works in Quarkus native images:</p>
 * <pre>{@code
 * # Build native image
 * ./mvnw package -Pnative
 *
 * # Run native image
 * ./target/myapp-1.0.0-runner
 *
 * # Metrics still available at /q/metrics
 * }</pre>
 *
 * <p><b>Dev Mode:</b></p>
 * <p>In Quarkus dev mode, metrics are automatically available:</p>
 * <pre>{@code
 * # Start dev mode
 * ./mvnw quarkus:dev
 *
 * # Access metrics
 * curl http://localhost:8080/q/metrics
 *
 * # Dev UI with metrics
 * http://localhost:8080/q/dev
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Quarkus Arc manages the ApplicationScoped bean,
 * and Micrometer's {@link MeterRegistry} is inherently thread-safe.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see MetricsService
 * @see FrameworkAdapter
 * @see MeterRegistry
 */
@ApplicationScoped
public class QuarkusMetricsAdapter implements FrameworkAdapter<MetricsService>, MetricsService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(QuarkusMetricsAdapter.class);

    /**
     * Quarkus's MeterRegistry instance.
     * <p>
     * This is injected by Quarkus CDI and is shared across the application.
     * All metrics registered via this adapter are added to this registry and
     * automatically exposed via Quarkus's /q/metrics endpoint.
     * </p>
     */
    @Inject
    MeterRegistry meterRegistry;

    /**
     * No-arg constructor required by CDI.
     * <p>
     * This constructor is called by Quarkus Arc. The MeterRegistry is injected
     * after construction via field injection.
     * </p>
     */
    public QuarkusMetricsAdapter() {
        log.debug("Constructing QuarkusMetricsAdapter (MeterRegistry injected post-construction)");
    }

    /**
     * Gets the framework supported by this adapter.
     *
     * @return {@link Framework#QUARKUS}
     */
    @Override
    public Framework getSupportedFramework() {
        return Framework.QUARKUS;
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
     * Counters are registered with Quarkus's MeterRegistry and automatically
     * exposed via /q/metrics endpoint in Prometheus format.
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
     * Timers are registered with Quarkus's MeterRegistry and track both
     * count and total time of events. Exposed via /q/metrics endpoint.
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
     * Gauges are sampled periodically by Quarkus and exported to monitoring
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
     * the execution time. The timer is registered with Quarkus's MeterRegistry.
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
     * The counter is registered with Quarkus's MeterRegistry.
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
     * The counter is registered with Quarkus's MeterRegistry.
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

