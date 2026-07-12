package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural unit tests for the static {@link AdharMetrics} fluent API.
 */
class AdharMetricsTest {

    private SimpleMeterRegistry registry;
    private AdharMetricsProperties properties;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new AdharMetricsProperties();
        MetricsUtils utils = new MetricsUtils(registry, properties);
        AdharMetrics.initialize(registry, properties, utils, null);
        AdharMetrics.clearCache();
    }

    @Test
    void quickAccessCountersAndTimers() {
        AdharMetrics.counter("c1").increment();
        AdharMetrics.counter("c2", "k", "v").increment(2.0);
        AdharMetrics.timer("t1").record(Duration.ofMillis(5));
        AdharMetrics.timer("t2", "k", "v").record(Duration.ofMillis(5));

        assertThat(registry.counter("c1").count()).isEqualTo(1.0);
        assertThat(registry.counter("c2", "k", "v").count()).isEqualTo(2.0);
        assertThat(registry.timer("t1").count()).isEqualTo(1L);
    }

    @Test
    void summariesRecord() {
        AdharMetrics.summary("s1").record(1.0);
        AdharMetrics.summary("s2", "k", "v").record(2.0);
        assertThat(registry.summary("s1").count()).isEqualTo(1L);
        assertThat(registry.summary("s2", "k", "v").count()).isEqualTo(1L);
    }

    @Test
    void convenienceIncrementAndRecord() {
        AdharMetrics.increment("inc");
        AdharMetrics.increment("inc", 4.0);
        AdharMetrics.recordTimer("rt", 10L, "k", "v");
        AdharMetrics.recordTimer("rt2", Duration.ofMillis(3));
        AdharMetrics.recordSummary("rs", 5.0, "k", "v");

        assertThat(registry.counter("inc").count()).isEqualTo(5.0);
        assertThat(registry.timer("rt", "k", "v").count()).isEqualTo(1L);
        assertThat(registry.summary("rs", "k", "v").count()).isEqualTo(1L);
    }

    @Test
    void timeSupplierAndRunnable() {
        String r = AdharMetrics.time("ts", () -> "ok", "k", "v");
        assertThat(r).isEqualTo("ok");

        AdharMetrics.time("tr", () -> { /* no-op */ }, "k", "v");
        assertThat(registry.timer("ts", "k", "v").count()).isEqualTo(1L);
        assertThat(registry.timer("tr", "k", "v").count()).isEqualTo(1L);
    }

    @Test
    void timeSupplierRecordsErrorTimer() {
        assertThatThrownBy(() -> AdharMetrics.time("te", () -> {
            throw new RuntimeException("x");
        })).isInstanceOf(RuntimeException.class);
        assertThat(registry.timer("te.errors").count()).isEqualTo(1L);
    }

    @Test
    void timeRunnableRecordsErrorTimer() {
        assertThatThrownBy(() -> AdharMetrics.time("tre", () -> {
            throw new RuntimeException("x");
        })).isInstanceOf(RuntimeException.class);
        assertThat(registry.timer("tre.errors").count()).isEqualTo(1L);
    }

    @Test
    void histogramAndManualTimerSamples() {
        AdharMetrics.histogram("h", new double[]{0.5}, "k", "v").record(Duration.ofMillis(1));

        AdharMetrics.startTimer("sample-1");
        AdharMetrics.stopTimer("sample-1", "manual.timer", "k", "v");
        assertThat(registry.timer("manual.timer", "k", "v").count()).isEqualTo(1L);

        // stopping an unknown sample is a no-op
        AdharMetrics.stopTimer("missing", "noop.timer");
        assertThat(registry.find("noop.timer").timer()).isNull();
    }

    @Test
    void setGaugeAndBusinessAndHealth() {
        AdharMetrics.setGauge("g", 99, "k", "v");
        assertThat(registry.get("g").tags("k", "v").gauge().value()).isEqualTo(99.0);

        AdharMetrics.recordBusiness("created", "order", 12.0, "region", "us");
        assertThat(registry.find("business.events").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("business.values").summary().count()).isEqualTo(1L);

        AdharMetrics.recordHealth("db", true, "node", "1");
        assertThat(registry.find("db.health").gauge().value()).isEqualTo(1.0);
        AdharMetrics.recordHealth("cache", false);
        assertThat(registry.find("cache.health").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void recordThroughputAndErrorRate() {
        AdharMetrics.recordThroughput("op", 100, Duration.ofSeconds(10));
        assertThat(registry.find("op.throughput").gauge().value()).isEqualTo(10.0);

        AdharMetrics.recordErrorRate("op2", 5, 100);
        assertThat(registry.find("op2.error_rate").gauge().value()).isEqualTo(5.0);

        AdharMetrics.recordErrorRate("op3", 1, 0); // total 0 -> 0.0
        assertThat(registry.find("op3.error_rate").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void gettersAndStatistics() {
        assertThat(AdharMetrics.getRegistry()).isSameAs(registry);
        assertThat(AdharMetrics.getProperties()).isSameAs(properties);
        assertThat(AdharMetrics.getKubernetesMetrics()).isEmpty();

        AdharMetrics.counter("x").increment();
        Map<String, Object> stats = AdharMetrics.getStatistics();
        assertThat(stats).containsKeys("totalMeters", "activeTimerSamples",
                "performanceMonitors", "registryClass");
        assertThat((int) stats.get("totalMeters")).isGreaterThan(0);
    }

    @Test
    void fluentMetricsBuilderCounterAndTimer() {
        AdharMetrics.builder()
                .counter("orders.created")
                .description("orders")
                .tag("region", "us")
                .baseUnit("orders")
                .increment();
        AdharMetrics.builder().counter("orders.created").tag("region", "us").increment(3.0);

        assertThat(registry.counter("orders.created", "region", "us").count()).isEqualTo(4.0);

        var timer = AdharMetrics.builder().timer("build.timer").description("t").buildTimer();
        timer.record(Duration.ofMillis(1));
        assertThat(timer.count()).isEqualTo(1L);

        var counter = AdharMetrics.builder().counter("build.counter").buildCounter();
        counter.increment();
        assertThat(counter.count()).isEqualTo(1.0);

        // gauge() and summary() name setters return the same builder
        assertThat(AdharMetrics.builder().gauge("g")).isNotNull();
        assertThat(AdharMetrics.builder().summary("s")).isNotNull();
    }

    @Test
    void batchBuilderExecutesAllOperations() {
        AdharMetrics.batch()
                .counter("batch.count", 10)
                .timer("batch.timer", Duration.ofSeconds(1))
                .gauge("batch.gauge", 5)
                .summary("batch.summary", 7)
                .execute();

        assertThat(registry.counter("batch.count").count()).isEqualTo(10.0);
        assertThat(registry.timer("batch.timer").count()).isEqualTo(1L);
        assertThat(registry.find("batch.gauge").gauge().value()).isEqualTo(5.0);
        assertThat(registry.summary("batch.summary").count()).isEqualTo(1L);
    }

    @Test
    void gaugeBuilderValueAndRegister() {
        AdharMetrics.gauge("gb.value")
                .description("d")
                .tag("k", "v")
                .baseUnit("u")
                .value(33.0, "extra", "tag");
        assertThat(registry.get("gb.value").tags("k", "v", "extra", "tag").gauge().value())
                .isEqualTo(33.0);

        AtomicInteger holder = new AtomicInteger(8);
        Gauge gauge = AdharMetrics.gauge("gb.register")
                .description("d")
                .tag("k", "v")
                .register(holder, AtomicInteger::get);
        assertThat(gauge.value()).isEqualTo(8.0);
    }

    @Test
    void performanceBuilderMonitorsSupplierWithAllOptions() {
        String result = AdharMetrics.performance("critical.op")
                .withThroughput()
                .withErrorRate()
                .withPercentiles(0.5, 0.95)
                .monitor(() -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(registry.find("critical.op.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.find("critical.op.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void performanceBuilderMonitorsRunnableAndRecordsErrors() {
        AdharMetrics.performance("runnable.op").monitor(() -> { /* no-op */ });
        assertThat(registry.find("runnable.op.total").counter().count()).isEqualTo(1.0);

        assertThatThrownBy(() -> AdharMetrics.performance("err.op")
                .withErrorRate()
                .monitor((Runnable) () -> {
                    throw new RuntimeException("boom");
                })).isInstanceOf(RuntimeException.class);
        assertThat(registry.find("err.op.errors").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("err.op.error_rate").gauge().value()).isEqualTo(100.0);
    }

    @Test
    void clearCacheResetsMonitors() {
        AdharMetrics.performance("p").monitor(() -> "x");
        AdharMetrics.startTimer("s");
        AdharMetrics.clearCache();
        Map<String, Object> stats = AdharMetrics.getStatistics();
        assertThat(stats.get("performanceMonitors")).isEqualTo(0);
        assertThat(stats.get("activeTimerSamples")).isEqualTo(0);
    }

    @Test
    void tagsAreApplied() {
        AdharMetrics.counter("tagged", "a", "1", "b", "2").increment();
        var counter = registry.find("tagged").counter();
        assertThat(counter.getId().getTags()).containsAll(Tags.of("a", "1", "b", "2"));
    }
}
