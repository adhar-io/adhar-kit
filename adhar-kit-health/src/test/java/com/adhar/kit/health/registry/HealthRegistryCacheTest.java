package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthRegistry} TTL result caching.
 */
class HealthRegistryCacheTest {

    private HealthRegistry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    private static final class CountingIndicator implements AdharHealthIndicator {
        private final String name;
        private final AtomicInteger invocations = new AtomicInteger();

        CountingIndicator(String name) {
            this.name = name;
        }

        @Override
        public Health check() {
            invocations.incrementAndGet();
            return Health.up().component(name).build();
        }

        @Override
        public String getName() {
            return name;
        }

        int invocations() {
            return invocations.get();
        }
    }

    @Test
    void checkHealth_withinTtl_reusesCachedResult() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(60_000);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);

        HealthResponse first = registry.checkHealth();
        HealthResponse second = registry.checkHealth();

        assertThat(indicator.invocations()).isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void checkHealth_ttlZero_disablesCaching() {
        registry = new HealthRegistry();
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);

        registry.checkHealth();
        registry.checkHealth();

        assertThat(indicator.invocations()).isEqualTo(2);
    }

    @Test
    void checkHealth_afterTtlExpiry_reexecutesChecks() throws Exception {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(100);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);

        registry.checkHealth();
        Thread.sleep(250);
        registry.checkHealth();

        assertThat(indicator.invocations()).isEqualTo(2);
    }

    @Test
    void register_invalidatesCache() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(60_000);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);
        registry.checkHealth();

        registry.register(new CountingIndicator("cache"));
        HealthResponse response = registry.checkHealth();

        assertThat(indicator.invocations()).isEqualTo(2);
        assertThat(response.getComponents()).containsKeys("db", "cache");
    }

    @Test
    void unregister_invalidatesCache() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(60_000);
        CountingIndicator db = new CountingIndicator("db");
        CountingIndicator cache = new CountingIndicator("cache");
        registry.register(db);
        registry.register(cache);
        registry.checkHealth();

        registry.unregister("cache");

        assertThat(registry.checkHealth().getComponents()).containsOnlyKeys("db");
    }

    @Test
    void invalidateCache_forcesReexecution() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(60_000);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);
        registry.checkHealth();

        registry.invalidateCache();
        registry.checkHealth();

        assertThat(indicator.invocations()).isEqualTo(2);
    }

    @Test
    void groupAndOverallResults_cacheIndependently() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(60_000);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);

        registry.checkHealth();
        registry.checkGroup(HealthRegistry.DEFAULT_GROUP);
        registry.checkGroup(HealthRegistry.DEFAULT_GROUP);

        // one execution for the overall scrape, one for the group scrape (then cached)
        assertThat(indicator.invocations()).isEqualTo(2);
    }

    @Test
    void constructor_withTtl_enablesCaching() {
        registry = new HealthRegistry(60_000, 10);
        CountingIndicator indicator = new CountingIndicator("db");
        registry.register(indicator);

        registry.checkHealth();
        registry.checkHealth();

        assertThat(registry.getCacheTtlMillis()).isEqualTo(60_000);
        assertThat(indicator.invocations()).isEqualTo(1);
    }

    @Test
    void setCacheTtlMillis_negative_clampsToDisabled() {
        registry = new HealthRegistry();
        registry.setCacheTtlMillis(-5);

        assertThat(registry.getCacheTtlMillis()).isZero();
    }
}
