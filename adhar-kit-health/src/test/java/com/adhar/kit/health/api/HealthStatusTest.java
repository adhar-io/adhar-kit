package com.adhar.kit.health.api;

import com.adhar.kit.health.api.HealthService.HealthStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link HealthStatus} enum declared in {@link HealthService}.
 */
class HealthStatusTest {

    @Test
    void values_containsAllStatuses() {
        assertThat(HealthStatus.values())
            .containsExactly(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.UNKNOWN);
    }

    @Test
    void valueOf_resolvesEachConstant() {
        assertThat(HealthStatus.valueOf("UP")).isEqualTo(HealthStatus.UP);
        assertThat(HealthStatus.valueOf("DOWN")).isEqualTo(HealthStatus.DOWN);
        assertThat(HealthStatus.valueOf("UNKNOWN")).isEqualTo(HealthStatus.UNKNOWN);
    }
}
