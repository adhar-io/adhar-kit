package com.adhar.kit.resilience.helidon;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Helidon-specific adapter for resilience using Resilience4j.
 *
 * <p>This adapter provides a standalone circuit breaker implementation for Helidon SE
 * and MP applications. It uses Resilience4j directly, which is framework-agnostic
 * and works without any DI container.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Standalone:</b> No DI annotations; compatible with Helidon SE (no container)</li>
 *   <li><b>Resilience4j:</b> Uses the industry-standard circuit breaker library</li>
 *   <li><b>Configurable:</b> Supports custom failure rate thresholds and sliding window sizes</li>
 *   <li><b>Fallback Support:</b> Built-in fallback execution on circuit breaker failure</li>
 *   <li><b>Thread Safe:</b> Resilience4j CircuitBreaker is inherently thread-safe</li>
 * </ul>
 *
 * <p><b>Usage with Helidon SE:</b></p>
 * <pre>{@code
 * HelidonResilienceAdapter resilience = new HelidonResilienceAdapter();
 *
 * // Execute with circuit breaker
 * String result = resilience.execute("external-api", () -> {
 *     return httpClient.get("https://api.example.com/data");
 * });
 *
 * // Execute with fallback
 * String result = resilience.executeWithFallback("external-api",
 *     () -> httpClient.get("https://api.example.com/data"),
 *     () -> "cached-default-value"
 * );
 * }</pre>
 *
 * <p><b>Custom Configuration:</b></p>
 * <pre>{@code
 * HelidonResilienceAdapter resilience = new HelidonResilienceAdapter(
 *     50.0f,  // failure rate threshold
 *     100,    // sliding window size
 *     Duration.ofSeconds(60),  // wait duration in open state
 *     10      // permitted calls in half-open state
 * );
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Resilience4j's {@link CircuitBreakerRegistry} and
 * individual {@link CircuitBreaker} instances handle concurrent access internally.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see CircuitBreakerService
 * @see FrameworkAdapter
 * @see CircuitBreakerRegistry
 */
public class HelidonResilienceAdapter implements FrameworkAdapter<CircuitBreakerService>, CircuitBreakerService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(HelidonResilienceAdapter.class);

    /**
     * Resilience4j circuit breaker registry managing all named circuit breakers.
     */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Constructs a HelidonResilienceAdapter with custom circuit breaker configuration.
     *
     * @param failureRateThreshold the failure rate threshold in percentage (0-100)
     * @param slidingWindowSize the sliding window size for failure rate calculation
     * @param waitDurationInOpenState how long to wait before transitioning from OPEN to HALF_OPEN
     * @param permittedNumberOfCallsInHalfOpenState number of calls permitted in HALF_OPEN state
     */
    public HelidonResilienceAdapter(float failureRateThreshold, int slidingWindowSize,
                                    Duration waitDurationInOpenState,
                                    int permittedNumberOfCallsInHalfOpenState) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slidingWindowSize(slidingWindowSize)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .build();

        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        log.info("Initialized Helidon Circuit Breaker adapter with custom configuration");
    }

    /**
     * Constructs a HelidonResilienceAdapter with default Resilience4j configuration.
     */
    public HelidonResilienceAdapter() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
        log.info("Initialized Helidon Circuit Breaker adapter with default configuration");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.HELIDON;
    }

    @Override
    public CircuitBreakerService getService() {
        return this;
    }

    @Override
    public <T> T execute(String name, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return circuitBreaker.executeSupplier(supplier);
    }

    @Override
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return circuitBreaker.executeSupplier(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                log.debug("Circuit breaker {} failed, executing fallback", name);
                return fallback.get();
            }
        });
    }

    @Override
    public State getState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return mapState(circuitBreaker.getState());
    }

    @Override
    public Metrics getMetrics(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        CircuitBreaker.Metrics resilience4jMetrics = circuitBreaker.getMetrics();

        return new Metrics() {
            @Override
            public float getFailureRate() {
                return resilience4jMetrics.getFailureRate();
            }

            @Override
            public int getNumberOfSuccessfulCalls() {
                return (int) resilience4jMetrics.getNumberOfSuccessfulCalls();
            }

            @Override
            public int getNumberOfFailedCalls() {
                return (int) resilience4jMetrics.getNumberOfFailedCalls();
            }

            @Override
            public int getNumberOfNotPermittedCalls() {
                return (int) resilience4jMetrics.getNumberOfNotPermittedCalls();
            }
        };
    }

    @Override
    public void reset(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        circuitBreaker.reset();
    }

    private State mapState(CircuitBreaker.State resilience4jState) {
        return switch (resilience4jState) {
            case CLOSED -> State.CLOSED;
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
            case DISABLED -> State.DISABLED;
            case FORCED_OPEN -> State.FORCED_OPEN;
            default -> State.CLOSED;
        };
    }
}
