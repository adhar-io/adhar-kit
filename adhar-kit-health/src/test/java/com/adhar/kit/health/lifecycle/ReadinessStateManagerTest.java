package com.adhar.kit.health.lifecycle;

import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.registry.HealthRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ReadinessStateManager}.
 */
class ReadinessStateManagerTest {

    @Test
    void defaultConstructor_startsNotReady() {
        ReadinessStateManager manager = new ReadinessStateManager();

        assertThat(manager.isReady()).isFalse();
        assertThat(manager.getReason()).isNotBlank();

        Health health = manager.check();
        assertThat(health.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
        assertThat(health.getComponent()).isEqualTo(ReadinessStateManager.NAME);
        assertThat(health.getDetails())
            .containsEntry("ready", false)
            .containsKey("reason");
    }

    @Test
    void initiallyReadyConstructor_startsReady() {
        ReadinessStateManager manager = new ReadinessStateManager(true);

        assertThat(manager.isReady()).isTrue();
        assertThat(manager.getReason()).isNull();
        assertThat(manager.check().getStatus()).isEqualTo(Health.Status.UP);
    }

    @Test
    void markReady_opensGate() {
        ReadinessStateManager manager = new ReadinessStateManager();

        manager.markReady();

        assertThat(manager.isReady()).isTrue();
        assertThat(manager.getReason()).isNull();
        Health health = manager.check();
        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("ready", true);
    }

    @Test
    void markNotReady_closesGateWithReason() {
        ReadinessStateManager manager = new ReadinessStateManager(true);

        manager.markNotReady("Maintenance window");

        assertThat(manager.isReady()).isFalse();
        assertThat(manager.getReason()).isEqualTo("Maintenance window");
        assertThat(manager.check().getDetails()).containsEntry("reason", "Maintenance window");
    }

    @Test
    void markNotReady_nullReason_usesDefaultReason() {
        ReadinessStateManager manager = new ReadinessStateManager(true);

        manager.markNotReady(null);

        assertThat(manager.getReason()).isEqualTo("Not ready");
    }

    @Test
    void onShutdown_flipsGateToNotReady() {
        ReadinessStateManager manager = new ReadinessStateManager(true);

        manager.onShutdown();

        assertThat(manager.isReady()).isFalse();
        assertThat(manager.getReason()).contains("shutting down");
        assertThat(manager.check().getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
    }

    @Test
    void registerShutdownHook_isIdempotent() {
        ReadinessStateManager manager = new ReadinessStateManager(true);

        assertThat(manager.registerShutdownHook()).isTrue();
        assertThat(manager.registerShutdownHook()).isFalse();
    }

    @Test
    void getName_returnsReadinessGate() {
        assertThat(new ReadinessStateManager().getName()).isEqualTo(ReadinessStateManager.NAME);
    }

    @Test
    void readinessGate_drivesReadinessGroup() {
        HealthRegistry registry = new HealthRegistry();
        try {
            ReadinessStateManager manager = new ReadinessStateManager();
            registry.register(manager, HealthRegistry.READINESS_GROUP);

            assertThat(registry.checkGroup(HealthRegistry.READINESS_GROUP).getStatus())
                .isEqualTo(Health.Status.OUT_OF_SERVICE);

            manager.markReady();
            assertThat(registry.checkGroup(HealthRegistry.READINESS_GROUP).getStatus())
                .isEqualTo(Health.Status.UP);

            manager.onShutdown();
            assertThat(registry.checkGroup(HealthRegistry.READINESS_GROUP).getStatus())
                .isEqualTo(Health.Status.OUT_OF_SERVICE);
        } finally {
            registry.shutdown();
        }
    }
}
