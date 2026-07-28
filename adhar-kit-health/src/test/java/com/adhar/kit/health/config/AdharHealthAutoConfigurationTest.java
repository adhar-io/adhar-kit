package com.adhar.kit.health.config;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.event.HealthEventBroadcaster;
import com.adhar.kit.health.event.HealthTransitionApplicationEvent;
import com.adhar.kit.health.event.SpringHealthEventPublisher;
import com.adhar.kit.health.indicator.CircuitBreakerHealthIndicator;
import com.adhar.kit.health.lifecycle.ReadinessStateManager;
import com.adhar.kit.health.lifecycle.SpringReadinessLifecycle;
import com.adhar.kit.health.registry.HealthRegistry;
import com.adhar.kit.health.registry.RegistryHealthService;
import com.adhar.kit.health.spi.CircuitBreakerStateProvider;
import com.adhar.kit.health.spi.CircuitBreakerStatus;
import com.adhar.kit.health.web.HealthEventSseController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    @Test
    void weightBinding_appliesWeightsAndThresholds() {
        runner.withPropertyValues(
            "adhar.health.weighted.up-threshold=0.9",
            "adhar.health.weighted.down-threshold=0.2",
            "adhar.health.weighted.weights.memory=3.0"
        ).run(context -> {
            HealthRegistry registry = context.getBean(HealthRegistry.class);
            assertThat(registry.getWeight("memory")).isEqualTo(3.0);
        });
    }

    @Test
    void eventStream_providesBroadcasterAndPublisher() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HealthEventBroadcaster.class);
            assertThat(context).hasSingleBean(SpringHealthEventPublisher.class);

            // publisher is wired to the registry and republishes transitions as events
            java.util.List<HealthTransitionApplicationEvent> events = new java.util.ArrayList<>();
            context.addApplicationListener(
                (org.springframework.context.ApplicationListener<HealthTransitionApplicationEvent>) events::add);

            HealthRegistry registry = context.getBean(HealthRegistry.class);
            registry.checkHealth();

            assertThat(context.getBean(HealthEventBroadcaster.class)).isNotNull();
            assertThat(events).isNotEmpty();
        });
    }

    @Test
    void eventsDisabled_skipsApplicationEventPublisher() {
        runner.withPropertyValues("adhar.health.events.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(HealthEventBroadcaster.class);
            assertThat(context).doesNotHaveBean(SpringHealthEventPublisher.class);
        });
    }

    @Test
    void sseController_isWiredWhenWebMvcPresent() {
        runner.run(context -> assertThat(context).hasSingleBean(HealthEventSseController.class));
    }

    @Test
    void sseDisabled_skipsController() {
        runner.withPropertyValues("adhar.health.events.sse-enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(HealthEventSseController.class));
    }

    @Test
    void circuitBreakerIndicator_absentWithoutProvider() {
        runner.run(context ->
            assertThat(context).doesNotHaveBean(CircuitBreakerHealthIndicator.class));
    }

    @Test
    void circuitBreakerIndicator_wiredWhenProviderPresent_andRegistered() {
        runner.withUserConfiguration(CircuitBreakerProviderConfig.class).run(context -> {
            assertThat(context).hasSingleBean(CircuitBreakerHealthIndicator.class);
            HealthRegistry registry = context.getBean(HealthRegistry.class);
            assertThat(registry.getIndicators()).containsKey(CircuitBreakerHealthIndicator.NAME);
        });
    }

    @Test
    void circuitBreakerIndicator_disabledByProperty() {
        runner.withUserConfiguration(CircuitBreakerProviderConfig.class)
            .withPropertyValues("adhar.health.circuit-breaker.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(CircuitBreakerHealthIndicator.class));
    }

    @Configuration
    static class CircuitBreakerProviderConfig {
        @Bean
        CircuitBreakerStateProvider testProvider() {
            return () -> List.of(new CircuitBreakerStatus("test", CircuitBreakerStatus.State.CLOSED));
        }
    }
}
