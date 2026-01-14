package com.adhar.kit.health.micronaut;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Micronaut implementation of Health Service.
 *
 * <p>Endpoints at:</p>
 * <ul>
 *   <li>/health - Overall health</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Singleton
@Requires(classes = io.micronaut.context.ApplicationContext.class)
public class MicronautHealthAdapter implements FrameworkAdapter<HealthService>, HealthService {

    private final Map<String, Supplier<HealthStatus>> healthChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> livenessChecks = new ConcurrentHashMap<>();
    private final Map<String, Supplier<HealthStatus>> readinessChecks = new ConcurrentHashMap<>();

    @Override
    public Framework getSupportedFramework() {
        return Framework.MICRONAUT;
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

