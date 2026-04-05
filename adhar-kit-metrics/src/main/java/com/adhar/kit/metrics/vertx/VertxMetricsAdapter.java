package com.adhar.kit.metrics.vertx;

import com.adhar.kit.metrics.api.MetricsService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Vert.x adapter for metrics collection using Micrometer.
 *
 * <p>This adapter provides metrics integration for Vert.x applications using
 * Micrometer's {@link MeterRegistry}. Since Vert.x does not have a built-in
 * dependency injection container, this adapter uses constructor-based initialization
 * where the {@link MeterRegistry} is provided explicitly by the application.</p>
 *
 * <p>Vert.x natively supports Micrometer via the {@code vertx-micrometer-metrics}
 * module, which can supply a {@link MeterRegistry} through
 * {@code MicrometerMetricsOptions}. This adapter works with any Micrometer registry
 * including Prometheus, JMX, and others.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization via constructor</li>
 *   <li><b>Micrometer Native:</b> Uses the same Micrometer API as Vert.x metrics</li>
 *   <li><b>Prometheus Support:</b> Compatible with PrometheusMeterRegistry for /metrics endpoint</li>
 *   <li><b>Thread-Safe:</b> Safe for use across Vert.x event loop and worker threads</li>
 *   <li><b>Non-Blocking:</b> All metric operations are non-blocking</li>
 * </ul>
 *
 * <p><b>Dependencies (pom.xml):</b></p>
 * <pre>{@code
 * <dependency>
 *     <groupId>io.vertx</groupId>
 *     <artifactId>vertx-micrometer-metrics</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>io.micrometer</groupId>
 *     <artifactId>micrometer-registry-prometheus</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create with Vert.x's BackendRegistries
 * MeterRegistry registry = BackendRegistries.getDefaultNow();
 * VertxMetricsAdapter metrics = new VertxMetricsAdapter(registry);
 *
 * // Use in a Verticle
 * public class OrderVerticle extends AbstractVerticle {
 *     private final VertxMetricsAdapter metrics;
 *
 *     public OrderVerticle(MeterRegistry registry) {
 *         this.metrics = new VertxMetricsAdapter(registry);
 *     }
 *
 *     public void start() {
 *         vertx.eventBus().consumer("orders.create", msg -> {
 *             metrics.increment("orders.created", "type", "async");
 *             // process order...
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Vert.x MicrometerMetricsOptions:</b></p>
 * <pre>{@code
 * VertxOptions options = new VertxOptions().setMetricsOptions(
 *     new MicrometerMetricsOptions()
 *         .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
 *         .setEnabled(true)
 * );
 * Vertx vertx = Vertx.vertx(options);
 *
 * // Retrieve the registry after Vert.x startup
 * MeterRegistry registry = BackendRegistries.getDefaultNow();
 * VertxMetricsAdapter metrics = new VertxMetricsAdapter(registry);
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Micrometer's {@link MeterRegistry} is inherently
 * thread-safe and can be used from any Vert.x event loop or worker thread without
 * additional synchronization.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see MetricsService
 * @see FrameworkAdapter
 * @see MeterRegistry
 */
public class VertxMetricsAdapter implements FrameworkAdapter<MetricsService>, MetricsService {

    private static final Logger log = LoggerFactory.getLogger(VertxMetricsAdapter.class);

    private final MeterRegistry meterRegistry;

    /**
     * Constructs a new Vert.x metrics adapter with the given MeterRegistry.
     *
     * <p>The MeterRegistry can be obtained from Vert.x's {@code BackendRegistries.getDefaultNow()}
     * after configuring {@code MicrometerMetricsOptions}, or it can be any Micrometer-compatible
     * registry such as {@code PrometheusMeterRegistry} or {@code SimpleMeterRegistry}.</p>
     *
     * @param meterRegistry the Micrometer MeterRegistry to use for metric registration
     * @throws NullPointerException if meterRegistry is null
     */
    public VertxMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
        log.info("Initialized Vert.x Metrics adapter with registry: {}", meterRegistry.getClass().getSimpleName());
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
    }

    @Override
    public MetricsService getService() {
        return this;
    }

    @Override
    public Counter counter(String name, String... tags) {
        log.trace("Creating counter: {} with tags: {}", name, tags);
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    @Override
    public Timer timer(String name, String... tags) {
        log.trace("Creating timer: {} with tags: {}", name, tags);
        return Timer.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    @Override
    public <T extends Number> T gauge(String name, Supplier<T> supplier, String... tags) {
        log.trace("Creating gauge: {} with tags: {}", name, tags);
        Gauge.builder(name, supplier, obj -> obj.get().doubleValue())
                .tags(tags)
                .register(meterRegistry);
        return supplier.get();
    }

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

    @Override
    public void increment(String name, String... tags) {
        log.trace("Incrementing counter: {} with tags: {}", name, tags);
        counter(name, tags).increment();
    }

    @Override
    public void increment(String name, double amount, String... tags) {
        log.trace("Incrementing counter: {} by {} with tags: {}", name, amount, tags);
        counter(name, tags).increment(amount);
    }
}
