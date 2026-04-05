package com.adhar.kit.health.helidon;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Helidon-specific adapter for health checks.
 *
 * <p>This adapter provides a standalone health check implementation for Helidon SE and MP
 * applications. It maintains its own registry of health, liveness, and readiness checks
 * and can be integrated with Helidon's built-in health check endpoints.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>Three-tier Checks:</b> Supports health, liveness, and readiness checks</li>
 *   <li><b>Thread Safe:</b> Uses ConcurrentHashMap for concurrent check registration</li>
 *   <li><b>Helidon Integration:</b> Can be wired to Helidon's /health, /health/live, /health/ready endpoints</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * HelidonHealthAdapter health = new HelidonHealthAdapter();
 *
 * // Register checks
 * health.registerLivenessCheck("app", () -> HealthStatus.UP);
 * health.registerReadinessCheck("database", () -> {
 *     return isDatabaseConnected() ? HealthStatus.UP : HealthStatus.DOWN;
 * });
 *
 * // Integrate with Helidon WebServer
 * WebServer.builder()
 *     .routing(routing -> routing
 *         .get("/health", (req, res) -> res.send(health.getHealth().name()))
 *         .get("/health/live", (req, res) -> res.send(health.getLiveness().name()))
 *         .get("/health/ready", (req, res) -> res.send(health.getReadiness().name()))
 *     )
 *     .build()
 *     .start();
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. All check registries use {@link ConcurrentHashMap},
 * and individual health check suppliers should be implemented in a thread-safe manner.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see HealthService
 * @see FrameworkAdapter
 */
public class HelidonHealthAdapter implements FrameworkAdapter<HealthService>, HealthService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(HelidonHealthAdapter.class);

    private final Map<String, Supplier<HealthStatus>> healthChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> livenessChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> readinessChecks = new ConcurrentHashMap<>();

    /**
     * Constructs a HelidonHealthAdapter.
     */
    public HelidonHealthAdapter() {
        log.debug("Initialized HelidonHealthAdapter");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    @Override
    public HealthService getService() {
        return this;
    }

    @Override
    public void registerHealthCheck(String name, Supplier<HealthStatus> healthCheck) {
        healthChecks.put(name, healthCheck);
        log.debug("Registered health check: {}", name);
    }

    @Override
    public void registerLivenessCheck(String name, Supplier<HealthStatus> livenessCheck) {
        livenessChecks.put(name, livenessCheck);
        log.debug("Registered liveness check: {}", name);
    }

    @Override
    public void registerReadinessCheck(String name, Supplier<HealthStatus> readinessCheck) {
        readinessChecks.put(name, readinessCheck);
        log.debug("Registered readiness check: {}", name);
    }

    @Override
    public HealthStatus getHealth() {
        return executeChecks(healthChecks);
    }

    @Override
    public HealthStatus getLiveness() {
        return executeChecks(livenessChecks);
    }

    @Override
    public HealthStatus getReadiness() {
        return executeChecks(readinessChecks);
    }

    @Override
    public Map<String, HealthStatus> getDetailedHealth() {
        Map<String, HealthStatus> details = new ConcurrentHashMap<>();
        healthChecks.forEach((name, check) -> {
            try {
                details.put(name, check.get());
            } catch (Exception e) {
                log.error("Health check '{}' failed", name, e);
                details.put(name, HealthStatus.DOWN);
            }
        });
        return details;
    }

    @Override
    public boolean unregisterHealthCheck(String name) {
        return healthChecks.remove(name) != null;
    }

    @Override
    public boolean hasHealthCheck(String name) {
        return healthChecks.containsKey(name);
    }

    private HealthStatus executeChecks(Map<String, Supplier<HealthStatus>> checks) {
        if (checks.isEmpty()) return HealthStatus.UP;

        for (Map.Entry<String, Supplier<HealthStatus>> entry : checks.entrySet()) {
            try {
                if (entry.getValue().get() != HealthStatus.UP) {
                    return HealthStatus.DOWN;
                }
            } catch (Exception e) {
                log.error("Check '{}' failed", entry.getKey(), e);
                return HealthStatus.DOWN;
            }
        }
        return HealthStatus.UP;
    }
}
