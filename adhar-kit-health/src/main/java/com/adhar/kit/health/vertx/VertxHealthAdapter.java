package com.adhar.kit.health.vertx;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Vert.x adapter for health checks.
 *
 * <p>This adapter provides a standalone health check implementation for Vert.x
 * applications. Since Vert.x does not have a built-in dependency injection container,
 * this adapter is instantiated directly and manages health checks internally using
 * thread-safe concurrent maps.</p>
 *
 * <p>While Vert.x provides its own async health check mechanism via
 * {@code vertx-health-check}, this adapter implements the Adhar Kit {@link HealthService}
 * interface to provide a unified health check API across all supported frameworks.
 * The adapter can be used alongside Vert.x's native health checks.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization, no annotations needed</li>
 *   <li><b>Liveness/Readiness:</b> Separate check categories for Kubernetes probes</li>
 *   <li><b>Thread-Safe:</b> ConcurrentHashMap-backed check registry</li>
 *   <li><b>Detailed Health:</b> Per-check status reporting</li>
 *   <li><b>Dynamic Registration:</b> Register and unregister checks at runtime</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * VertxHealthAdapter health = new VertxHealthAdapter();
 *
 * // Register checks
 * health.registerLivenessCheck("event-loop", () ->
 *     vertx.nettyEventLoopGroup().isShutdown()
 *         ? HealthStatus.DOWN : HealthStatus.UP);
 *
 * health.registerReadinessCheck("database", () -> {
 *     // check database connectivity
 *     return dbConnected ? HealthStatus.UP : HealthStatus.DOWN;
 * });
 *
 * // Expose via Vert.x HTTP server
 * router.get("/health").handler(ctx -> {
 *     HealthStatus status = health.getHealth();
 *     ctx.response()
 *         .setStatusCode(status == HealthStatus.UP ? 200 : 503)
 *         .end(status.name());
 * });
 *
 * router.get("/health/live").handler(ctx -> {
 *     HealthStatus status = health.getLiveness();
 *     ctx.response()
 *         .setStatusCode(status == HealthStatus.UP ? 200 : 503)
 *         .end(status.name());
 * });
 *
 * router.get("/health/ready").handler(ctx -> {
 *     HealthStatus status = health.getReadiness();
 *     ctx.response()
 *         .setStatusCode(status == HealthStatus.UP ? 200 : 503)
 *         .end(status.name());
 * });
 * }</pre>
 *
 * <p><b>Integration with Vert.x Health Checks:</b></p>
 * <pre>{@code
 * // Use alongside native Vert.x health checks
 * HealthChecks vertxHealthChecks = HealthChecks.create(vertx);
 * vertxHealthChecks.register("adhar-kit", promise -> {
 *     HealthStatus status = health.getHealth();
 *     if (status == HealthStatus.UP) {
 *         promise.complete(Status.OK());
 *     } else {
 *         promise.complete(Status.KO());
 *     }
 * });
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. All internal maps use {@link ConcurrentHashMap},
 * making it safe to register/unregister checks and query health status from any
 * Vert.x event loop or worker thread.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see HealthService
 * @see FrameworkAdapter
 */
public class VertxHealthAdapter implements FrameworkAdapter<HealthService>, HealthService {

    private static final Logger log = LoggerFactory.getLogger(VertxHealthAdapter.class);

    private final Map<String, Supplier<HealthStatus>> healthChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> livenessChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> readinessChecks = new ConcurrentHashMap<>();

    /**
     * Constructs a new Vert.x health adapter.
     *
     * <p>No external dependencies are required. The adapter maintains internal
     * registries for health, liveness, and readiness checks.</p>
     */
    public VertxHealthAdapter() {
        log.info("Initialized Vert.x Health adapter");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
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
        if (checks.isEmpty()) {
            return HealthStatus.UP;
        }

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
