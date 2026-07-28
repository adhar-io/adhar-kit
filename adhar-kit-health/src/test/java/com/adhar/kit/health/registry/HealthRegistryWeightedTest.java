package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HealthRegistry} weighted aggregation.
 */
class HealthRegistryWeightedTest {

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

    @Test
    void noWeights_fallsBackToCriticalBehaviour_andHasNoScore() {
        registry.register(indicator("db", Health.Status.UP));
        registry.register(indicator("cache", Health.Status.DOWN));

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(response.getDetails()).doesNotContainKey(HealthRegistry.WEIGHTED_SCORE_DETAIL);
    }

    @Test
    void allWeightedUp_isUpWithScoreOne() {
        registry.register(indicator("a", Health.Status.UP), true, 2.0);
        registry.register(indicator("b", Health.Status.UP), true, 1.0);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.UP);
        assertThat((Double) response.getDetails().get(HealthRegistry.WEIGHTED_SCORE_DETAIL))
                .isEqualTo(1.0);
    }

    @Test
    void partialWeightedFailure_isDegraded_withDefaultThresholds() {
        // default thresholds: up=1.0, down=0.0 -> anything between is OUT_OF_SERVICE
        registry.register(indicator("a", Health.Status.UP), true, 3.0);
        registry.register(indicator("b", Health.Status.DOWN), true, 1.0);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
        // score = (3*1.0 + 1*0.0) / 4 = 0.75
        assertThat((Double) response.getDetails().get(HealthRegistry.WEIGHTED_SCORE_DETAIL))
                .isEqualTo(0.75);
        assertThat(response.getDetails().get(HealthRegistry.DEGRADED_DETAIL))
                .isEqualTo(java.util.List.of("b"));
    }

    @Test
    void allWeightedDown_isDown() {
        registry.register(indicator("a", Health.Status.DOWN), true, 1.0);
        registry.register(indicator("b", Health.Status.DOWN), true, 1.0);

        HealthResponse response = registry.checkHealth();

        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat((Double) response.getDetails().get(HealthRegistry.WEIGHTED_SCORE_DETAIL))
                .isEqualTo(0.0);
    }

    @Test
    void configurableThresholds_produceDownForLowScore() {
        // Require 0.9 for UP, and treat <=0.8 as DOWN.
        registry.setWeightedThresholds(0.9, 0.8);
        registry.register(indicator("a", Health.Status.UP), true, 1.0);
        registry.register(indicator("b", Health.Status.DOWN), true, 1.0);

        HealthResponse response = registry.checkHealth();

        // score = 0.5 <= 0.8 -> DOWN
        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void configurableThresholds_produceDegradedForMidScore() {
        registry.setWeightedThresholds(0.9, 0.3);
        registry.register(indicator("a", Health.Status.UP), true, 1.0);
        registry.register(indicator("b", Health.Status.DOWN), true, 1.0);

        HealthResponse response = registry.checkHealth();

        // score = 0.5 -> between 0.3 and 0.9 -> OUT_OF_SERVICE
        assertThat(response.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
    }

    @Test
    void unweightedCriticalDown_stillFailsWeightedGroup() {
        registry.register(indicator("weighted", Health.Status.UP), true, 1.0);
        registry.register(indicator("criticalPlain", Health.Status.DOWN)); // weight 0, critical

        HealthResponse response = registry.checkHealth();

        // weighted score is 1.0 (UP) but the unweighted critical DOWN folds in as DOWN
        assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(response.getDetails()).containsKey(HealthRegistry.WEIGHTED_SCORE_DETAIL);
    }

    @Test
    void unknownAndOutOfService_scoreAsHalf() {
        registry.register(indicator("a", Health.Status.UNKNOWN), true, 1.0);
        registry.register(indicator("b", Health.Status.OUT_OF_SERVICE), true, 1.0);

        HealthResponse response = registry.checkHealth();

        assertThat((Double) response.getDetails().get(HealthRegistry.WEIGHTED_SCORE_DETAIL))
                .isEqualTo(0.5);
        assertThat(response.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
    }

    @Test
    void setWeight_appliesToExistingIndicator_andReturnsFlags() {
        registry.register(indicator("a", Health.Status.DOWN));
        assertThat(registry.getWeight("a")).isEqualTo(0.0);

        assertThat(registry.setWeight("a", 5.0)).isTrue();
        assertThat(registry.getWeight("a")).isEqualTo(5.0);
        assertThat(registry.setWeight("missing", 1.0)).isFalse();
        assertThat(registry.getWeight("missing")).isEqualTo(0.0);
    }

    @Test
    void register_negativeWeight_throws() {
        assertThatThrownBy(() -> registry.register(indicator("a", Health.Status.UP), true, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setWeight_negative_throws() {
        registry.register(indicator("a", Health.Status.UP));
        assertThatThrownBy(() -> registry.setWeight("a", -0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setWeightedThresholds_downAboveUp_throws() {
        assertThatThrownBy(() -> registry.setWeightedThresholds(0.5, 0.6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
