package com.adhar.kit.health.spring;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Spring Boot implementation of Health Service using Spring Boot Actuator.
 *
 * <p>This adapter integrates with Spring Boot's health check mechanism,
 * automatically exposing health endpoints at:</p>
 * <ul>
 *   <li>/actuator/health - Overall health</li>
 *   <li>/actuator/health/liveness - Liveness probe</li>
 *   <li>/actuator/health/readiness - Readiness probe</li>
 * </ul>
 *
 * <p><b>Spring Boot Configuration (application.yml):</b></p>
 * <pre>
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,info
 *   endpoint:
 *     health:
 *       show-details: always
 *       probes:
 *         enabled: true
 *   health:
 *     livenessState:
 *       enabled: true
 *     readinessState:
 *       enabled: true
 * </pre>
 *
 * <p><b>Kubernetes Deployment Example:</b></p>
 * <pre>
 * livenessProbe:
 *   httpGet:
 *     path: /actuator/health/liveness
 *     port: 8080
 *   initialDelaySeconds: 30
 *   periodSeconds: 10
 * readinessProbe:
 *   httpGet:
 *     path: /actuator/health/readiness
 *     port: 8080
 *   initialDelaySeconds: 10
 *   periodSeconds: 5
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
public class SpringHealthAdapter implements FrameworkAdapter<HealthService>, HealthService {

    /** Map of registered health checks */
    private final Map<String, Supplier<HealthStatus>> healthChecks = new ConcurrentHashMap<>();

    /** Map of registered liveness checks */
    private final Map<String, Supplier<HealthStatus>> livenessChecks = new ConcurrentHashMap<>();

    /** Map of registered readiness checks */
    private final Map<String, Supplier<HealthStatus>> readinessChecks = new ConcurrentHashMap<>();

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
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

        // Execute all health checks and collect results
        healthChecks.forEach((name, check) -> {
            try {
                HealthStatus status = check.get();
                details.put(name, status);
            } catch (Exception e) {
                log.error("Health check '{}' failed with exception", name, e);
                details.put(name, HealthStatus.DOWN);
            }
        });

        return details;
    }

    @Override
    public boolean unregisterHealthCheck(String name) {
        boolean removed = healthChecks.remove(name) != null;
        if (removed) {
            log.debug("Unregistered health check: {}", name);
        }
        return removed;
    }

    @Override
    public boolean hasHealthCheck(String name) {
        return healthChecks.containsKey(name);
    }

    /**
     * Executes all checks in the given map and returns overall status.
     *
     * <p>Returns UP only if all checks return UP, otherwise returns DOWN.</p>
     *
     * @param checks map of health checks to execute
     * @return overall health status
     */
    private HealthStatus executeChecks(Map<String, Supplier<HealthStatus>> checks) {
        if (checks.isEmpty()) {
            return HealthStatus.UP; // No checks registered, assume healthy
        }

        for (Map.Entry<String, Supplier<HealthStatus>> entry : checks.entrySet()) {
            try {
                HealthStatus status = entry.getValue().get();
                if (status != HealthStatus.UP) {
                    log.warn("Health check '{}' returned status: {}", entry.getKey(), status);
                    return HealthStatus.DOWN;
                }
            } catch (Exception e) {
                log.error("Health check '{}' threw exception", entry.getKey(), e);
                return HealthStatus.DOWN;
            }
        }

        return HealthStatus.UP;
    }

    /**
     * Creates a Spring Boot HealthIndicator for integration with Actuator.
     *
     * @return Spring Boot Health object
     */
    public Health getSpringHealth() {
        HealthStatus status = getHealth();
        Health.Builder builder = status == HealthStatus.UP ? Health.up() : Health.down();

        // Add details from all checks
        Map<String, HealthStatus> details = getDetailedHealth();
        details.forEach((name, checkStatus) ->
            builder.withDetail(name, checkStatus.name())
        );

        return builder.build();
    }
}

