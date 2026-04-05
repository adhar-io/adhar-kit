package com.adhar.kit.resilience.vertx;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.resilience.api.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Vert.x adapter for resilience using Resilience4j circuit breakers.
 *
 * <p>This adapter provides circuit breaker functionality for Vert.x applications
 * using the framework-agnostic Resilience4j library. Since Vert.x does not have
 * a built-in dependency injection container, the {@link CircuitBreakerRegistry}
 * is created directly with configurable defaults.</p>
 *
 * <p>Resilience4j is a lightweight, non-blocking resilience library that integrates
 * well with Vert.x's reactive, event-driven architecture. All circuit breaker
 * operations are synchronous and non-blocking, making them safe for use on
 * Vert.x event loop threads.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization via constructor</li>
 *   <li><b>Resilience4j Native:</b> Uses Resilience4j directly without framework wrappers</li>
 *   <li><b>Configurable:</b> Custom circuit breaker configuration via constructor</li>
 *   <li><b>Metrics:</b> Built-in access to circuit breaker metrics</li>
 *   <li><b>Non-Blocking:</b> All operations are non-blocking and event loop safe</li>
 *   <li><b>Fallback Support:</b> Execute operations with automatic fallback on failure</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Default configuration
 * VertxResilienceAdapter resilience = new VertxResilienceAdapter();
 *
 * // Or with custom configuration
 * CircuitBreakerConfig config = CircuitBreakerConfig.custom()
 *     .failureRateThreshold(50)
 *     .waitDurationInOpenState(Duration.ofSeconds(30))
 *     .slidingWindowSize(100)
 *     .build();
 * VertxResilienceAdapter resilience = new VertxResilienceAdapter(config);
 *
 * // Use in a Verticle
 * public class OrderVerticle extends AbstractVerticle {
 *     private final VertxResilienceAdapter resilience = new VertxResilienceAdapter();
 *
 *     public void start() {
 *         vertx.eventBus().consumer("orders.create", msg -> {
 *             Order order = resilience.executeWithFallback(
 *                 "order-service",
 *                 () -> orderService.create(msg.body()),
 *                 () -> Order.pending(msg.body())
 *             );
 *             msg.reply(order);
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Monitoring Circuit Breaker State:</b></p>
 * <pre>{@code
 * // Check state
 * CircuitBreakerService.State state = resilience.getState("order-service");
 *
 * // Get metrics
 * CircuitBreakerService.Metrics metrics = resilience.getMetrics("order-service");
 * float failureRate = metrics.getFailureRate();
 * int successCalls = metrics.getNumberOfSuccessfulCalls();
 *
 * // Expose via Vert.x HTTP
 * router.get("/circuit-breaker/:name").handler(ctx -> {
 *     String name = ctx.pathParam("name");
 *     CircuitBreakerService.State state = resilience.getState(name);
 *     ctx.response().end(state.name());
 * });
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. Resilience4j's {@link CircuitBreakerRegistry} and
 * {@link CircuitBreaker} instances are thread-safe and can be used from any Vert.x
 * event loop or worker thread without additional synchronization.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see CircuitBreakerService
 * @see FrameworkAdapter
 * @see CircuitBreakerRegistry
 */
public class VertxResilienceAdapter implements FrameworkAdapter<CircuitBreakerService>, CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(VertxResilienceAdapter.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Constructs a new Vert.x resilience adapter with default circuit breaker configuration.
     *
     * <p>Uses Resilience4j's default configuration: 50% failure rate threshold,
     * 60 second wait duration in open state, and 100 call sliding window.</p>
     */
    public VertxResilienceAdapter() {
        this(CircuitBreakerConfig.ofDefaults());
    }

    /**
     * Constructs a new Vert.x resilience adapter with custom circuit breaker configuration.
     *
     * @param config the Resilience4j circuit breaker configuration
     * @throws NullPointerException if config is null
     */
    public VertxResilienceAdapter(CircuitBreakerConfig config) {
        Objects.requireNonNull(config, "CircuitBreakerConfig must not be null");
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
        log.info("Initialized Vert.x Resilience adapter with Resilience4j");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
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
                log.debug("Circuit breaker '{}' failed, executing fallback", name);
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
