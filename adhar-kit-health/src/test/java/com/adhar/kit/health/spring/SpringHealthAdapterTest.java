package com.adhar.kit.health.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.health.api.HealthService.HealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringHealthAdapter}.
 */
class SpringHealthAdapterTest {

    private SpringHealthAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringHealthAdapter();
    }

    @Test
    void getSupportedFramework_isSpringBoot() {
        assertThat(adapter.getSupportedFramework()).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void getService_returnsSelf() {
        assertThat(adapter.getService()).isSameAs(adapter);
    }

    @Test
    void getHealth_whenNoChecks_returnsUp() {
        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void getHealth_whenAllChecksUp_returnsUp() {
        adapter.registerHealthCheck("a", () -> HealthStatus.UP);
        adapter.registerHealthCheck("b", () -> HealthStatus.UP);

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void getHealth_whenOneCheckDown_returnsDown() {
        adapter.registerHealthCheck("a", () -> HealthStatus.UP);
        adapter.registerHealthCheck("b", () -> HealthStatus.DOWN);

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void getHealth_whenCheckThrows_returnsDown() {
        adapter.registerHealthCheck("boom", () -> {
            throw new RuntimeException("failure");
        });

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void getLiveness_reflectsRegisteredLivenessChecks() {
        adapter.registerLivenessCheck("live", () -> HealthStatus.DOWN);

        assertThat(adapter.getLiveness()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void getReadiness_reflectsRegisteredReadinessChecks() {
        adapter.registerReadinessCheck("ready", () -> HealthStatus.UP);

        assertThat(adapter.getReadiness()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void getDetailedHealth_collectsEachStatusAndHandlesExceptions() {
        adapter.registerHealthCheck("ok", () -> HealthStatus.UP);
        adapter.registerHealthCheck("bad", () -> {
            throw new IllegalStateException("kaboom");
        });

        Map<String, HealthStatus> details = adapter.getDetailedHealth();

        assertThat(details).containsEntry("ok", HealthStatus.UP);
        assertThat(details).containsEntry("bad", HealthStatus.DOWN);
    }

    @Test
    void unregisterHealthCheck_returnsTrueWhenPresentFalseOtherwise() {
        adapter.registerHealthCheck("temp", () -> HealthStatus.UP);

        assertThat(adapter.unregisterHealthCheck("temp")).isTrue();
        assertThat(adapter.unregisterHealthCheck("temp")).isFalse();
        assertThat(adapter.hasHealthCheck("temp")).isFalse();
    }

    @Test
    void hasHealthCheck_reflectsRegistration() {
        assertThat(adapter.hasHealthCheck("x")).isFalse();
        adapter.registerHealthCheck("x", () -> HealthStatus.UP);
        assertThat(adapter.hasHealthCheck("x")).isTrue();
    }

    @Test
    void getSpringHealth_whenUp_buildsUpHealthWithDetails() {
        adapter.registerHealthCheck("db", () -> HealthStatus.UP);

        org.springframework.boot.health.contributor.Health springHealth = adapter.getSpringHealth();

        assertThat(springHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(springHealth.getDetails()).containsEntry("db", "UP");
    }

    @Test
    void getSpringHealth_whenDown_buildsDownHealth() {
        adapter.registerHealthCheck("db", () -> HealthStatus.DOWN);

        org.springframework.boot.health.contributor.Health springHealth = adapter.getSpringHealth();

        assertThat(springHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(springHealth.getDetails()).containsEntry("db", "DOWN");
    }
}
