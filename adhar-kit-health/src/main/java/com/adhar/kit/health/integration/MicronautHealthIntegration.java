package com.adhar.kit.health.integration;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.registry.HealthRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Micronaut integration for Adhar Health.
 *
 * <p>Provides automatic health check configuration for Micronaut applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Factory
 * public class HealthConfig {
 *
 *     @Bean
 *     @Singleton
 *     public HealthRegistry healthRegistry(Collection<AdharHealthIndicator> indicators) {
 *         return MicronautHealthIntegration.createHealthRegistry(indicators);
 *     }
 * }
 *
 * // Custom indicator
 * @HealthIndicator(name = "payment")
 * @Singleton
 * public class PaymentHealthIndicator implements AdharHealthIndicator {
 *     @Override
 *     public Health check() {
 *         return Health.up().build();
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "payment";
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class MicronautHealthIntegration {

    /**
     * Creates health registry for Micronaut.
     *
     * @param indicators collection of health indicators
     * @return configured health registry
     */
    public static HealthRegistry createHealthRegistry(Collection<AdharHealthIndicator> indicators) {
        log.info("Creating health registry for Micronaut");

        HealthRegistry registry = new HealthRegistry();

        // Register all discovered indicators
        for (AdharHealthIndicator indicator : indicators) {
            registry.register(indicator);
        }

        log.info("Registered {} health indicators", indicators.size());

        return registry;
    }

    /**
     * Checks if Micronaut is available.
     *
     * @return true if Micronaut is on classpath
     */
    public static boolean isMicronautAvailable() {
        try {
            Class.forName("io.micronaut.runtime.Micronaut");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

