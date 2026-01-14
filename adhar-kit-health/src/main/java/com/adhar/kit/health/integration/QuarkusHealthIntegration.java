package com.adhar.kit.health.integration;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.registry.HealthRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Quarkus integration for Adhar Health.
 *
 * <p>Provides automatic health check configuration for Quarkus applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class HealthConfig {
 *
 *     @Produces
 *     @Singleton
 *     public HealthRegistry healthRegistry(Instance<AdharHealthIndicator> indicators) {
 *         return QuarkusHealthIntegration.createHealthRegistry(
 *             StreamSupport.stream(indicators.spliterator(), false)
 *                 .collect(Collectors.toList())
 *         );
 *     }
 * }
 *
 * // Custom indicator
 * @HealthIndicator(name = "payment")
 * @ApplicationScoped
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
public class QuarkusHealthIntegration {

    /**
     * Creates health registry for Quarkus.
     *
     * @param indicators collection of health indicators
     * @return configured health registry
     */
    public static HealthRegistry createHealthRegistry(Collection<AdharHealthIndicator> indicators) {
        log.info("Creating health registry for Quarkus");

        HealthRegistry registry = new HealthRegistry();

        // Register all discovered indicators
        for (AdharHealthIndicator indicator : indicators) {
            registry.register(indicator);
        }

        log.info("Registered {} health indicators", indicators.size());

        return registry;
    }

    /**
     * Checks if Quarkus is available.
     *
     * @return true if Quarkus is on classpath
     */
    public static boolean isQuarkusAvailable() {
        try {
            Class.forName("io.quarkus.runtime.Quarkus");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

