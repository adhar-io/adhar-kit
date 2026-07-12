package com.adhar.kit.health.integration;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.registry.HealthRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpringBootHealthIntegration}.
 */
class SpringBootHealthIntegrationTest {

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
    void createHealthRegistry_registersAllIndicators() {
        HealthRegistry registry = SpringBootHealthIntegration.createHealthRegistry(
            List.of(indicator("custom1"), indicator("custom2")));
        try {
            assertThat(registry.getIndicators()).containsKeys("custom1", "custom2");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void createHealthRegistryWithAutoConfig_addsAllDetectedIndicators() {
        AdharHealthProperties properties = new AdharHealthProperties();

        HealthRegistry registry = SpringBootHealthIntegration.createHealthRegistryWithAutoConfig(
            List.of(indicator("custom")),
            properties,
            new Object(),  // redis connection factory
            new Object(),  // kafka admin client
            new Object(),  // mongo client
            new Object(),  // elasticsearch client
            new Object()); // grpc channel
        try {
            assertThat(registry.getIndicators())
                .containsKeys("custom", "redis", "kafka", "mongodb", "elasticsearch", "grpc");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void createHealthRegistryWithAutoConfig_withNoClients_addsOnlyCustom() {
        AdharHealthProperties properties = new AdharHealthProperties();

        HealthRegistry registry = SpringBootHealthIntegration.createHealthRegistryWithAutoConfig(
            List.of(indicator("custom")),
            properties,
            null, null, null, null, null);
        try {
            assertThat(registry.getIndicators()).containsOnlyKeys("custom");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void constructor_isInstantiable() {
        assertThat(new SpringBootHealthIntegration()).isNotNull();
    }

    @Test
    void availabilityChecks_reflectClasspath() {
        // All of these client libraries are present on the test classpath.
        assertThat(SpringBootHealthIntegration.isSpringBootAvailable()).isTrue();
        assertThat(SpringBootHealthIntegration.isRedisAvailable()).isTrue();
        assertThat(SpringBootHealthIntegration.isKafkaAvailable()).isTrue();
        assertThat(SpringBootHealthIntegration.isMongoAvailable()).isTrue();
        assertThat(SpringBootHealthIntegration.isElasticsearchAvailable()).isTrue();
        assertThat(SpringBootHealthIntegration.isGrpcAvailable()).isTrue();
    }
}
