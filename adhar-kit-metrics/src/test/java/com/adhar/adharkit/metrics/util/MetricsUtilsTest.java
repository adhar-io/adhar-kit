package com.adhar.adharkit.metrics.util;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link MetricsUtils}.
 */
@ExtendWith(MockitoExtension.class)
class MetricsUtilsTest {

    private MeterRegistry registry;
    private AdharMetricsProperties properties;
    private MetricsUtils metricsUtils;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new AdharMetricsProperties();
        properties.setEnabled(true);
        properties.getCommonTags().put("application", "test-app");

        metricsUtils = new MetricsUtils(registry, properties);
    }

    // ==================== Counter Tests ====================

    @Test
    void counter_WithValidName_CreatesCounter() {
        String metricName = "test.counter";
        Counter counter = metricsUtils.counter(metricName);

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo(metricName);
    }

    @Test
    void counter_WithNameAndDescription_CreatesCounterWithDescription() {
        String metricName = "test.counter";
        String description = "Test counter description";

        Counter counter = metricsUtils.counter(metricName, description);

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getName()).isEqualTo(metricName);
        assertThat(counter.getId().getDescription()).isEqualTo(description);
    }

    @Test
    void counter_WithTags_CreatesCounterWithTags() {
        String metricName = "test.counter";
        Counter counter = metricsUtils.counter(metricName, "key1", "value1", "key2", "value2");

        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTags()).containsExactlyInAnyOrder(
                Tag.of("key1", "value1"),
                Tag.of("key2", "value2")
        );
    }

    @Test
    void counter_WithNullName_ThrowsException() {
        assertThatThrownBy(() -> metricsUtils.counter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric name cannot be null or empty");
    }

    @Test
    void counter_WithOddNumberOfTags_ThrowsException() {
        assertThatThrownBy(() -> metricsUtils.counter("test.counter", "key1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tags must be provided as key-value pairs (even number of elements)");
    }

    @Test
    void incrementCounter_WithValidName_IncrementsCounter() {
        String metricName = "test.counter";
        metricsUtils.incrementCounter(metricName);

        Counter counter = registry.get(metricName).counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementCounter_WithAmount_IncrementsCounterByAmount() {
        String metricName = "test.counter";
        double amount = 5.5;

        metricsUtils.incrementCounter(metricName, amount);

        Counter counter = registry.get(metricName).counter();
        assertThat(counter.count()).isEqualTo(amount);
    }

    // ==================== Timer Tests ====================

    @Test
    void timer_WithValidName_CreatesTimer() {
        String metricName = "test.timer";
        Timer timer = metricsUtils.timer(metricName);

        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo(metricName);
    }

    @Test
    void timer_WithNameAndDescription_CreatesTimerWithDescription() {
        String metricName = "test.timer";
        String description = "Test timer description";

        Timer timer = metricsUtils.timer(metricName, description);

        assertThat(timer).isNotNull();
        assertThat(timer.getId().getDescription()).isEqualTo(description);
    }

    @Test
    void timeSupplier_WithValidSupplier_ReturnsResultAndRecordsTiming() {
        String metricName = "test.timer";
        String expectedResult = "test-result";

        Supplier<String> supplier = () -> {
            try {
                Thread.sleep(10); // Small delay for timing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return expectedResult;
        };

        String result = metricsUtils.timeSupplier(metricName, supplier);

        assertThat(result).isEqualTo(expectedResult);
        Timer timer = registry.get(metricName).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    void timeRunnable_WithValidRunnable_RecordsTiming() {
        String metricName = "test.timer";

        Runnable runnable = () -> {
            try {
                Thread.sleep(10); // Small delay for timing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        metricsUtils.timeRunnable(metricName, runnable);

        Timer timer = registry.get(metricName).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    void recordTimer_WithDuration_RecordsTimingInMilliseconds() {
        String metricName = "test.timer";
        long duration = 100L; // 100ms

        metricsUtils.recordTimer(metricName, duration);

        Timer timer = registry.get(metricName).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(duration);
    }

    // ==================== Gauge Tests ====================

    @Test
    void gauge_WithValidParameters_CreatesGauge() {
        String metricName = "test.gauge";
        String testObject = "test";

        Gauge gauge = metricsUtils.gauge(metricName, testObject, String::length);

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getName()).isEqualTo(metricName);
        assertThat(gauge.value()).isEqualTo(4.0); // "test".length()
    }

    @Test
    void gauge_WithDescription_CreatesGaugeWithDescription() {
        String metricName = "test.gauge";
        String description = "Test gauge description";
        String testObject = "test";

        Gauge gauge = metricsUtils.gauge(metricName, description, testObject, String::length);

        assertThat(gauge).isNotNull();
        assertThat(gauge.getId().getDescription()).isEqualTo(description);
    }

    @Test
    void numberGauge_WithNumber_CreatesGaugeWithNumberValue() {
        String metricName = "test.gauge";
        Number number = 42.5;

        Gauge gauge = metricsUtils.numberGauge(metricName, number);

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.5);
    }

    // ==================== Distribution Summary Tests ====================

    @Test
    void summary_WithValidName_CreatesSummary() {
        String metricName = "test.summary";
        DistributionSummary summary = metricsUtils.summary(metricName);

        assertThat(summary).isNotNull();
        assertThat(summary.getId().getName()).isEqualTo(metricName);
    }

    @Test
    void summary_WithDescription_CreatesSummaryWithDescription() {
        String metricName = "test.summary";
        String description = "Test summary description";

        DistributionSummary summary = metricsUtils.summary(metricName, description);

        assertThat(summary).isNotNull();
        assertThat(summary.getId().getDescription()).isEqualTo(description);
    }

    @Test
    void recordSummary_WithValue_RecordsValueInSummary() {
        String metricName = "test.summary";
        double value = 25.5;

        metricsUtils.recordSummary(metricName, value);

        DistributionSummary summary = registry.get(metricName).summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(value);
    }

    // ==================== Long Task Timer Tests ====================

    @Test
    void longTaskTimer_WithValidName_CreatesLongTaskTimer() {
        String metricName = "test.longtask";
        LongTaskTimer longTaskTimer = metricsUtils.longTaskTimer(metricName);

        assertThat(longTaskTimer).isNotNull();
        assertThat(longTaskTimer.getId().getName()).isEqualTo(metricName);
    }

    // ==================== Utility Methods Tests ====================

    @Test
    void getRegistry_ReturnsConfiguredRegistry() {
        MeterRegistry result = metricsUtils.getRegistry();

        assertThat(result).isSameAs(registry);
    }

    @Test
    void getProperties_ReturnsConfiguredProperties() {
        AdharMetricsProperties result = metricsUtils.getProperties();

        assertThat(result).isSameAs(properties);
    }

    @Test
    void isMetricsEnabled_ReturnsConfigurationValue() {
        boolean result = metricsUtils.isMetricsEnabled();

        assertThat(result).isTrue();

        properties.setEnabled(false);
        result = metricsUtils.isMetricsEnabled();

        assertThat(result).isFalse();
    }

    @Test
    void tagsFromMap_WithValidMap_ReturnsTagsArray() {
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("key1", "value1");
        tagMap.put("key2", "value2");

        String[] result = metricsUtils.tagsFromMap(tagMap);

        assertThat(result).hasLength(4);
        assertThat(result).containsExactlyInAnyOrder("key1", "value1", "key2", "value2");
    }

    @Test
    void tagsFromMap_WithEmptyMap_ReturnsEmptyArray() {
        Map<String, String> tagMap = new HashMap<>();

        String[] result = metricsUtils.tagsFromMap(tagMap);

        assertThat(result).isEmpty();
    }

    @Test
    void tagsFromMap_WithNullMap_ReturnsEmptyArray() {
        String[] result = metricsUtils.tagsFromMap(null);

        assertThat(result).isEmpty();
    }

    @Test
    void normalizeName_WithValidName_NormalizesCorrectly() {
        String result = metricsUtils.normalizeName("test-name_with.special@chars");

        assertThat(result).isEqualTo("test-name_with.special_chars");
    }

    @Test
    void normalizeName_WithMultipleUnderscores_CollapsesToSingle() {
        String result = metricsUtils.normalizeName("test___multiple____underscores");

        assertThat(result).isEqualTo("test_multiple_underscores");
    }

    @Test
    void normalizeName_WithLeadingTrailingUnderscores_RemovesThem() {
        String result = metricsUtils.normalizeName("_test_name_");

        assertThat(result).isEqualTo("test_name");
    }

    @Test
    void createMetricName_WithApplicationName_PrefixesWithAppName() {
        String result = metricsUtils.createMetricName("test.metric");

        assertThat(result).isEqualTo("test-app.test.metric");
    }

    @Test
    void createMetricName_WithoutApplicationName_ReturnsNormalizedName() {
        properties.getCommonTags().clear(); // Remove application name

        String result = metricsUtils.createMetricName("test.metric");

        assertThat(result).isEqualTo("test.metric");
    }

    @Test
    void remove_WithValidMeter_RemovesMeterFromRegistry() {
        String metricName = "test.counter";
        Counter counter = metricsUtils.counter(metricName);

        assertThat(registry.get(metricName).counter()).isNotNull();

        Meter removed = metricsUtils.remove(metricName);

        assertThat(removed).isNotNull();
        assertThatThrownBy(() -> registry.get(metricName).counter())
                .isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void clear_RemovesAllMetersFromRegistry() {
        metricsUtils.counter("test.counter1");
        metricsUtils.counter("test.counter2");
        metricsUtils.timer("test.timer");

        assertThat(registry.getMeters()).isNotEmpty();

        metricsUtils.clear();

        assertThat(registry.getMeters()).isEmpty();
    }

    // ==================== Validation Tests ====================

    @Test
    void validateNameAndTags_WithEmptyTagKey_ThrowsException() {
        assertThatThrownBy(() -> metricsUtils.counter("test.counter", "", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag key at index 0 cannot be null or empty");
    }

    @Test
    void validateNameAndTags_WithNullTagKey_ThrowsException() {
        assertThatThrownBy(() -> metricsUtils.counter("test.counter", null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag key at index 0 cannot be null or empty");
    }
}