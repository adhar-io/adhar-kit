package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional tests for {@link HealthRegistry} covering shutdown and lookup edge cases.
 */
class HealthRegistryExtraTest {

    private static AdharHealthIndicator upIndicator(String name) {
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
    void getIndicator_returnsNullWhenAbsent() {
        HealthRegistry registry = new HealthRegistry();
        try {
            assertThat(registry.getIndicator("missing")).isNull();
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void checkHealth_withExplicitTimeout_aggregatesResults() {
        HealthRegistry registry = new HealthRegistry();
        registry.register(upIndicator("a"));
        registry.register(upIndicator("b"));
        try {
            HealthResponse response = registry.checkHealth(2000);

            assertThat(response.getStatus()).isEqualTo(Health.Status.UP);
            assertThat(response.getComponents()).containsOnlyKeys("a", "b");
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void checkHealth_whenInterruptedDuringCollection_returnsDown() {
        HealthRegistry registry = new HealthRegistry();
        registry.register(slowIndicator("slow", 2000));
        try {
            // Interrupt so Future.get() throws InterruptedException, hitting the generic catch.
            Thread.currentThread().interrupt();
            HealthResponse response = registry.checkHealth(5000);

            assertThat(response.getStatus()).isEqualTo(Health.Status.DOWN);
            assertThat(response.getComponents().get("slow").getStatus()).isEqualTo(Health.Status.DOWN);
        } finally {
            Thread.interrupted(); // clear any residual interrupt status
            registry.shutdown();
        }
    }

    @Test
    void shutdown_whenInterruptedWithActiveTask_handlesInterruptedException() {
        HealthRegistry registry = new HealthRegistry();
        registry.register(slowIndicator("slow", 10_000));
        // Start a long-running task and let the collection time out, leaving the worker busy.
        registry.checkHealth(200);

        // Interrupt so awaitTermination throws InterruptedException, exercising shutdown's catch.
        Thread.currentThread().interrupt();
        registry.shutdown();

        assertThat(Thread.interrupted()).isTrue(); // branch re-sets and we clear it
    }

    private static AdharHealthIndicator slowIndicator(String name, long sleepMillis) {
        return new AdharHealthIndicator() {
            @Override
            public Health check() {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Health.up().component(name).build();
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}
