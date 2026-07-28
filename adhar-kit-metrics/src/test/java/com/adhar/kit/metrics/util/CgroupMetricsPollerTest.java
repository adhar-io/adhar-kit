package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link CgroupMetricsPoller}.
 */
class CgroupMetricsPollerTest {

    private final AdharMetricsProperties properties = new AdharMetricsProperties();

    private Path fixture(String name) {
        try {
            return Paths.get(getClass().getResource("/cgroup/" + name).toURI());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void start_withCgroupAvailable_registersGaugesAndReturnsTrue() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = new KubernetesMetricsUtils(registry, properties, fixture("v2"));
        CgroupMetricsPoller poller = new CgroupMetricsPoller(utils, 1);

        try {
            boolean started = poller.start();

            assertThat(started).isTrue();
            assertThat(registry.find(KubernetesMetricsUtils.CPU_LIMIT_METRIC).gauge()).isNotNull();
            assertThat(registry.find(KubernetesMetricsUtils.MEMORY_USAGE_METRIC).gauge().value())
                    .isEqualTo(134217728.0);
            // start() is idempotent
            assertThat(poller.start()).isTrue();
        } finally {
            poller.shutdown();
        }
    }

    @Test
    void start_withoutCgroup_returnsFalseAndDoesNotSchedule() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = new KubernetesMetricsUtils(registry, properties,
                Paths.get("/nonexistent/cgroup/root"));
        CgroupMetricsPoller poller = new CgroupMetricsPoller(utils, 5);

        assertThat(poller.start()).isFalse();
        assertThat(registry.find(KubernetesMetricsUtils.CPU_LIMIT_METRIC).gauge()).isNull();
    }

    @Test
    void intervalIsClampedToMinimum() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = new KubernetesMetricsUtils(registry, properties, fixture("v1"));
        CgroupMetricsPoller poller = new CgroupMetricsPoller(utils, 0);

        try {
            assertThat(poller.start()).isTrue();
        } finally {
            poller.shutdown();
        }
    }

    @Test
    void shutdownIsSafeWhenNeverStarted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = new KubernetesMetricsUtils(registry, properties, fixture("v1"));
        CgroupMetricsPoller poller = new CgroupMetricsPoller(utils, 1);

        assertDoesNotThrow(poller::shutdown);
    }
}
