package com.adhar.kit.resilience;

import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Universal Circuit Breaker facade that works across all frameworks.
 * Auto-detects the framework and delegates to the appropriate adapter.
 *
 * <p>Framework-specific adapters are loaded via reflection to avoid compile-time
 * dependencies on framework-specific modules that are excluded during compilation.</p>
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
     * Creates a facade around an explicit {@link CircuitBreakerService} delegate.
     * This is how DI containers (e.g. the Spring starter, which injects a
     * {@code SpringCircuitBreakerAdapter}) construct a working facade - the
     * no-arg singleton path cannot build framework adapters that themselves need
     * injected infrastructure like a {@code CircuitBreakerRegistry}.
     *
     * @param delegate the circuit-breaker implementation to delegate to
     */
    public CircuitBreakerFacade(CircuitBreakerService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        log.info("Initialized CircuitBreakerFacade with injected {} delegate",
                delegate.getClass().getSimpleName());
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
            case QUARKUS -> createAdapterByReflection(
                    "com.adhar.kit.resilience.quarkus.QuarkusCircuitBreakerAdapter");
            case MICRONAUT -> createAdapterByReflection(
                    "com.adhar.kit.resilience.micronaut.MicronautCircuitBreakerAdapter");
            case HELIDON -> createAdapterByReflection(
                    "com.adhar.kit.resilience.helidon.HelidonResilienceAdapter");
            case VERTX -> createAdapterByReflection(
                    "com.adhar.kit.resilience.vertx.VertxResilienceAdapter");
            default -> throw new IllegalStateException(
                    "Unsupported framework: " + FrameworkDetector.detect()
            );
        };
    }

    /**
     * Creates a framework-specific adapter via reflection.
     * This avoids compile-time dependencies on excluded framework adapter classes.
     *
     * @param className fully qualified class name of the adapter
     * @return the adapter instance as a CircuitBreakerService
     */
    private CircuitBreakerService createAdapterByReflection(String className) {
        try {
            Class<?> adapterClass = Class.forName(className);
            return (CircuitBreakerService) adapterClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create adapter: " + className + ". "
                    + "Ensure the adapter class is on the classpath.", e);
        }
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
                    + "Ensure a supported framework (Quarkus, Micronaut, Helidon, Vert.x) is on the classpath, "
                    + "or use Spring's @Autowired SpringCircuitBreakerAdapter directly.");
        }
    }
}
