package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HealthRegistry.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class HealthRegistryTest {

    private HealthRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HealthRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    void testRegisterIndicator() {
        TestHealthIndicator indicator = new TestHealthIndicator("test", Health.Status.UP);

        registry.register(indicator);

        assertEquals(indicator, registry.getIndicator("test"));
    }

    @Test
    void testUnregisterIndicator() {
        TestHealthIndicator indicator = new TestHealthIndicator("test", Health.Status.UP);
        registry.register(indicator);

        registry.unregister("test");

        assertNull(registry.getIndicator("test"));
    }

    @Test
    void testCheckHealth_AllHealthy() {
        registry.register(new TestHealthIndicator("db", Health.Status.UP));
        registry.register(new TestHealthIndicator("cache", Health.Status.UP));

        HealthResponse response = registry.checkHealth();

        assertEquals(Health.Status.UP, response.getStatus());
        assertEquals(2, response.getComponents().size());
        assertTrue(response.isHealthy());
    }

    @Test
    void testCheckHealth_OneUnhealthy() {
        registry.register(new TestHealthIndicator("db", Health.Status.UP));
        registry.register(new TestHealthIndicator("cache", Health.Status.DOWN));

        HealthResponse response = registry.checkHealth();

        assertEquals(Health.Status.DOWN, response.getStatus());
        assertEquals(2, response.getComponents().size());
        assertFalse(response.isHealthy());
    }

    @Test
    void testCheckHealth_Timeout() {
        // Indicator that takes too long
        registry.register(new SlowHealthIndicator("slow", 10000));

        HealthResponse response = registry.checkHealth(1000); // 1 second timeout

        Health slowHealth = response.getComponents().get("slow");
        assertNotNull(slowHealth);
        assertEquals(Health.Status.DOWN, slowHealth.getStatus());
    }

    @Test
    void testCheckHealth_Exception() {
        registry.register(new FailingHealthIndicator("failing"));

        HealthResponse response = registry.checkHealth();

        assertEquals(Health.Status.DOWN, response.getStatus());
        Health failingHealth = response.getComponents().get("failing");
        assertNotNull(failingHealth);
        assertEquals(Health.Status.DOWN, failingHealth.getStatus());
    }

    @Test
    void testGetIndicators() {
        registry.register(new TestHealthIndicator("db", Health.Status.UP));
        registry.register(new TestHealthIndicator("cache", Health.Status.UP));

        assertEquals(2, registry.getIndicators().size());
    }

    // Test helper classes

    static class TestHealthIndicator implements AdharHealthIndicator {
        private final String name;
        private final Health.Status status;

        TestHealthIndicator(String name, Health.Status status) {
            this.name = name;
            this.status = status;
        }

        @Override
        public Health check() {
            return Health.builder()
                .status(status)
                .component(name)
                .build();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class SlowHealthIndicator implements AdharHealthIndicator {
        private final String name;
        private final long delay;

        SlowHealthIndicator(String name, long delay) {
            this.name = name;
            this.delay = delay;
        }

        @Override
        public Health check() {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Health.up().component(name).build();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class FailingHealthIndicator implements AdharHealthIndicator {
        private final String name;

        FailingHealthIndicator(String name) {
            this.name = name;
        }

        @Override
        public Health check() {
            throw new RuntimeException("Simulated failure");
        }

        @Override
        public String getName() {
            return name;
        }
    }
}

