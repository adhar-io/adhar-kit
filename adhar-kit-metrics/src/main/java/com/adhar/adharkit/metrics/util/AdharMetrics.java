package com.adhar.adharkit.metrics.util;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * AdharMetrics provides a comprehensive, fluent API for manual metrics management.
 * <p>
 * This class serves as the primary interface for programmatic metrics creation and management,
 * offering enterprise-grade features like metric builders, batch operations, and performance monitoring.
 * </p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * {@code
 * // Simple metrics
 * AdharMetrics.counter("user.registrations").increment();
 * AdharMetrics.timer("api.response.time").record(Duration.ofMillis(150));
 * AdharMetrics.gauge("active.connections").value(connectionPool.getActiveCount());
 *
 * // Fluent API
 * AdharMetrics.builder()
 *     .counter("orders.created")
 *     .tag("region", "us-east-1")
 *     .tag("service", "order-service")
 *     .description("Number of orders created")
 *     .increment();
 *
 * // Batch operations
 * AdharMetrics.batch()
 *     .counter("batch.processed", 100)
 *     .timer("batch.duration", Duration.ofSeconds(30))
 *     .gauge("batch.size", 1000)
 *     .execute();
 *
 * // Performance monitoring
 * AdharMetrics.performance("critical.operation")
 *     .withThroughput()
 *     .withErrorRate()
 *     .withPercentiles(0.5, 0.95, 0.99)
 *     .monitor(() -> criticalBusinessLogic());
 * }
 * </pre>
 */
@Slf4j
public class AdharMetrics {

    private static MeterRegistry registry;
    private static AdharMetricsProperties properties;
    private static MetricsUtils metricsUtils;
    private static KubernetesMetricsUtils kubernetesMetricsUtils;

    // Cache for performance monitoring
    private static final Map<String, PerformanceMonitor> performanceMonitors = new ConcurrentHashMap<>();
    private static final Map<String, Timer.Sample> activeSamples = new ConcurrentHashMap<>();

    /**
     * Initializes AdharMetrics with the provided registry and properties.
     * This method is called automatically by the auto-configuration.
     */
    public static void initialize(MeterRegistry registry, AdharMetricsProperties properties,
                                 MetricsUtils metricsUtils, KubernetesMetricsUtils kubernetesMetricsUtils) {
        AdharMetrics.registry = registry;
        AdharMetrics.properties = properties;
        AdharMetrics.metricsUtils = metricsUtils;
        AdharMetrics.kubernetesMetricsUtils = kubernetesMetricsUtils;
        log.info("AdharMetrics initialized successfully");
    }

    // ==================== Quick Access Methods ====================

    /**
     * Creates or retrieves a counter with the given name.
     */
    public static Counter counter(String name) {
        return Counter.builder(name).register(registry);
    }

    /**
     * Creates or retrieves a counter with the given name and tags.
     */
    public static Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    /**
     * Creates or retrieves a timer with the given name.
     */
    public static Timer timer(String name) {
        return Timer.builder(name).register(registry);
    }

    /**
     * Creates or retrieves a timer with the given name and tags.
     */
    public static Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }

    /**
     * Creates a gauge builder for the given name.
     */
    public static GaugeBuilder gauge(String name) {
        return new GaugeBuilder(name);
    }

    /**
     * Creates or retrieves a distribution summary with the given name.
     */
    public static DistributionSummary summary(String name) {
        return DistributionSummary.builder(name).register(registry);
    }

    /**
     * Creates or retrieves a distribution summary with the given name and tags.
     */
    public static DistributionSummary summary(String name, String... tags) {
        return DistributionSummary.builder(name).tags(tags).register(registry);
    }

    // ==================== Fluent Builder API ====================

    /**
     * Creates a new metrics builder for fluent API usage.
     */
    public static MetricsBuilder builder() {
        return new MetricsBuilder();
    }

    /**
     * Creates a new batch metrics builder for batch operations.
     */
    public static BatchBuilder batch() {
        return new BatchBuilder();
    }

    /**
     * Creates a new performance monitor for comprehensive monitoring.
     */
    public static PerformanceBuilder performance(String name) {
        return new PerformanceBuilder(name);
    }

    // ==================== Convenience Methods ====================

    /**
     * Increments a counter by 1.
     */
    public static void increment(String counterName, String... tags) {
        counter(counterName, tags).increment();
    }

    /**
     * Increments a counter by the specified amount.
     */
    public static void increment(String counterName, double amount, String... tags) {
        counter(counterName, tags).increment(amount);
    }

    /**
     * Records a timer value in milliseconds.
     */
    public static void recordTimer(String timerName, long durationMs, String... tags) {
        timer(timerName, tags).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a timer value as Duration.
     */
    public static void recordTimer(String timerName, Duration duration, String... tags) {
        timer(timerName, tags).record(duration);
    }

    /**
     * Records a distribution summary value.
     */
    public static void recordSummary(String summaryName, double value, String... tags) {
        summary(summaryName, tags).record(value);
    }

    /**
     * Times the execution of a supplier and returns its result.
     */
    public static <T> T time(String timerName, Supplier<T> supplier, String... tags) {
        Timer.Sample sample = Timer.start(registry);
        try {
            T result = supplier.get();
            sample.stop(timer(timerName, tags));
            return result;
        } catch (Exception e) {
            sample.stop(timer(timerName + ".errors", tags));
            throw e;
        }
    }

    /**
     * Times the execution of a runnable.
     */
    public static void time(String timerName, Runnable runnable, String... tags) {
        Timer.Sample sample = Timer.start(registry);
        try {
            runnable.run();
            sample.stop(timer(timerName, tags));
        } catch (Exception e) {
            sample.stop(timer(timerName + ".errors", tags));
            throw e;
        }
    }

    // ==================== Advanced Metrics ====================

    /**
     * Creates a histogram timer with percentile buckets.
     */
    public static Timer histogram(String name, double[] buckets, String... tags) {
        return Timer.builder(name)
                .tags(tags)
                .register(registry);
    }

    /**
     * Starts a timer sample for manual timing control.
     */
    public static Timer.Sample startTimer(String sampleId) {
        Timer.Sample sample = Timer.start(registry);
        activeSamples.put(sampleId, sample);
        return sample;
    }

    /**
     * Stops a timer sample and records to the specified timer.
     */
    public static void stopTimer(String sampleId, String timerName, String... tags) {
        Timer.Sample sample = activeSamples.remove(sampleId);
        if (sample != null) {
            sample.stop(timer(timerName, tags));
        }
    }

    /**
     * Creates or updates a gauge with a number value.
     */
    public static void setGauge(String name, Number value, String... tags) {
        Gauge.builder(name, value, Number::doubleValue)
                .tags(tags)
                .register(registry);
    }

    /**
     * Records business metrics with context.
     */
    public static void recordBusiness(String event, String entity, double value, String... additionalTags) {
        List<String> allTags = new ArrayList<>();
        allTags.add("event");
        allTags.add(event);
        allTags.add("entity");
        allTags.add(entity);
        Collections.addAll(allTags, additionalTags);

        counter("business.events", allTags.toArray(new String[0])).increment();
        summary("business.values", allTags.toArray(new String[0])).record(value);
    }

    // ==================== Health and Performance Monitoring ====================

    /**
     * Records application health status.
     */
    public static void recordHealth(String component, boolean healthy, String... tags) {
        List<String> allTags = new ArrayList<>();
        allTags.add("component");
        allTags.add(component);
        allTags.add("status");
        allTags.add(healthy ? "healthy" : "unhealthy");
        Collections.addAll(allTags, tags);

        gauge(component + ".health").value(healthy ? 1.0 : 0.0, allTags.toArray(new String[0]));
    }

    /**
     * Records throughput metrics.
     */
    public static void recordThroughput(String operation, long count, Duration window) {
        double rate = count / window.getSeconds();
        gauge(operation + ".throughput")
                .tag("window", window.toString())
                .value(rate);
    }

    /**
     * Records error rate metrics.
     */
    public static void recordErrorRate(String operation, long errors, long total) {
        double errorRate = total > 0 ? (double) errors / total * 100.0 : 0.0;
        gauge(operation + ".error_rate")
                .tag("errors", String.valueOf(errors))
                .tag("total", String.valueOf(total))
                .value(errorRate);
    }

    // ==================== Utility Methods ====================

    /**
     * Gets the underlying MeterRegistry.
     */
    public static MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Gets the metrics properties.
     */
    public static AdharMetricsProperties getProperties() {
        return properties;
    }

    /**
     * Gets the Kubernetes metrics utils if available.
     */
    public static Optional<KubernetesMetricsUtils> getKubernetesMetrics() {
        return Optional.ofNullable(kubernetesMetricsUtils);
    }

    /**
     * Clears all cached metrics and samples.
     */
    public static void clearCache() {
        performanceMonitors.clear();
        activeSamples.clear();
        log.debug("AdharMetrics cache cleared");
    }

    /**
     * Gets current metrics statistics.
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMeters", registry.getMeters().size());
        stats.put("activeTimerSamples", activeSamples.size());
        stats.put("performanceMonitors", performanceMonitors.size());
        stats.put("registryClass", registry.getClass().getSimpleName());
        return stats;
    }

    // ==================== Builder Classes ====================

    /**
     * Fluent builder for creating metrics with various configurations.
     */
    public static class MetricsBuilder {
        private String name;
        private String description;
        private final List<String> tags = new ArrayList<>();
        private String baseUnit;

        public MetricsBuilder counter(String name) {
            this.name = name;
            return this;
        }

        public MetricsBuilder timer(String name) {
            this.name = name;
            return this;
        }

        public MetricsBuilder gauge(String name) {
            this.name = name;
            return this;
        }

        public MetricsBuilder summary(String name) {
            this.name = name;
            return this;
        }

        public MetricsBuilder description(String description) {
            this.description = description;
            return this;
        }

        public MetricsBuilder tag(String key, String value) {
            tags.add(key);
            tags.add(value);
            return this;
        }

        public MetricsBuilder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        public Counter buildCounter() {
            Counter.Builder builder = Counter.builder(name).tags(tags.toArray(new String[0]));
            if (description != null) builder.description(description);
            return builder.register(registry);
        }

        public Timer buildTimer() {
            Timer.Builder builder = Timer.builder(name).tags(tags.toArray(new String[0]));
            if (description != null) builder.description(description);
            return builder.register(registry);
        }

        public void increment() {
            buildCounter().increment();
        }

        public void increment(double amount) {
            buildCounter().increment(amount);
        }
    }

    /**
     * Builder for batch metric operations.
     */
    public static class BatchBuilder {
        private final List<Runnable> operations = new ArrayList<>();

        public BatchBuilder counter(String name, double value) {
            operations.add(() -> AdharMetrics.counter(name).increment(value));
            return this;
        }

        public BatchBuilder timer(String name, Duration duration) {
            operations.add(() -> AdharMetrics.timer(name).record(duration));
            return this;
        }

        public BatchBuilder gauge(String name, double value) {
            operations.add(() -> AdharMetrics.setGauge(name, value));
            return this;
        }

        public BatchBuilder summary(String name, double value) {
            operations.add(() -> AdharMetrics.summary(name).record(value));
            return this;
        }

        public void execute() {
            operations.forEach(Runnable::run);
        }
    }

    /**
     * Builder for gauge metrics with fluent API.
     */
    public static class GaugeBuilder {
        private final String name;
        private String description;
        private final List<String> tags = new ArrayList<>();
        private String baseUnit;

        public GaugeBuilder(String name) {
            this.name = name;
        }

        public GaugeBuilder description(String description) {
            this.description = description;
            return this;
        }

        public GaugeBuilder tag(String key, String value) {
            tags.add(key);
            tags.add(value);
            return this;
        }

        public GaugeBuilder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        public <T> Gauge register(T obj, ToDoubleFunction<T> valueFunction) {
            Gauge.Builder<T> builder = Gauge.builder(name, obj, valueFunction)
                    .tags(tags.toArray(new String[0]));
            if (description != null) builder.description(description);
            return builder.register(registry);
        }

        public void value(double value, String... additionalTags) {
            List<String> allTags = new ArrayList<>(tags);
            Collections.addAll(allTags, additionalTags);
            AtomicLong gaugeValue = new AtomicLong(Double.doubleToLongBits(value));

            Gauge.builder(name, gaugeValue, obj -> Double.longBitsToDouble(obj.get()))
                    .tags(allTags.toArray(new String[0]))
                    .register(registry);
        }
    }

    /**
     * Builder for performance monitoring with comprehensive metrics.
     */
    public static class PerformanceBuilder {
        private final String name;
        private boolean recordThroughput = false;
        private boolean recordErrorRate = false;
        private boolean recordPercentiles = false;
        private double[] percentiles = {0.5, 0.95, 0.99};

        public PerformanceBuilder(String name) {
            this.name = name;
        }

        public PerformanceBuilder withThroughput() {
            this.recordThroughput = true;
            return this;
        }

        public PerformanceBuilder withErrorRate() {
            this.recordErrorRate = true;
            return this;
        }

        public PerformanceBuilder withPercentiles(double... percentiles) {
            this.recordPercentiles = true;
            this.percentiles = percentiles;
            return this;
        }

        public <T> T monitor(Supplier<T> operation) {
            PerformanceMonitor monitor = performanceMonitors.computeIfAbsent(name,
                k -> new PerformanceMonitor(name, recordThroughput, recordErrorRate, recordPercentiles, percentiles));

            return monitor.execute(operation);
        }

        public void monitor(Runnable operation) {
            monitor(() -> {
                operation.run();
                return null;
            });
        }
    }

    /**
     * Internal performance monitor for tracking comprehensive metrics.
     */
    private static class PerformanceMonitor {
        private final String name;
        private final boolean recordThroughput;
        private final boolean recordErrorRate;
        private final Timer timer;
        private final Counter totalCounter;
        private final Counter errorCounter;
        private final AtomicLong lastThroughputCheck = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong requestsSinceLastCheck = new AtomicLong(0);

        public PerformanceMonitor(String name, boolean recordThroughput, boolean recordErrorRate,
                                boolean recordPercentiles, double[] percentiles) {
            this.name = name;
            this.recordThroughput = recordThroughput;
            this.recordErrorRate = recordErrorRate;

            Timer.Builder timerBuilder = Timer.builder(name + ".duration");
            if (recordPercentiles) {
                timerBuilder.publishPercentiles(percentiles);
            }
            this.timer = timerBuilder.register(registry);

            this.totalCounter = Counter.builder(name + ".total").register(registry);
            this.errorCounter = recordErrorRate ?
                Counter.builder(name + ".errors").register(registry) : null;
        }

        public <T> T execute(Supplier<T> operation) {
            Timer.Sample sample = Timer.start(registry);
            boolean success = false;

            try {
                T result = operation.get();
                success = true;
                return result;
            } catch (Exception e) {
                if (errorCounter != null) {
                    errorCounter.increment();
                }
                throw e;
            } finally {
                sample.stop(timer);
                totalCounter.increment();
                requestsSinceLastCheck.incrementAndGet();

                updateDerivedMetrics();
            }
        }

        private void updateDerivedMetrics() {
            if (recordThroughput) {
                long now = System.currentTimeMillis();
                long lastCheck = lastThroughputCheck.get();
                long timeDiff = now - lastCheck;

                if (timeDiff > 10000) { // Update every 10 seconds
                    if (lastThroughputCheck.compareAndSet(lastCheck, now)) {
                        long requests = requestsSinceLastCheck.getAndSet(0);
                        double throughput = (double) requests / (timeDiff / 1000.0);
                        AdharMetrics.setGauge(name + ".throughput", throughput);
                    }
                }
            }

            if (recordErrorRate && errorCounter != null) {
                double total = totalCounter.count();
                double errors = errorCounter.count();
                double errorRate = total > 0 ? (errors / total) * 100.0 : 0.0;
                AdharMetrics.setGauge(name + ".error_rate", errorRate);
            }
        }
    }
}
