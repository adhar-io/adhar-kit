package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MemoryHealthIndicator}.
 */
class MemoryHealthIndicatorTest {

    private static MemoryMXBean bean(long used, long committed, long max) {
        MemoryMXBean bean = mock(MemoryMXBean.class);
        when(bean.getHeapMemoryUsage()).thenReturn(new MemoryUsage(0, used, committed, max));
        return bean;
    }

    @Test
    void check_usageBelowThreshold_returnsUp() {
        MemoryHealthIndicator indicator = new MemoryHealthIndicator(bean(100, 500, 1000), 0.9);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getComponent()).isEqualTo("memory");
        assertThat(health.getDetails())
            .containsEntry("heapUsed", 100L)
            .containsEntry("heapMax", 1000L)
            .containsEntry("heapCommitted", 500L)
            .containsEntry("threshold", 0.9);
    }

    @Test
    void check_usageAtOrAboveThreshold_returnsDown() {
        MemoryHealthIndicator indicator = new MemoryHealthIndicator(bean(950, 1000, 1000), 0.9);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).contains("above threshold");
    }

    @Test
    void check_noMaxHeap_fallsBackToCommitted() {
        MemoryHealthIndicator indicator = new MemoryHealthIndicator(bean(10, 100, -1), 0.9);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("heapMax", 100L);
    }

    @Test
    void check_beanThrows_returnsDown() {
        MemoryMXBean broken = mock(MemoryMXBean.class);
        when(broken.getHeapMemoryUsage()).thenThrow(new IllegalStateException("broken"));

        Health health = new MemoryHealthIndicator(broken, 0.9).check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).isEqualTo("broken");
    }

    @Test
    void defaultConstructor_usesRealMemoryBean() {
        Health health = new MemoryHealthIndicator().check();

        assertThat(health.getStatus()).isIn(Health.Status.UP, Health.Status.DOWN);
        assertThat(health.getDetails()).containsKeys("heapUsed", "heapMax", "heapUsage");
    }

    @Test
    void configConstructor_appliesThreshold() {
        AdharHealthProperties.MemoryConfig config = new AdharHealthProperties.MemoryConfig();
        config.setThreshold(0.75);

        Health health = new MemoryHealthIndicator(config).check();

        assertThat(health.getDetails()).containsEntry("threshold", 0.75);
    }

    @Test
    void getName_returnsMemory() {
        assertThat(new MemoryHealthIndicator().getName()).isEqualTo("memory");
    }
}
