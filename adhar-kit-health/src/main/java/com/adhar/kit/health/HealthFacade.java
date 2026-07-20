package com.adhar.kit.health;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.registry.RegistryHealthService;
import com.adhar.kit.commons.framework.FrameworkDetector;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Universal Health facade that works across all frameworks.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * HealthFacade health = HealthFacade.getInstance();
 *
 * // Register database health check
 * health.registerReadinessCheck("database", () -> {
 *     try {
 *         dataSource.getConnection().close();
 *         return HealthService.HealthStatus.UP;
 *     } catch (Exception e) {
 *         return HealthService.HealthStatus.DOWN;
 *     }
 * });
 *
 * // Check overall health
 * HealthService.HealthStatus status = health.getHealth();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class HealthFacade implements HealthService {

    private static volatile HealthFacade instance;
    private final HealthService delegate;

    private HealthFacade() {
        this.delegate = createDelegate();
        log.info("Initialized HealthFacade with {} adapter", FrameworkDetector.detect());
    }

    public static HealthFacade getInstance() {
        if (instance == null) {
            synchronized (HealthFacade.class) {
                if (instance == null) {
                    instance = new HealthFacade();
                }
            }
        }
        return instance;
    }

    private HealthService createDelegate() {
        return switch (FrameworkDetector.detect()) {
            // Spring/Quarkus/Micronaut adapters are dependency-injection managed;
            // through the facade the registry-backed default service is used.
            case HELIDON -> adapterOrDefault("com.adhar.kit.health.helidon.HelidonHealthAdapter", "Helidon");
            case VERTX -> adapterOrDefault("com.adhar.kit.health.vertx.VertxHealthAdapter", "Vert.x");
            default -> createDefaultDelegate();
        };
    }

    /**
     * Attempts to create a framework adapter reflectively and falls back to the
     * registry-backed default service when the adapter is unavailable.
     */
    private HealthService adapterOrDefault(String adapterClassName, String frameworkName) {
        try {
            log.debug("Creating {} health adapter", frameworkName);
            var adapterClass = Class.forName(adapterClassName);
            return (HealthService) adapterClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("{} health adapter not available ({}); falling back to registry-backed health service",
                    frameworkName, e.getMessage());
            return createDefaultDelegate();
        }
    }

    private HealthService createDefaultDelegate() {
        log.debug("Creating registry-backed health service delegate");
        return new RegistryHealthService();
    }

    @Override
    public void registerHealthCheck(String name, Supplier<HealthStatus> healthCheck) {
        delegate.registerHealthCheck(name, healthCheck);
    }

    @Override
    public void registerLivenessCheck(String name, Supplier<HealthStatus> livenessCheck) {
        delegate.registerLivenessCheck(name, livenessCheck);
    }

    @Override
    public void registerReadinessCheck(String name, Supplier<HealthStatus> readinessCheck) {
        delegate.registerReadinessCheck(name, readinessCheck);
    }

    @Override
    public HealthStatus getHealth() {
        return delegate.getHealth();
    }

    @Override
    public HealthStatus getLiveness() {
        return delegate.getLiveness();
    }

    @Override
    public HealthStatus getReadiness() {
        return delegate.getReadiness();
    }

    @Override
    public Map<String, HealthStatus> getDetailedHealth() {
        return delegate.getDetailedHealth();
    }

    @Override
    public boolean unregisterHealthCheck(String name) {
        return delegate.unregisterHealthCheck(name);
    }

    @Override
    public boolean hasHealthCheck(String name) {
        return delegate.hasHealthCheck(name);
    }
}

