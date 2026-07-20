package com.adhar.kit.health.registry;

import com.adhar.kit.health.api.HealthService.HealthStatus;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RegistryHealthService}.
 */
class RegistryHealthServiceTest {

    private RegistryHealthService service;

    @BeforeEach
    void setUp() {
        service = new RegistryHealthService();
    }

    @AfterEach
    void tearDown() {
        service.getRegistry().shutdown();
    }

    @Test
    void noChecksRegistered_everythingIsUp() {
        assertThat(service.getHealth()).isEqualTo(HealthStatus.UP);
        assertThat(service.getLiveness()).isEqualTo(HealthStatus.UP);
        assertThat(service.getReadiness()).isEqualTo(HealthStatus.UP);
        assertThat(service.getDetailedHealth()).isEmpty();
    }

    @Test
    void registerHealthCheck_goesToDefaultGroup() {
        service.registerHealthCheck("db", () -> HealthStatus.UP);

        assertThat(service.getRegistry().getGroups("db"))
            .containsExactly(HealthRegistry.DEFAULT_GROUP);
        assertThat(service.getHealth()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void livenessAndReadinessChecks_areGroupScoped() {
        service.registerLivenessCheck("live", () -> HealthStatus.UP);
        service.registerReadinessCheck("ready", () -> HealthStatus.DOWN);

        assertThat(service.getLiveness()).isEqualTo(HealthStatus.UP);
        assertThat(service.getReadiness()).isEqualTo(HealthStatus.DOWN);
        // overall health spans all groups
        assertThat(service.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void failingReadinessCheck_doesNotAffectLiveness() {
        service.registerLivenessCheck("live", () -> HealthStatus.UP);
        service.registerReadinessCheck("ready", () -> HealthStatus.DOWN);

        assertThat(service.getLiveness()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void unknownStatus_isPropagated() {
        service.registerHealthCheck("mystery", () -> HealthStatus.UNKNOWN);

        assertThat(service.getHealth()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(service.getDetailedHealth()).containsEntry("mystery", HealthStatus.UNKNOWN);
    }

    @Test
    void nullSupplierResult_mapsToUnknown() {
        service.registerHealthCheck("broken", () -> null);

        assertThat(service.getDetailedHealth()).containsEntry("broken", HealthStatus.UNKNOWN);
    }

    @Test
    void throwingSupplier_mapsToDown() {
        service.registerHealthCheck("boom", () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(service.getHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(service.getDetailedHealth()).containsEntry("boom", HealthStatus.DOWN);
    }

    @Test
    void outOfServiceIndicator_mapsToDown() {
        service.getRegistry().register(new AdharHealthIndicator() {
            @Override
            public Health check() {
                return Health.outOfService().component("gate").build();
            }

            @Override
            public String getName() {
                return "gate";
            }
        });

        assertThat(service.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void getDetailedHealth_containsAllChecks() {
        service.registerHealthCheck("a", () -> HealthStatus.UP);
        service.registerLivenessCheck("b", () -> HealthStatus.UP);
        service.registerReadinessCheck("c", () -> HealthStatus.DOWN);

        Map<String, HealthStatus> details = service.getDetailedHealth();

        assertThat(details)
            .containsEntry("a", HealthStatus.UP)
            .containsEntry("b", HealthStatus.UP)
            .containsEntry("c", HealthStatus.DOWN);
    }

    @Test
    void unregisterAndHasHealthCheck() {
        service.registerHealthCheck("db", () -> HealthStatus.UP);

        assertThat(service.hasHealthCheck("db")).isTrue();
        assertThat(service.unregisterHealthCheck("db")).isTrue();
        assertThat(service.unregisterHealthCheck("db")).isFalse();
        assertThat(service.hasHealthCheck("db")).isFalse();
    }

    @Test
    void constructor_withExistingRegistry_usesIt() {
        HealthRegistry registry = new HealthRegistry();
        try {
            RegistryHealthService custom = new RegistryHealthService(registry);
            assertThat(custom.getRegistry()).isSameAs(registry);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void toHealthStatus_mapsAllStatuses() {
        assertThat(RegistryHealthService.toHealthStatus(Health.Status.UP)).isEqualTo(HealthStatus.UP);
        assertThat(RegistryHealthService.toHealthStatus(Health.Status.UNKNOWN)).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(RegistryHealthService.toHealthStatus(Health.Status.DOWN)).isEqualTo(HealthStatus.DOWN);
        assertThat(RegistryHealthService.toHealthStatus(Health.Status.OUT_OF_SERVICE)).isEqualTo(HealthStatus.DOWN);
    }
}
