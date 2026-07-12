package com.adhar.kit.metrics.spring;

import com.adhar.kit.commons.framework.Framework;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural unit tests for {@link SpringMetricsAdapter}.
 */
class SpringMetricsAdapterTest {

    private SimpleMeterRegistry registry;
    private SpringMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new SpringMetricsAdapter(registry);
    }

    @Test
    void constructorRejectsNullRegistry() {
        assertThatThrownBy(() -> new SpringMetricsAdapter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MeterRegistry cannot be null");
    }

    @Test
    void frameworkAndServiceAccessors() {
        assertThat(adapter.getSupportedFramework()).isEqualTo(Framework.SPRING_BOOT);
        assertThat(adapter.getService()).isSameAs(adapter);
    }

    @Test
    void counterTimerGauge() {
        adapter.counter("c", "k", "v").increment();
        assertThat(registry.counter("c", "k", "v").count()).isEqualTo(1.0);

        adapter.timer("t", "k", "v").record(java.time.Duration.ofMillis(1));
        assertThat(registry.timer("t", "k", "v").count()).isEqualTo(1L);

        Integer value = adapter.gauge("g", () -> 42, "k", "v");
        assertThat(value).isEqualTo(42);
        assertThat(registry.find("g").tags("k", "v").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void recordTimeReturnsResultAndRecords() {
        String result = adapter.recordTime("op", () -> "done");
        assertThat(result).isEqualTo("done");
        assertThat(registry.timer("op").count()).isEqualTo(1L);
    }

    @Test
    void recordTimeWrapsExceptions() {
        assertThatThrownBy(() -> adapter.recordTime("op.fail", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Timed operation failed");
    }

    @Test
    void incrementOverloads() {
        adapter.increment("i", "k", "v");
        adapter.increment("i", 4.0, "k", "v");
        assertThat(registry.counter("i", "k", "v").count()).isEqualTo(5.0);
    }
}
