package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Heap memory health indicator.
 *
 * <p>Reports DOWN when heap usage (used / max) reaches the configured threshold.
 * When the JVM reports no maximum heap size, committed memory is used as the
 * denominator.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * MemoryHealthIndicator indicator = new MemoryHealthIndicator(0.9);
 * registry.register(indicator, false, HealthRegistry.LIVENESS_GROUP);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class MemoryHealthIndicator implements AdharHealthIndicator {

    /** Default heap usage ratio at which memory is considered unhealthy. */
    public static final double DEFAULT_THRESHOLD = 0.9;

    private final MemoryMXBean memoryMXBean;
    private final double threshold;

    /**
     * Creates an indicator with the default threshold.
     */
    public MemoryHealthIndicator() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * Creates an indicator.
     *
     * @param threshold heap usage ratio [0..1] at which memory is DOWN
     */
    public MemoryHealthIndicator(double threshold) {
        this(ManagementFactory.getMemoryMXBean(), threshold);
    }

    /**
     * Creates an indicator from configuration properties.
     *
     * @param config memory health configuration
     */
    public MemoryHealthIndicator(AdharHealthProperties.MemoryConfig config) {
        this(config.getThreshold());
    }

    /**
     * Creates an indicator with an explicit memory MX bean (useful for testing).
     *
     * @param memoryMXBean memory MX bean to query
     * @param threshold    heap usage ratio [0..1] at which memory is DOWN
     */
    public MemoryHealthIndicator(MemoryMXBean memoryMXBean, double threshold) {
        this.memoryMXBean = memoryMXBean;
        this.threshold = threshold;
    }

    @Override
    public Health check() {
        try {
            MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
            long used = heap.getUsed();
            long max = heap.getMax() > 0 ? heap.getMax() : heap.getCommitted();
            double usage = max > 0 ? (double) used / max : 0.0;

            Health.HealthBuilder builder;
            if (usage >= threshold) {
                builder = Health.down()
                        .error(String.format("Heap usage %.2f%% above threshold %.2f%%",
                                usage * 100, threshold * 100));
            } else {
                builder = Health.up();
            }

            return builder
                    .component(getName())
                    .withDetail("heapUsed", used)
                    .withDetail("heapMax", max)
                    .withDetail("heapCommitted", heap.getCommitted())
                    .withDetail("heapUsage", String.format("%.2f", usage))
                    .withDetail("threshold", threshold)
                    .build();
        } catch (Exception e) {
            log.error("Memory health check failed", e);
            return Health.down(e)
                    .component(getName())
                    .build();
        }
    }

    @Override
    public String getName() {
        return "memory";
    }
}
