package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.util.KubernetesMetricsUtils.CgroupStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for the cgroup v1 / v2 parsing and container-resource gauge registration added to
 * {@link KubernetesMetricsUtils}. Exercised against fixture directories under
 * {@code src/test/resources/cgroup} that emulate both cgroup file layouts (no Docker/K8s).
 */
class KubernetesCgroupMetricsTest {

    private final AdharMetricsProperties properties = new AdharMetricsProperties();

    private Path fixture(String name) {
        try {
            return Paths.get(getClass().getResource("/cgroup/" + name).toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Missing fixture: " + name, e);
        }
    }

    private KubernetesMetricsUtils utils(SimpleMeterRegistry registry, String fixture) {
        return new KubernetesMetricsUtils(registry, properties, fixture(fixture));
    }

    // ==================== Detection ====================

    @Test
    void detectsCgroupV2ByControllersFile() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        assertThat(utils(registry, "v2").isCgroupV2()).isTrue();
        assertThat(utils(registry, "v1").isCgroupV2()).isFalse();
    }

    @Test
    void isCgroupAvailable_reflectsDirectoryPresence() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        assertThat(utils(registry, "v2").isCgroupAvailable()).isTrue();

        KubernetesMetricsUtils missing = new KubernetesMetricsUtils(registry, properties,
                Paths.get("/nonexistent/cgroup/root"));
        assertThat(missing.isCgroupAvailable()).isFalse();
    }

    // ==================== cgroup v2 parsing ====================

    @Test
    void readsCgroupV2Stats() {
        CgroupStats stats = utils(new SimpleMeterRegistry(), "v2").readCgroupStats();

        assertThat(stats.v2()).isTrue();
        assertThat(stats.cpuLimitCores()).isCloseTo(1.5, within(1e-9));
        assertThat(stats.cpuUsageSeconds()).isCloseTo(12.5, within(1e-9));
        assertThat(stats.memoryLimitBytes()).isCloseTo(536870912.0, within(1e-9));
        assertThat(stats.memoryUsageBytes()).isCloseTo(134217728.0, within(1e-9));
    }

    @Test
    void readsCgroupV2UnlimitedStats() {
        CgroupStats stats = utils(new SimpleMeterRegistry(), "v2-unlimited").readCgroupStats();

        assertThat(stats.v2()).isTrue();
        assertThat(stats.cpuLimitCores()).isNaN();
        assertThat(stats.memoryLimitBytes()).isNaN();
        assertThat(stats.cpuUsageSeconds()).isCloseTo(5.0, within(1e-9));
        assertThat(stats.memoryUsageBytes()).isCloseTo(67108864.0, within(1e-9));
    }

    // ==================== cgroup v1 parsing ====================

    @Test
    void readsCgroupV1Stats() {
        CgroupStats stats = utils(new SimpleMeterRegistry(), "v1").readCgroupStats();

        assertThat(stats.v2()).isFalse();
        assertThat(stats.cpuLimitCores()).isCloseTo(2.0, within(1e-9));
        assertThat(stats.cpuUsageSeconds()).isCloseTo(7.5, within(1e-9));
        assertThat(stats.memoryLimitBytes()).isCloseTo(1073741824.0, within(1e-9));
        assertThat(stats.memoryUsageBytes()).isCloseTo(268435456.0, within(1e-9));
    }

    @Test
    void readsCgroupV1UnlimitedStats() {
        CgroupStats stats = utils(new SimpleMeterRegistry(), "v1-unlimited").readCgroupStats();

        assertThat(stats.cpuLimitCores()).isNaN();
        assertThat(stats.memoryLimitBytes()).isNaN();
        assertThat(stats.cpuUsageSeconds()).isCloseTo(3.0, within(1e-9));
        assertThat(stats.memoryUsageBytes()).isCloseTo(134217728.0, within(1e-9));
    }

    // ==================== Gauge registration ====================

    @Test
    void collectRegistersContainerGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = utils(registry, "v2");

        utils.collectContainerResourceMetrics();

        assertThat(registry.find(KubernetesMetricsUtils.CPU_LIMIT_METRIC).gauge().value())
                .isCloseTo(1.5, within(1e-9));
        assertThat(registry.find(KubernetesMetricsUtils.MEMORY_LIMIT_METRIC).gauge().value())
                .isCloseTo(536870912.0, within(1e-9));
        assertThat(registry.find(KubernetesMetricsUtils.MEMORY_USAGE_METRIC).gauge().value())
                .isCloseTo(134217728.0, within(1e-9));
        // usage-cores gauge exists; first sample has no previous point so value is NaN.
        assertThat(registry.find(KubernetesMetricsUtils.CPU_USAGE_METRIC).gauge()).isNotNull();
    }

    @Test
    void secondCollectComputesCpuUsageCores() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = utils(registry, "v2");

        utils.collectContainerResourceMetrics(); // primes previous sample
        Thread.sleep(20);
        utils.collectContainerResourceMetrics(); // same cumulative usage -> 0 cores delta

        double cpuUsage = registry.find(KubernetesMetricsUtils.CPU_USAGE_METRIC).gauge().value();
        assertThat(cpuUsage).isNotNaN();
        assertThat(cpuUsage).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void collectIsIdempotentForGaugeRegistration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = utils(registry, "v1");

        utils.collectContainerResourceMetrics();
        utils.collectContainerResourceMetrics();

        long cpuLimitGauges = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(KubernetesMetricsUtils.CPU_LIMIT_METRIC))
                .count();
        assertThat(cpuLimitGauges).isEqualTo(1);
    }

    // ==================== Static parser edge cases ====================

    @Test
    void parseCpuMaxV2_handlesVariants() {
        assertThat(KubernetesMetricsUtils.parseCpuMaxV2("150000 100000")).isCloseTo(1.5, within(1e-9));
        assertThat(KubernetesMetricsUtils.parseCpuMaxV2("max 100000")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuMaxV2(null)).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuMaxV2("garbage")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuMaxV2("100000 0")).isNaN();
    }

    @Test
    void parseCpuQuotaV1_handlesVariants() {
        assertThat(KubernetesMetricsUtils.parseCpuQuotaV1("200000", "100000")).isCloseTo(2.0, within(1e-9));
        assertThat(KubernetesMetricsUtils.parseCpuQuotaV1("-1", "100000")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuQuotaV1(null, "100000")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuQuotaV1("200000", "0")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuQuotaV1("x", "y")).isNaN();
    }

    @Test
    void parseCpuUsageV2_extractsUsageUsec() {
        assertThat(KubernetesMetricsUtils.parseCpuUsageV2("nr_periods 0\nusage_usec 2500000\nuser_usec 1"))
                .isCloseTo(2.5, within(1e-9));
        assertThat(KubernetesMetricsUtils.parseCpuUsageV2("no usage here")).isNaN();
        assertThat(KubernetesMetricsUtils.parseCpuUsageV2(null)).isNaN();
    }

    @Test
    void parseMemoryLimit_treatsSentinelsAsUnlimited() {
        assertThat(KubernetesMetricsUtils.parseMemoryLimit("1073741824")).isCloseTo(1073741824.0, within(1e-9));
        assertThat(KubernetesMetricsUtils.parseMemoryLimit("max")).isNaN();
        assertThat(KubernetesMetricsUtils.parseMemoryLimit("9223372036854771712")).isNaN();
        assertThat(KubernetesMetricsUtils.parseMemoryLimit(null)).isNaN();
        assertThat(KubernetesMetricsUtils.parseMemoryLimit("-1")).isNaN();
    }

    @Test
    void parseBytes_handlesBlankAndInvalid() {
        assertThat(KubernetesMetricsUtils.parseBytes("42")).isCloseTo(42.0, within(1e-9));
        assertThat(KubernetesMetricsUtils.parseBytes("  ")).isNaN();
        assertThat(KubernetesMetricsUtils.parseBytes(null)).isNaN();
        assertThat(KubernetesMetricsUtils.parseBytes("nope")).isNaN();
    }

    @Test
    void readCgroupStats_onMissingFilesYieldsNaN() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KubernetesMetricsUtils utils = new KubernetesMetricsUtils(registry, properties,
                Paths.get("/nonexistent/cgroup"));

        CgroupStats stats = utils.readCgroupStats();
        assertThat(stats.cpuLimitCores()).isNaN();
        assertThat(stats.cpuUsageSeconds()).isNaN();
        assertThat(stats.memoryLimitBytes()).isNaN();
        assertThat(stats.memoryUsageBytes()).isNaN();
    }
}
