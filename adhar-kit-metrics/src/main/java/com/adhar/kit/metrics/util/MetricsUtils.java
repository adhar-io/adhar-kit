package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Utility class for working with metrics.
 * <p>
 * This class provides convenience methods for creating and managing metrics,
 * making it easier for developers to use the metrics infrastructure.
 * </p>
 */
@Slf4j
public class MetricsUtils {

    private final MeterRegistry registry;
    private final AdharMetricsProperties properties;

    /**
     * Constructor for MetricsUtils.
     *
     * @param registry The meter registry
     * @param properties The metrics properties
     */
    public MetricsUtils(MeterRegistry registry, AdharMetricsProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    // ==================== Counter Methods ====================

    /**
     * Creates a counter with the given name and tags.
     *
     * @param name The counter name
     * @param tags The tags as key-value pairs
     * @return The counter
     */
    public Counter counter(String name, String... tags) {
        validateNameAndTags(name, tags);
        return Counter.builder(name)
                .tags(tags)
                .register(registry);
    }

    /**
     * Creates a counter with the given name, description, and tags.
     *
     * @param name The counter name
     * @param description The counter description
     * @param tags The tags as key-value pairs
     * @return The counter
     */
    public Counter counter(String name, String description, String... tags) {
        validateNameAndTags(name, tags);
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    /**
     * Increments a counter by name and tags.
     *
     * @param name The counter name
     * @param tags The tags as key-value pairs
     */
    public void incrementCounter(String name, String... tags) {
        counter(name, tags).increment();
    }

    /**
     * Increments a counter by a specific amount.
     *
     * @param name The counter name
     * @param amount The amount to increment
     * @param tags The tags as key-value pairs
     */
    public void incrementCounter(String name, double amount, String... tags) {
        counter(name, tags).increment(amount);
    }

    // ==================== Timer Methods ====================

    /**
     * Creates a timer with the given name and tags.
     *
     * @param name The timer name
     * @param tags The tags as key-value pairs
     * @return The timer
     */
    public Timer timer(String name, String... tags) {
        validateNameAndTags(name, tags);
        return Timer.builder(name)
                .tags(tags)
                .register(registry);
    }

    /**
     * Creates a timer with the given name, description, and tags.
     *
     * @param name The timer name
     * @param description The timer description
     * @param tags The tags as key-value pairs
     * @return The timer
     */
    public Timer timer(String name, String description, String... tags) {
        validateNameAndTags(name, tags);
        return Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    /**
     * Times the execution of a supplier and returns its result.
     *
     * @param name The timer name
     * @param supplier The supplier to time
     * @param tags Optional tags
     * @param <T> The return type
     * @return The supplier result
     */
    public <T> T timeSupplier(String name, Supplier<T> supplier, String... tags) {
        Timer.Sample sample = Timer.start(registry);
        try {
            T result = supplier.get();
            sample.stop(timer(name, tags));
            return result;
        } catch (Exception e) {
            sample.stop(timer(name + ".error", tags));
            throw e;
        }
    }

    /**
     * Times the execution of a runnable.
     *
     * @param name The timer name
     * @param runnable The runnable to time
     * @param tags The tags as key-value pairs
     */
    public void timeRunnable(String name, Runnable runnable, String... tags) {
        Timer.Sample sample = Timer.start(registry);
        try {
            runnable.run();
            sample.stop(timer(name, tags));
        } catch (Exception e) {
            sample.stop(timer(name + ".error", tags));
            throw e;
        }
    }

    /**
     * Records a timing measurement in milliseconds.
     *
     * @param name The timer name
     * @param duration The duration in milliseconds
     * @param tags The tags as key-value pairs
     */
    public void recordTimer(String name, long duration, String... tags) {
        timer(name, tags).record(duration, TimeUnit.MILLISECONDS);
    }

    // ==================== Gauge Methods ====================

    /**
     * Creates a gauge with the given name and value function.
     *
     * @param name The gauge name
     * @param obj The object to observe
     * @param valueFunction The function to extract the value
     * @param tags The tags as key-value pairs
     * @param <T> The object type
     * @return The gauge
     */
    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> valueFunction, String... tags) {
        validateNameAndTags(name, tags);
        return Gauge.builder(name, obj, valueFunction)
                .tags(tags)
                .register(registry);
    }

    /**
     * Creates a gauge with the given name, description, and value function.
     *
     * @param name The gauge name
     * @param description The gauge description
     * @param obj The object to observe
     * @param valueFunction The function to extract the value
     * @param tags The tags as key-value pairs
     * @param <T> The object type
     * @return The gauge
     */
    public <T> Gauge gauge(String name, String description, T obj, ToDoubleFunction<T> valueFunction, String... tags) {
        validateNameAndTags(name, tags);
        return Gauge.builder(name, obj, valueFunction)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    /**
     * Creates a simple number gauge.
     *
     * @param name The gauge name
     * @param number The number to observe
     * @param tags The tags as key-value pairs
     * @return The gauge
     */
    public Gauge numberGauge(String name, Number number, String... tags) {
        return gauge(name, number, Number::doubleValue, tags);
    }

    // ==================== Distribution Summary Methods ====================

    /**
     * Creates a distribution summary with the given name and tags.
     *
     * @param name The distribution summary name
     * @param tags The tags as key-value pairs
     * @return The distribution summary
     */
    public DistributionSummary summary(String name, String... tags) {
        validateNameAndTags(name, tags);
        return DistributionSummary.builder(name)
                .tags(tags)
                .register(registry);
    }

    /**
     * Creates a distribution summary with the given name, description, and tags.
     *
     * @param name The distribution summary name
     * @param description The distribution summary description
     * @param tags The tags as key-value pairs
     * @return The distribution summary
     */
    public DistributionSummary summary(String name, String description, String... tags) {
        validateNameAndTags(name, tags);
        return DistributionSummary.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    /**
     * Records a value in a distribution summary.
     *
     * @param name The distribution summary name
     * @param value The value to record
     * @param tags The tags as key-value pairs
     */
    public void recordSummary(String name, double value, String... tags) {
        summary(name, tags).record(value);
    }

    // ==================== Long Task Timer Methods ====================

    /**
     * Creates a long task timer with the given name and tags.
     *
     * @param name The long task timer name
     * @param tags The tags as key-value pairs
     * @return The long task timer
     */
    public LongTaskTimer longTaskTimer(String name, String... tags) {
        validateNameAndTags(name, tags);
        return LongTaskTimer.builder(name)
                .tags(tags)
                .register(registry);
    }

    // ==================== Utility Methods ====================

    /**
     * Gets the meter registry.
     *
     * @return The meter registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Gets the metrics properties.
     *
     * @return The metrics properties
     */
    public AdharMetricsProperties getProperties() {
        return properties;
    }

    /**
     * Checks if metrics are enabled.
     *
     * @return true if metrics are enabled, false otherwise
     */
    public boolean isMetricsEnabled() {
        return properties.isEnabled();
    }

    /**
     * Creates tags from a map.
     *
     * @param tagMap The tag map
     * @return The tags array
     */
    public String[] tagsFromMap(Map<String, String> tagMap) {
        if (tagMap == null || tagMap.isEmpty()) {
            return new String[0];
        }

        String[] tags = new String[tagMap.size() * 2];
        int index = 0;
        for (Map.Entry<String, String> entry : tagMap.entrySet()) {
            tags[index++] = entry.getKey();
            tags[index++] = entry.getValue();
        }
        return tags;
    }

    /**
     * Validates metric name and tags.
     *
     * @param name The metric name
     * @param tags The tags
     */
    private void validateNameAndTags(String name, String... tags) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }

        if (tags != null && tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided as key-value pairs (even number of elements)");
        }
        
        if (tags != null) {
            for (int i = 0; i < tags.length; i += 2) {
                if (!StringUtils.hasText(tags[i])) {
                    throw new IllegalArgumentException("Tag key at index " + i + " cannot be null or empty");
                }
            }
        }
    }

    /**
     * Normalizes a metric name by replacing invalid characters.
     *
     * @param name The original name
     * @return The normalized name
     */
    public String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }

        return name.replaceAll("[^a-zA-Z0-9._-]", "_")
                  .replaceAll("_{2,}", "_")
                  .replaceAll("^_|_$", "");
    }

    /**
     * Creates a metric name with a prefix based on application name.
     *
     * @param metricName The base metric name
     * @return The prefixed metric name
     */
    public String createMetricName(String metricName) {
        String applicationName = properties.getCommonTags().get("application");
        if (StringUtils.hasText(applicationName)) {
            return normalizeName(applicationName) + "." + normalizeName(metricName);
        }
        return normalizeName(metricName);
    }

    /**
     * Removes a meter from the registry.
     *
     * @param name The meter name
     * @param tags The tags
     * @return The removed meter, or null if not found
     */
    public Meter remove(String name, String... tags) {
        Meter.Id id = new Meter.Id(name, Tags.of(tags), null, null, Meter.Type.OTHER);
        return registry.remove(id);
    }

    /**
     * Clears all meters from the registry.
     */
    public void clear() {
        registry.clear();
    }
}