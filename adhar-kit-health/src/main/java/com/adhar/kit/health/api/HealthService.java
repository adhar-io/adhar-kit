package com.adhar.kit.health.api;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Framework-agnostic Health Check Service API.
 *
 * <p>This interface provides a unified health check abstraction that works across
 * Spring Boot, Quarkus, and Micronaut frameworks. It supports registering custom
 * health indicators for monitoring application and infrastructure health.</p>
 *
 * <p><b>Common Use Cases:</b></p>
 * <ul>
 *   <li>Database connectivity checks</li>
 *   <li>External service availability</li>
 *   <li>Disk space monitoring</li>
 *   <li>Cache connectivity</li>
 *   <li>Message queue connectivity</li>
 *   <li>Custom business logic health</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * HealthService health = HealthFacade.getInstance();
 *
 * // Register liveness check
 * health.registerLivenessCheck("database", () ->
 *     databaseIsConnected() ? HealthStatus.UP : HealthStatus.DOWN);
 *
 * // Register readiness check
 * health.registerReadinessCheck("external-api", () ->
 *     apiIsReachable() ? HealthStatus.UP : HealthStatus.DOWN);
 *
 * // Get overall health
 * HealthStatus status = health.getHealth();
 *
 * // Get detailed health with all checks
 * Map<String, HealthStatus> details = health.getDetailedHealth();
 * }</pre>
 *
 * <p><b>Kubernetes Integration:</b></p>
 * <ul>
 *   <li><b>Liveness:</b> Should the container be restarted?</li>
 *   <li><b>Readiness:</b> Should traffic be routed to this instance?</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.dbs.adhar.health.HealthFacade
 */
public interface HealthService {

    /**
     * Health status enumeration.
     */
    enum HealthStatus {
        /** Service is healthy and operational */
        UP,

        /** Service is unhealthy or non-operational */
        DOWN,

        /** Service health is unknown or indeterminate */
        UNKNOWN
    }

    /**
     * Registers a general health check.
     *
     * <p>This check contributes to the overall application health status.</p>
     *
     * @param name unique name for the health check
     * @param healthCheck supplier that returns the health status
     */
    void registerHealthCheck(String name, Supplier<HealthStatus> healthCheck);

    /**
     * Registers a liveness check.
     *
     * <p>Liveness checks indicate whether the application is running.
     * If liveness checks fail, the container/pod should be restarted.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * health.registerLivenessCheck("application", () -> {
     *     // Check if application can continue to run
     *     return threadPoolIsHealthy() ? HealthStatus.UP : HealthStatus.DOWN;
     * });
     * }</pre>
     *
     * @param name unique name for the liveness check
     * @param livenessCheck supplier that returns the liveness status
     */
    void registerLivenessCheck(String name, Supplier<HealthStatus> livenessCheck);

    /**
     * Registers a readiness check.
     *
     * <p>Readiness checks indicate whether the application is ready to serve traffic.
     * If readiness checks fail, traffic should not be routed to this instance.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * health.registerReadinessCheck("database", () -> {
     *     // Check if database is accessible
     *     try {
     *         dataSource.getConnection().close();
     *         return HealthStatus.UP;
     *     } catch (Exception e) {
     *         return HealthStatus.DOWN;
     *     }
     * });
     * }</pre>
     *
     * @param name unique name for the readiness check
     * @param readinessCheck supplier that returns the readiness status
     */
    void registerReadinessCheck(String name, Supplier<HealthStatus> readinessCheck);

    /**
     * Gets the overall health status of the application.
     *
     * <p>Returns UP only if all health checks pass, otherwise returns DOWN.</p>
     *
     * @return the overall health status
     */
    HealthStatus getHealth();

    /**
     * Gets the liveness status of the application.
     *
     * <p>Returns UP only if all liveness checks pass.</p>
     *
     * @return the liveness status
     */
    HealthStatus getLiveness();

    /**
     * Gets the readiness status of the application.
     *
     * <p>Returns UP only if all readiness checks pass.</p>
     *
     * @return the readiness status
     */
    HealthStatus getReadiness();

    /**
     * Gets detailed health information including all registered checks.
     *
     * <p>The returned map contains the status of each individual health check.</p>
     *
     * @return map of health check names to their status
     */
    Map<String, HealthStatus> getDetailedHealth();

    /**
     * Unregisters a health check.
     *
     * @param name the name of the health check to remove
     * @return true if the check was removed, false if it didn't exist
     */
    boolean unregisterHealthCheck(String name);

    /**
     * Checks if a health check is registered.
     *
     * @param name the name of the health check
     * @return true if the check exists, false otherwise
     */
    boolean hasHealthCheck(String name);
}

