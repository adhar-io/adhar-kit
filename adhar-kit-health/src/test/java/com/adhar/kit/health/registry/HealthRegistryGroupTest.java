package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HealthRegistry} groups and status aggregation.
 */
class HealthRegistryGroupTest {

    private HealthRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HealthRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private static AdharHealthIndicator indicator(String name, Health.Status status) {
        return new AdharHealthIndicator() {
            @Override
            public Health check() {
                return Health.builder().status(status).component(name).build();
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ---- worst-of aggregation matrix ----

    @Test
    void worstOf_emptyCollection_isUp() {
        assertThat(HealthRegistry.worstOf(List.of())).isEqualTo(Health.Status.UP);
    }

    @Test
    void worstOf_allUp_isUp() {
        assertThat(HealthRegistry.worstOf(List.of(Health.Status.UP, Health.Status.UP)))
            .isEqualTo(Health.Status.UP);
    }

    @Test
    void worstOf_unknownBeatsUp() {
        assertThat(HealthRegistry.worstOf(List.of(Health.Status.UP, Health.Status.UNKNOWN)))
            .isEqualTo(Health.Status.UNKNOWN);
    }

    @Test
    void worstOf_outOfServiceBeatsUnknown() {
        assertThat(HealthRegistry.worstOf(
                List.of(Health.Status.UNKNOWN, Health.Status.OUT_OF_SERVICE, Health.Status.UP)))
            .isEqualTo(Health.Status.OUT_OF_SERVICE);
    }

    @Test
    void worstOf_downBeatsEverything() {
        assertThat(HealthRegistry.worstOf(List.of(
                Health.Status.UP, Health.Status.UNKNOWN,
                Health.Status.OUT_OF_SERVICE, Health.Status.DOWN)))
            .isEqualTo(Health.Status.DOWN);
    }

    @Test
    void checkHealth_propagatesOutOfService() {
        registry.register(indicator("db", Health.Status.UP));
        registry.register(indicator("gate", Health.Status.OUT_OF_SERVICE));

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
    }

    @Test
    void checkHealth_propagatesUnknown() {
        registry.register(indicator("db", Health.Status.UP));
        registry.register(indicator("mystery", Health.Status.UNKNOWN));

        assertThat(registry.checkHealth().getStatus()).isEqualTo(Health.Status.UNKNOWN);
    }

    // ---- non-critical (degraded) indicators ----

    @Test
    void checkHealth_nonCriticalDown_degradesWithoutFailing() {
        registry.register(indicator("db", Health.Status.UP));
        registry.register(indicator("optionalCache", Health.Status.DOWN), false,
            HealthRegistry.DEFAULT_GROUP);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(response.getComponents().get("optionalCache").getStatus())
            .isEqualTo(Health.Status.DOWN);
        assertThat(response.getDetails().get(HealthRegistry.DEGRADED_DETAIL))
            .isEqualTo(List.of("optionalCache"));
    }

    @Test
    void checkHealth_nonCriticalUp_hasNoDegradedDetail() {
        registry.register(indicator("optionalCache", Health.Status.UP), false,
            HealthRegistry.DEFAULT_GROUP);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(response.getDetails()).doesNotContainKey(HealthRegistry.DEGRADED_DETAIL);
    }

    @Test
    void checkHealth_criticalDownAndNonCriticalDown_isDownAndDegraded() {
        registry.register(indicator("db", Health.Status.DOWN));
        registry.register(indicator("a", Health.Status.OUT_OF_SERVICE), false,
            HealthRegistry.DEFAULT_GROUP);
        registry.register(indicator("b", Health.Status.UNKNOWN), false,
            HealthRegistry.DEFAULT_GROUP);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(response.getDetails().get(HealthRegistry.DEGRADED_DETAIL))
            .isEqualTo(List.of("a", "b"));
    }

    // ---- group registration and filtering ----

    @Test
    void checkGroup_filtersIndicatorsByGroup() {
        registry.register(indicator("live", Health.Status.UP), HealthRegistry.LIVENESS_GROUP);
        registry.register(indicator("ready", Health.Status.DOWN), HealthRegistry.READINESS_GROUP);

        HealthResponse liveness = registry.checkGroup(HealthRegistry.LIVENESS_GROUP);
        HealthResponse readiness = registry.checkGroup(HealthRegistry.READINESS_GROUP);

        assertThat(liveness.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(liveness.getComponents()).containsOnlyKeys("live");
        assertThat(readiness.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(readiness.getComponents()).containsOnlyKeys("ready");
    }

    @Test
    void checkGroup_emptyGroup_isUp() {
        registry.register(indicator("db", Health.Status.DOWN));

        HealthResponse response = registry.checkGroup(HealthRegistry.STARTUP_GROUP);

        assertThat(response.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(response.getComponents()).isEmpty();
    }

    @Test
    void checkGroup_nullGroup_throws() {
        assertThatThrownBy(() -> registry.checkGroup(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkGroup_withTimeout_appliesTimeout() {
        registry.register(new AdharHealthIndicator() {
            @Override
            public Health check() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Health.up().component("slow").build();
            }

            @Override
            public String getName() {
                return "slow";
            }
        }, HealthRegistry.READINESS_GROUP);

        HealthResponse response = registry.checkGroup(HealthRegistry.READINESS_GROUP, 200);

        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(response.getComponents().get("slow").getError()).contains("timeout");
    }

    @Test
    void register_multiGroupIndicator_appearsInEachGroup() {
        registry.register(indicator("shared", Health.Status.UP),
            HealthRegistry.LIVENESS_GROUP, HealthRegistry.READINESS_GROUP);

        assertThat(registry.checkGroup(HealthRegistry.LIVENESS_GROUP).getComponents())
            .containsKey("shared");
        assertThat(registry.checkGroup(HealthRegistry.READINESS_GROUP).getComponents())
            .containsKey("shared");
        assertThat(registry.getGroups("shared"))
            .containsExactlyInAnyOrder(HealthRegistry.LIVENESS_GROUP, HealthRegistry.READINESS_GROUP);
    }

    @Test
    void register_withoutGroup_usesDefaultGroupAndCritical() {
        registry.register(indicator("db", Health.Status.UP));

        assertThat(registry.getGroups("db")).containsExactly(HealthRegistry.DEFAULT_GROUP);
        assertThat(registry.isCritical("db")).isTrue();
        assertThat(registry.checkGroup(HealthRegistry.DEFAULT_GROUP).getComponents())
            .containsKey("db");
    }

    @Test
    void register_emptyGroupArray_usesDefaultGroup() {
        registry.register(indicator("db", Health.Status.UP), true);

        assertThat(registry.getGroups("db")).containsExactly(HealthRegistry.DEFAULT_GROUP);
    }

    @Test
    void groupMetadata_forUnknownIndicator() {
        assertThat(registry.getGroups("missing")).isEmpty();
        assertThat(registry.isCritical("missing")).isFalse();
    }

    @Test
    void unregister_returnsWhetherIndicatorExisted() {
        registry.register(indicator("db", Health.Status.UP));

        assertThat(registry.unregister("db")).isTrue();
        assertThat(registry.unregister("db")).isFalse();
        assertThat(registry.getIndicator("db")).isNull();
    }

    @Test
    void checkHealth_includesAllGroups() {
        registry.register(indicator("default", Health.Status.UP));
        registry.register(indicator("live", Health.Status.UP), HealthRegistry.LIVENESS_GROUP);
        registry.register(indicator("ready", Health.Status.DOWN), HealthRegistry.READINESS_GROUP);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getComponents()).containsOnlyKeys("default", "live", "ready");
        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
    }
}
