package com.adhar.kit.metrics.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler-driven poller that periodically reads the container's cgroup CPU/memory
 * limits and usage (via {@link KubernetesMetricsUtils#collectContainerResourceMetrics()})
 * and refreshes the corresponding gauges.
 * <p>
 * The poller works without any Kubernetes API access -- it only reads the cgroup
 * filesystem. When no cgroup filesystem is present (e.g. running outside a Linux
 * container), it auto-detects the absence and does not schedule any work, so the bean
 * is harmless on developer machines.
 * </p>
 */
public class CgroupMetricsPoller {

    private static final Logger log = LoggerFactory.getLogger(CgroupMetricsPoller.class);

    private final KubernetesMetricsUtils metricsUtils;
    private final long intervalSeconds;
    private ScheduledExecutorService scheduler;

    /**
     * Creates the poller. Scheduling only starts if a cgroup filesystem is detected.
     *
     * @param metricsUtils the utility that reads cgroup files and registers gauges
     * @param intervalSeconds polling interval in seconds (clamped to a minimum of 1)
     */
    public CgroupMetricsPoller(KubernetesMetricsUtils metricsUtils, long intervalSeconds) {
        this.metricsUtils = metricsUtils;
        this.intervalSeconds = Math.max(1L, intervalSeconds);
    }

    /**
     * Starts the scheduled collection when a cgroup filesystem is available. Safe to call
     * once; subsequent calls are ignored.
     *
     * @return {@code true} if polling was started, {@code false} when no cgroup fs was found
     */
    public synchronized boolean start() {
        if (scheduler != null) {
            return true;
        }
        if (!metricsUtils.isCgroupAvailable()) {
            log.info("No cgroup filesystem detected -- container resource polling disabled");
            return false;
        }

        // Prime an initial sample so gauges exist immediately.
        collectSafely();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "adhar-cgroup-metrics-poller");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::collectSafely, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("CgroupMetricsPoller started (cgroup v{}), collecting every {}s",
                metricsUtils.isCgroupV2() ? "2" : "1", intervalSeconds);
        return true;
    }

    /**
     * Stops the scheduled collection. Called during application shutdown.
     */
    public synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("CgroupMetricsPoller stopped");
        }
    }

    private void collectSafely() {
        try {
            metricsUtils.collectContainerResourceMetrics();
        } catch (Exception e) {
            log.warn("Error collecting container resource metrics: {}", e.getMessage());
        }
    }
}
