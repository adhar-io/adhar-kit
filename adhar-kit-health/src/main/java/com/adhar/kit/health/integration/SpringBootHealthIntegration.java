package com.adhar.kit.health.integration;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.registry.HealthRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/**
 * Spring Boot integration for Adhar Health.
 *
 * <p>Provides automatic health check configuration for Spring Boot applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Configuration
 * public class HealthConfig {
 *
 *     @Bean
 *     public HealthRegistry healthRegistry(Collection<AdharHealthIndicator> indicators) {
 *         return SpringBootHealthIntegration.createHealthRegistry(indicators);
 *     }
 *
 *     @GetMapping("/health")
 *     public HealthResponse health(HealthRegistry registry) {
 *         return registry.checkHealth();
 *     }
 * }
 *
 * // Custom indicator
 * @HealthIndicator(name = "payment")
 * @Component
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
public class SpringBootHealthIntegration {

    /**
     * Creates health registry for Spring Boot.
     *
     * @param indicators collection of health indicators
     * @return configured health registry
     */
    public static HealthRegistry createHealthRegistry(Collection<AdharHealthIndicator> indicators) {
        log.info("Creating health registry for Spring Boot");

        HealthRegistry registry = new HealthRegistry();

        // Register all discovered indicators
        for (AdharHealthIndicator indicator : indicators) {
            registry.register(indicator);
        }

        log.info("Registered {} health indicators", indicators.size());

        return registry;
    }

    /**
     * Checks if Spring Boot is available.
     *
     * @return true if Spring Boot is on classpath
     */
    public static boolean isSpringBootAvailable() {
        try {
            Class.forName("org.springframework.boot.SpringApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

