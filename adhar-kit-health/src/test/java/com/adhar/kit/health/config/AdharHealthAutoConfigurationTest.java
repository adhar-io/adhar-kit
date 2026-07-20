package com.adhar.kit.health.config;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.lifecycle.ReadinessStateManager;
import com.adhar.kit.health.lifecycle.SpringReadinessLifecycle;
import com.adhar.kit.health.registry.HealthRegistry;
import com.adhar.kit.health.registry.RegistryHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link AdharHealthAutoConfiguration} wiring.
 */
class AdharHealthAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AdharHealthAutoConfiguration.class))
        // avoid piling up JVM shutdown hooks from tests
        .withPropertyValues("adhar.health.readiness-gate.shutdown-hook=false");

    @Test
    void defaultConfiguration_providesAllBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AdharHealthProperties.class);
            assertThat(context).hasSingleBean(HealthRegistry.class);
            assertThat(context).hasSingleBean(RegistryHealthService.class);
            assertThat(context).hasSingleBean(ReadinessStateManager.class);
            assertThat(context).hasSingleBean(SpringReadinessLifecycle.class);

            HealthRegistry registry = context.getBean(HealthRegistry.class);
            assertThat(registry.getCacheTtlMillis()).isEqualTo(10_000);
            assertThat(registry.getIndicators()).containsKeys("memory", "readinessGate");
            assertThat(registry.getGroups(ReadinessStateManager.NAME))
                .containsExactly(HealthRegistry.READINESS_GROUP);
        });
    }

    @Test
    void masterSwitchDisabled_backsOffCompletely() {
        runner.withPropertyValues("adhar.health.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(HealthRegistry.class);
            assertThat(context).doesNotHaveBean(HealthService.class);
        });
    }

    @Test
    void readinessGateDisabled_skipsGateAndLifecycle() {
        runner.withPropertyValues("adhar.health.readiness-gate.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(HealthRegistry.class);
            assertThat(context).doesNotHaveBean(ReadinessStateManager.class);
            assertThat(context).doesNotHaveBean(SpringReadinessLifecycle.class);
        });
    }

    @Test
    void propertyBinding_appliesToRegistry() {
        runner.withPropertyValues(
            "adhar.health.cache.ttl=2500",
            "adhar.health.memory.enabled=false",
            "adhar.health.history.capacity=42"
        ).run(context -> {
            HealthRegistry registry = context.getBean(HealthRegistry.class);
            assertThat(registry.getCacheTtlMillis()).isEqualTo(2500);
            assertThat(registry.getIndicators()).doesNotContainKey("memory");
            assertThat(registry.getHistory().getCapacity()).isEqualTo(42);
        });
    }
}
