package com.adhar.kit.health.indicator;

import com.adhar.kit.health.model.Health;

/**
 * Interface for health indicators.
 *
 * <p>Implement this interface to create custom health checks.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @HealthIndicator(name = "custom-service")
 * public class CustomServiceHealthIndicator implements AdharHealthIndicator {
 *
 *     @Override
 *     public Health check() {
 *         boolean isHealthy = checkServiceHealth();
 *
 *         if (isHealthy) {
 *             return Health.up()
 *                 .withDetail("service", "operational")
 *                 .build();
 *         } else {
 *             return Health.down()
 *                 .withDetail("service", "unavailable")
 *                 .build();
 *         }
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "custom-service";
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface AdharHealthIndicator {

    /**
     * Performs health check.
     *
     * @return health status
     */
    Health check();

    /**
     * Gets indicator name.
     *
     * @return indicator name
     */
    String getName();
}

