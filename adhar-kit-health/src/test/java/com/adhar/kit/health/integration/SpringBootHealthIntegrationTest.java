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
    void createHealthRegistryWithAutoConfig_withNoClients_addsCustomAndBuiltIns() {
        AdharHealthProperties properties = new AdharHealthProperties();

        HealthRegistry registry = SpringBootHealthIntegration.createHealthRegistryWithAutoConfig(
            List.of(indicator("custom")),
            properties,
            null, null, null, null, null);
        try {
            // memory is auto-configured by default alongside custom indicators
            assertThat(registry.getIndicators()).containsOnlyKeys("custom", "memory");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureRegistry_appliesCacheHistoryAndMemoryDefaults() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getHistory().setCapacity(7);
        HealthRegistry registry = new HealthRegistry();
        try {
            SpringBootHealthIntegration.configureRegistry(registry, properties);

            assertThat(registry.getCacheTtlMillis()).isEqualTo(10_000);
            assertThat(registry.getHistory().getCapacity()).isEqualTo(7);
            assertThat(registry.getIndicators()).containsKey("memory");
            // memory is registered non-critical in default + liveness groups
            assertThat(registry.isCritical("memory")).isFalse();
            assertThat(registry.getGroups("memory")).containsExactlyInAnyOrder(
                HealthRegistry.DEFAULT_GROUP, HealthRegistry.LIVENESS_GROUP);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureRegistry_cacheDisabled_setsTtlZero() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getCache().setEnabled(false);
        properties.getMemory().setEnabled(false);
        HealthRegistry registry = new HealthRegistry();
        try {
            SpringBootHealthIntegration.configureRegistry(registry, properties);

            assertThat(registry.getCacheTtlMillis()).isZero();
            assertThat(registry.getIndicators()).doesNotContainKey("memory");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureRegistry_certificateEnabledWithKeystore_registersIndicator() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getMemory().setEnabled(false);
        properties.getCertificate().setEnabled(true);
        properties.getCertificate().setKeystorePath("/tmp/keystore.p12");
        HealthRegistry registry = new HealthRegistry();
        try {
            SpringBootHealthIntegration.configureRegistry(registry, properties);

            assertThat(registry.getIndicators()).containsKey("certificate");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureRegistry_certificateEnabledWithoutSource_isSkipped() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getMemory().setEnabled(false);
        properties.getCertificate().setEnabled(true);
        HealthRegistry registry = new HealthRegistry();
        try {
            SpringBootHealthIntegration.configureRegistry(registry, properties);

            assertThat(registry.getIndicators()).doesNotContainKey("certificate");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureReadinessGate_registersGateInReadinessGroup() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getReadinessGate().setShutdownHook(false);
        HealthRegistry registry = new HealthRegistry();
        try {
            var manager = SpringBootHealthIntegration.configureReadinessGate(registry, properties);

            assertThat(manager).isNotNull();
            assertThat(manager.isReady()).isFalse();
            assertThat(registry.getGroups(manager.getName()))
                .containsExactly(HealthRegistry.READINESS_GROUP);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureReadinessGate_disabled_returnsNull() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getReadinessGate().setEnabled(false);
        HealthRegistry registry = new HealthRegistry();
        try {
            assertThat(SpringBootHealthIntegration.configureReadinessGate(registry, properties)).isNull();
            assertThat(registry.getIndicators()).isEmpty();
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void configureReadinessGate_initiallyReadyAndShutdownHook() {
        AdharHealthProperties properties = new AdharHealthProperties();
        properties.getReadinessGate().setInitiallyReady(true);
        properties.getReadinessGate().setShutdownHook(true);
        HealthRegistry registry = new HealthRegistry();
        try {
            var manager = SpringBootHealthIntegration.configureReadinessGate(registry, properties);

            assertThat(manager).isNotNull();
            assertThat(manager.isReady()).isTrue();
            // hook already registered by configureReadinessGate
            assertThat(manager.registerShutdownHook()).isFalse();
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
