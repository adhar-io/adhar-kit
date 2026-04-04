package com.adhar.kit.resilience;

import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import com.adhar.kit.resilience.micronaut.MicronautCircuitBreakerAdapter;
import com.adhar.kit.resilience.quarkus.QuarkusCircuitBreakerAdapter;
import com.adhar.kit.resilience.spring.SpringCircuitBreakerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Universal Circuit Breaker facade that works across all frameworks.
 * Auto-detects the framework and delegates to the appropriate adapter.
 *
 * Usage:
 * <pre>
 * CircuitBreakerFacade cb = CircuitBreakerFacade.getInstance();
 * String result = cb.execute("payment", () -> callExternalService());
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class CircuitBreakerFacade implements CircuitBreakerService {

    private static volatile CircuitBreakerFacade instance;
    private final CircuitBreakerService delegate;

    private CircuitBreakerFacade() {
        this.delegate = createDelegate();
        log.info("Initialized CircuitBreakerFacade with {} adapter",
                FrameworkDetector.detect());
    }

    /**
     * Get singleton instance.
     *
     * @return facade instance
     */
    public static CircuitBreakerFacade getInstance() {
        if (instance == null) {
            synchronized (CircuitBreakerFacade.class) {
                if (instance == null) {
                    instance = new CircuitBreakerFacade();
                }
            }
        }
        return instance;
    }

    private CircuitBreakerService createDelegate() {
        return switch (FrameworkDetector.detect()) {
            case SPRING_BOOT -> createSpringAdapter();
            case QUARKUS -> new QuarkusCircuitBreakerAdapter();
            case MICRONAUT -> new MicronautCircuitBreakerAdapter();
            default -> throw new IllegalStateException(
                    "Unsupported framework: " + FrameworkDetector.detect()
            );
        };
    }

    private CircuitBreakerService createSpringAdapter() {
        // Spring adapter should be obtained via dependency injection, not through facade
        throw new UnsupportedOperationException(
                "Spring adapter should be injected via @Autowired SpringCircuitBreakerAdapter, not accessed through CircuitBreakerFacade"
        );
    }

    @Override
    public <T> T execute(String name, Supplier<T> supplier) {
        requireDelegate();
        return delegate.execute(name, supplier);
    }

    @Override
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        requireDelegate();
        return delegate.executeWithFallback(name, supplier, fallback);
    }

    @Override
    public State getState(String name) {
        requireDelegate();
        return delegate.getState(name);
    }

    @Override
    public Metrics getMetrics(String name) {
        requireDelegate();
        return delegate.getMetrics(name);
    }

    @Override
    public void reset(String name) {
        requireDelegate();
        delegate.reset(name);
    }

    private void requireDelegate() {
        if (delegate == null) {
            throw new IllegalStateException(
                    "CircuitBreakerFacade delegate is not initialized. "
                    + "Ensure a supported framework (Quarkus, Micronaut) is on the classpath, "
                    + "or use Spring's @Autowired SpringCircuitBreakerAdapter directly.");
        }
    }
}

