package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool health indicator.
 *
 * <p>Wraps an {@link ExecutorService} and reports DOWN when the work queue is
 * saturated (queue usage at or above the configured threshold), and
 * OUT_OF_SERVICE when the executor has been shut down.</p>
 *
 * <p>Queue usage is only measurable for {@link ThreadPoolExecutor} instances with a
 * bounded queue; other executors report UNKNOWN.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ThreadPoolHealthIndicator indicator =
 *     new ThreadPoolHealthIndicator("worker-pool", workerPool, 0.9);
 * registry.register(indicator, HealthRegistry.LIVENESS_GROUP);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ThreadPoolHealthIndicator implements AdharHealthIndicator {

    /** Default queue usage ratio at which the pool is considered saturated. */
    public static final double DEFAULT_QUEUE_USAGE_THRESHOLD = 0.9;

    private final String name;
    private final ExecutorService executor;
    private final double queueUsageThreshold;

    /**
     * Creates an indicator named {@code threadPool} with the default threshold.
     *
     * @param executor executor to monitor
     */
    public ThreadPoolHealthIndicator(ExecutorService executor) {
        this("threadPool", executor, DEFAULT_QUEUE_USAGE_THRESHOLD);
    }

    /**
     * Creates an indicator.
     *
     * @param name                indicator name (allows monitoring multiple pools)
     * @param executor            executor to monitor
     * @param queueUsageThreshold queue usage ratio [0..1] at which the pool is DOWN
     */
    public ThreadPoolHealthIndicator(String name, ExecutorService executor, double queueUsageThreshold) {
        this.name = name;
        this.executor = executor;
        this.queueUsageThreshold = queueUsageThreshold;
    }

    /**
     * Creates an indicator from configuration properties.
     *
     * @param executor executor to monitor
     * @param config   thread pool health configuration
     */
    public ThreadPoolHealthIndicator(ExecutorService executor, AdharHealthProperties.ThreadPoolConfig config) {
        this("threadPool", executor, config.getQueueUsageThreshold());
    }

    @Override
    public Health check() {
        try {
            if (executor.isShutdown()) {
                return Health.outOfService()
                        .component(name)
                        .withDetail("message", "Executor has been shut down")
                        .build();
            }

            if (!(executor instanceof ThreadPoolExecutor pool)) {
                return Health.unknown()
                        .component(name)
                        .withDetail("message", "Unsupported executor type: " + executor.getClass().getName())
                        .build();
            }

            int queueSize = pool.getQueue().size();
            int remainingCapacity = pool.getQueue().remainingCapacity();
            long capacity = (long) queueSize + remainingCapacity;
            double usage = capacity > 0 ? (double) queueSize / capacity : 0.0;

            Health.HealthBuilder builder;
            if (capacity > 0 && usage >= queueUsageThreshold) {
                builder = Health.down()
                        .error("Thread pool queue saturated: " + queueSize + "/" + capacity);
            } else {
                builder = Health.up();
            }

            return builder
                    .component(name)
                    .withDetail("poolSize", pool.getPoolSize())
                    .withDetail("activeThreads", pool.getActiveCount())
                    .withDetail("corePoolSize", pool.getCorePoolSize())
                    .withDetail("maximumPoolSize", pool.getMaximumPoolSize())
                    .withDetail("queueSize", queueSize)
                    .withDetail("queueRemainingCapacity", remainingCapacity)
                    .withDetail("queueUsage", String.format("%.2f", usage))
                    .withDetail("queueUsageThreshold", queueUsageThreshold)
                    .build();
        } catch (Exception e) {
            log.error("Thread pool health check failed for {}", name, e);
            return Health.down(e)
                    .component(name)
                    .build();
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
