package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural unit tests for {@link MetricsUtils} using an in-memory SimpleMeterRegistry.
 */
class MetricsUtilsTest {

    private SimpleMeterRegistry registry;
    private AdharMetricsProperties properties;
    private MetricsUtils utils;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new AdharMetricsProperties();
        utils = new MetricsUtils(registry, properties);
    }

    @Test
    void counterIsRegisteredAndIncremented() {
        utils.counter("orders.created", new String[]{"region", "us"}).increment();
        utils.incrementCounter("orders.created", "region", "us");
        utils.incrementCounter("orders.created", 3.0, "region", "us");

        assertThat(registry.counter("orders.created", "region", "us").count()).isEqualTo(5.0);
    }

    @Test
    void counterWithDescription() {
        var counter = utils.counter("c.desc", "a description", new String[]{"k", "v"});
        counter.increment();
        assertThat(counter.getId().getDescription()).isEqualTo("a description");
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void timerRecordsAndDescriptionApplied() {
        Timer timer = utils.timer("api.time", "the timer", new String[]{"endpoint", "/a"});
        timer.record(10, TimeUnit.MILLISECONDS);
        utils.recordTimer("api.time", 5L, "endpoint", "/a");

        assertThat(timer.getId().getDescription()).isEqualTo("the timer");
        assertThat(registry.timer("api.time", "endpoint", "/a").count()).isEqualTo(2L);
    }

    @Test
    void timeSupplierReturnsResultAndRecords() {
        String result = utils.timeSupplier("sup.timer", () -> "value", "k", "v");
        assertThat(result).isEqualTo("value");
        assertThat(registry.timer("sup.timer", "k", "v").count()).isEqualTo(1L);
    }

    @Test
    void timeSupplierRecordsErrorTimerOnException() {
        assertThatThrownBy(() -> utils.timeSupplier("fail.timer", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(registry.timer("fail.timer.error").count()).isEqualTo(1L);
    }

    @Test
    void timeRunnableRecordsSuccessAndError() {
        utils.timeRunnable("run.timer", () -> { /* no-op */ });
        assertThat(registry.timer("run.timer").count()).isEqualTo(1L);

        assertThatThrownBy(() -> utils.timeRunnable("run.fail", () -> {
            throw new RuntimeException("x");
        })).isInstanceOf(RuntimeException.class);
        assertThat(registry.timer("run.fail.error").count()).isEqualTo(1L);
    }

    @Test
    void gaugeRegistersAndReadsValue() {
        AtomicInteger holder = new AtomicInteger(42);
        Gauge gauge = utils.gauge("active", holder, AtomicInteger::get, "pool", "main");
        assertThat(gauge.value()).isEqualTo(42.0);

        Gauge described = utils.gauge("active2", "active gauge", holder, AtomicInteger::get);
        assertThat(described.getId().getDescription()).isEqualTo("active gauge");

        Gauge number = utils.numberGauge("num", 7, "k", "v");
        assertThat(number.value()).isEqualTo(7.0);
    }

    @Test
    void summaryRecordsValues() {
        DistributionSummary summary = utils.summary("payload", new String[]{"k", "v"});
        summary.record(100);
        utils.recordSummary("payload", 200, "k", "v");

        DistributionSummary described = utils.summary("payload2", "desc", new String[]{"k", "v"});
        assertThat(described.getId().getDescription()).isEqualTo("desc");
        assertThat(registry.summary("payload", "k", "v").count()).isEqualTo(2L);
    }

    @Test
    void longTaskTimerRegistered() {
        LongTaskTimer ltt = utils.longTaskTimer("long.task", "k", "v");
        assertThat(ltt).isNotNull();
        assertThat(ltt.activeTasks()).isZero();
    }

    @Test
    void gettersAndEnabledFlag() {
        assertThat(utils.getRegistry()).isSameAs(registry);
        assertThat(utils.getProperties()).isSameAs(properties);
        properties.setEnabled(true);
        assertThat(utils.isMetricsEnabled()).isTrue();
    }

    @Test
    void tagsFromMapHandlesNullEmptyAndPopulated() {
        assertThat(utils.tagsFromMap(null)).isEmpty();
        assertThat(utils.tagsFromMap(Map.of())).isEmpty();

        Map<String, String> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("b", "2");
        assertThat(utils.tagsFromMap(map)).containsExactly("a", "1", "b", "2");
    }

    @Test
    void validationRejectsBadNameAndTags() {
        assertThatThrownBy(() -> utils.counter(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric name");
        assertThatThrownBy(() -> utils.counter("n", new String[]{"onlyKey"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-value pairs");
        assertThatThrownBy(() -> utils.counter("n", new String[]{"", "value"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag key");
    }

    @Test
    void normalizeNameCleansInvalidCharacters() {
        assertThat(utils.normalizeName("my metric@name!!")).isEqualTo("my_metric_name");
        assertThat(utils.normalizeName("")).isEmpty();
        assertThat(utils.normalizeName(null)).isNull();
    }

    @Test
    void createMetricNamePrefixesWithApplication() {
        assertThat(utils.createMetricName("orders")).isEqualTo("orders");
        properties.getCommonTags().put("application", "my-app");
        assertThat(utils.createMetricName("orders.created")).isEqualTo("my-app.orders.created");
    }

    @Test
    void removeAndClear() {
        utils.counter("to.remove", new String[]{"k", "v"}).increment();
        Meter removed = utils.remove("to.remove", "k", "v");
        assertThat(removed).isNotNull();

        utils.counter("a").increment();
        utils.counter("b").increment();
        utils.clear();
        assertThat(registry.getMeters()).isEmpty();
    }
}
