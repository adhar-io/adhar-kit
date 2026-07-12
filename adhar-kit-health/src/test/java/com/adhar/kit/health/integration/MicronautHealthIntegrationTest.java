package com.adhar.kit.health.integration;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.registry.HealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MicronautHealthIntegration}.
 */
class MicronautHealthIntegrationTest {

    private static AdharHealthIndicator indicator(String name) {
        return new AdharHealthIndicator() {
            @Override
            public Health check() {
                return Health.up().component(name).build();
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    @Test
    void createHealthRegistry_registersIndicators() {
        HealthRegistry registry =
            MicronautHealthIntegration.createHealthRegistry(List.of(indicator("a"), indicator("b")));
        try {
            assertThat(registry.getIndicators()).containsKeys("a", "b");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void constructor_isInstantiable() {
        assertThat(new MicronautHealthIntegration()).isNotNull();
    }

    @Test
    void isMicronautAvailable_returnsTrueOnClasspath() {
        // io.micronaut.runtime.Micronaut is on the test classpath.
        assertThat(MicronautHealthIntegration.isMicronautAvailable()).isTrue();
    }
}
