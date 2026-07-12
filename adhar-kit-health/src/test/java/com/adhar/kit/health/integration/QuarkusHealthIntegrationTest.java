package com.adhar.kit.health.integration;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.registry.HealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QuarkusHealthIntegration}.
 */
class QuarkusHealthIntegrationTest {

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
            QuarkusHealthIntegration.createHealthRegistry(List.of(indicator("a"), indicator("b")));
        try {
            assertThat(registry.getIndicators()).containsKeys("a", "b");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void constructor_isInstantiable() {
        assertThat(new QuarkusHealthIntegration()).isNotNull();
    }

    @Test
    void isQuarkusAvailable_returnsTrueOnClasspath() {
        // quarkus-core is on the test classpath.
        assertThat(QuarkusHealthIntegration.isQuarkusAvailable()).isTrue();
    }
}
