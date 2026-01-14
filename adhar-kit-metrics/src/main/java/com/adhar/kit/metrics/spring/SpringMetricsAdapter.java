package com.adhar.kit.metrics.spring;

import com.adhar.kit.metrics.api.MetricsService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Spring Boot-specific adapter for metrics collection using Micrometer.
 *
 * <p>This adapter provides seamless integration between Adhar Kit metrics and Spring Boot's
 * auto-configuration and metrics infrastructure. It leverages Spring Boot's built-in
 * {@link MeterRegistry} and automatically registers all metrics with Spring's actuator endpoints.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Auto-Configuration:</b> Automatically configured when Spring Boot is detected</li>
 *   <li><b>Actuator Integration:</b> Metrics available via /actuator/metrics endpoint</li>
 *   <li><b>Dependency Injection:</b> MeterRegistry autowired by Spring</li>
 *   <li><b>Prometheus Export:</b> Ready for Prometheus scraping via /actuator/prometheus</li>
 *   <li><b>Spring Service:</b> Registered as Spring @Service bean</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * # application.yml
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,metrics,prometheus
 *   metrics:
 *     enable:
 *       jvm: true
 *       process: true
 *       system: true
 *     export:
 *       prometheus:
 *         enabled: true
 * }</pre>
 *
 * <p><b>Basic Usage in Spring Boot:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @Autowired
 *     private SpringMetricsAdapter metrics;
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
 * @Service
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
 * <p><b>With @Timed Annotation:</b></p>
 * <pre>{@code
 * @RestController
 * public class OrderController {
 *
 *     @Autowired
 *     private SpringMetricsAdapter metrics;
 *
 *     @PostMapping("/orders")
 *     @Timed(value = "api.orders.create", percentiles = {0.5, 0.95, 0.99})
 *     public Order createOrder(@RequestBody OrderRequest request) {
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
 * @Component
 * public class ConnectionPoolMonitor {
 *
 *     @Autowired
 *     private SpringMetricsAdapter metrics;
 *
 *     @Autowired
 *     private DataSource dataSource;
 *
 *     @PostConstruct
 *     public void registerGauges() {
 *         metrics.gauge("db.connections.active",
 *             () -> ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()
 *         );
 *
 *         metrics.gauge("db.connections.idle",
 *             () -> ((HikariDataSource) dataSource).getHikariPoolMXBean().getIdleConnections()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Integration with Spring Boot Actuator:</b></p>
 * <p>All metrics registered via this adapter are automatically available through:</p>
 * <ul>
 *   <li><b>/actuator/metrics</b> - List all available metrics</li>
 *   <li><b>/actuator/metrics/{name}</b> - View specific metric details</li>
 *   <li><b>/actuator/prometheus</b> - Prometheus-formatted metrics</li>
 *   <li><b>/actuator/health</b> - Health checks (can include metrics)</li>
 * </ul>
 *
 * <p><b>Prometheus Export Example:</b></p>
 * <pre>{@code
 * # All metrics available at:
 * GET http://localhost:8080/actuator/prometheus
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
 * <p><b>Grafana Dashboard Integration:</b></p>
 * <pre>{@code
 * // Query examples for Grafana:
 *
 * // Request rate
 * rate(orders_created_total[5m])
 *
 * // Average response time
 * rate(api_request_duration_seconds_sum[5m]) / rate(api_request_duration_seconds_count[5m])
 *
 * // P95 latency
 * histogram_quantile(0.95, rate(api_request_duration_seconds_bucket[5m]))
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Spring Boot's {@link MeterRegistry} is thread-safe,
 * and all operations delegate to it. Spring manages the singleton lifecycle.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see MetricsService
 * @see FrameworkAdapter
 * @see MeterRegistry
 */
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
public class SpringMetricsAdapter implements FrameworkAdapter<MetricsService>, MetricsService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(SpringMetricsAdapter.class);

    /**
     * Spring Boot's MeterRegistry instance.
     * <p>
     * This is autowired by Spring Boot and is shared across the application.
     * All metrics registered via this adapter are added to this registry and
     * automatically exposed via Spring Boot Actuator endpoints.
     * </p>
     */
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a new SpringMetricsAdapter with the provided MeterRegistry.
     * <p>
     * This constructor is called by Spring's dependency injection. The MeterRegistry
     * is automatically autowired by Spring Boot's auto-configuration.
     * </p>
     *
     * @param meterRegistry the Spring Boot MeterRegistry instance
     * @throws IllegalArgumentException if meterRegistry is null
     */
    public SpringMetricsAdapter(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry cannot be null");
        }
        this.meterRegistry = meterRegistry;
        log.info("Initialized SpringMetricsAdapter with MeterRegistry: {}",
                 meterRegistry.getClass().getSimpleName());
    }

    /**
     * Gets the framework supported by this adapter.
     *
     * @return {@link Framework#SPRING_BOOT}
     */
    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
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
     * Counters are registered with Spring Boot's MeterRegistry and automatically
     * exposed via /actuator/metrics and /actuator/prometheus endpoints.
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
     * Timers are registered with Spring Boot's MeterRegistry and track both
     * count and total time of events. Exposed via actuator endpoints.
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
     * Gauges are sampled periodically by Spring Boot Actuator and exported to
     * monitoring systems. The supplier is called each time the gauge value is needed.
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
     * the execution time. The timer is registered with Spring Boot's MeterRegistry.
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
     * The counter is registered with Spring Boot's MeterRegistry.
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
     * The counter is registered with Spring Boot's MeterRegistry.
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

