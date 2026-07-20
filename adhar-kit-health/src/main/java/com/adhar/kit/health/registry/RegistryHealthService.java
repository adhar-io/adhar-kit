package com.adhar.kit.health.registry;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@link HealthService} implementation backed by a {@link HealthRegistry}.
 *
 * <p>This is the framework-agnostic default delegate used by
 * {@link com.adhar.kit.health.HealthFacade} so the facade works standalone on any
 * runtime — no framework adapter required. Registered checks are wrapped as
 * {@link AdharHealthIndicator}s and placed into the matching registry group:</p>
 *
 * <ul>
 *   <li>{@code registerHealthCheck} → {@code default} group</li>
 *   <li>{@code registerLivenessCheck} → {@code liveness} group</li>
 *   <li>{@code registerReadinessCheck} → {@code readiness} group</li>
 * </ul>
 *
 * <p>{@link #getHealth()} aggregates all indicators; {@link #getLiveness()} and
 * {@link #getReadiness()} aggregate only their group.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class RegistryHealthService implements HealthService {

    private final HealthRegistry registry;

    /**
     * Creates a service backed by a new registry.
     */
    public RegistryHealthService() {
        this(new HealthRegistry());
    }

    /**
     * Creates a service backed by an existing registry.
     *
     * @param registry backing health registry
     */
    public RegistryHealthService(HealthRegistry registry) {
        this.registry = registry;
    }

    /**
     * Gets the backing registry (for group registration, history, listeners, caching).
     *
     * @return backing health registry
     */
    public HealthRegistry getRegistry() {
        return registry;
    }

    @Override
    public void registerHealthCheck(String name, Supplier<HealthStatus> healthCheck) {
        registry.register(supplierIndicator(name, healthCheck), HealthRegistry.DEFAULT_GROUP);
    }

    @Override
    public void registerLivenessCheck(String name, Supplier<HealthStatus> livenessCheck) {
        registry.register(supplierIndicator(name, livenessCheck), HealthRegistry.LIVENESS_GROUP);
    }

    @Override
    public void registerReadinessCheck(String name, Supplier<HealthStatus> readinessCheck) {
        registry.register(supplierIndicator(name, readinessCheck), HealthRegistry.READINESS_GROUP);
    }

    @Override
    public HealthStatus getHealth() {
        return toHealthStatus(registry.checkHealth().getStatus());
    }

    @Override
    public HealthStatus getLiveness() {
        return toHealthStatus(registry.checkGroup(HealthRegistry.LIVENESS_GROUP).getStatus());
    }

    @Override
    public HealthStatus getReadiness() {
        return toHealthStatus(registry.checkGroup(HealthRegistry.READINESS_GROUP).getStatus());
    }

    @Override
    public Map<String, HealthStatus> getDetailedHealth() {
        Map<String, HealthStatus> details = new ConcurrentHashMap<>();
        registry.checkHealth().getComponents()
                .forEach((name, health) -> details.put(name, toHealthStatus(health.getStatus())));
        return details;
    }

    @Override
    public boolean unregisterHealthCheck(String name) {
        return registry.unregister(name);
    }

    @Override
    public boolean hasHealthCheck(String name) {
        return registry.getIndicator(name) != null;
    }

    /**
     * Maps a registry status to the {@link HealthService.HealthStatus} enum.
     *
     * <p>OUT_OF_SERVICE maps to DOWN so traffic is not routed to a
     * deliberately-disabled instance.</p>
     *
     * @param status registry status
     * @return service-level health status
     */
    static HealthStatus toHealthStatus(Health.Status status) {
        return switch (status) {
            case UP -> HealthStatus.UP;
            case UNKNOWN -> HealthStatus.UNKNOWN;
            case DOWN, OUT_OF_SERVICE -> HealthStatus.DOWN;
        };
    }

    private static AdharHealthIndicator supplierIndicator(String name, Supplier<HealthStatus> check) {
        return new AdharHealthIndicator() {
            @Override
            public Health check() {
                HealthStatus status = check.get();
                if (status == null) {
                    return Health.unknown().component(name).build();
                }
                return switch (status) {
                    case UP -> Health.up().component(name).build();
                    case DOWN -> Health.down().component(name).build();
                    case UNKNOWN -> Health.unknown().component(name).build();
                };
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }
}
