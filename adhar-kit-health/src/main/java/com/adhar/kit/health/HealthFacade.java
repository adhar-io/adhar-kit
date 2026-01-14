package com.adhar.kit.health;

import com.adhar.kit.health.api.HealthService;
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
            case SPRING_BOOT -> createSpringAdapter();
            case QUARKUS -> createQuarkusAdapter();
            case MICRONAUT -> createMicronautAdapter();
            default -> throw new IllegalStateException(
                    "Unsupported framework for health: " + FrameworkDetector.detect()
            );
        };
    }

    private HealthService createSpringAdapter() {
        // Spring adapter should be obtained via dependency injection, not through facade
        throw new UnsupportedOperationException(
                "Spring adapter should be injected via @Autowired SpringHealthAdapter, not accessed through HealthFacade"
        );
    }

    private HealthService createQuarkusAdapter() {
        throw new UnsupportedOperationException("Quarkus adapter initialization from facade not yet supported");
    }

    private HealthService createMicronautAdapter() {
        throw new UnsupportedOperationException("Micronaut adapter initialization from facade not yet supported");
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

