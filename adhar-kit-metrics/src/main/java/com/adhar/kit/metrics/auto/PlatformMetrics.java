package com.adhar.kit.metrics.auto;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralized platform metrics collector that provides pre-built metric names and
 * auto-records common metrics across all Adhar Kit modules.
 * <p>
 * This class serves as both a Spring bean and a static singleton, enabling metrics
 * recording from any context -- whether inside a Spring-managed bean or from a
 * static utility method.
 * </p>
 *
 * <p><b>Metric Naming Convention:</b></p>
 * <p>All metrics follow the pattern {@code adhar.<module>.<metric>}, for example:</p>
 * <ul>
 *   <li>{@code adhar.persistence.query.duration}</li>
 *   <li>{@code adhar.cache.access}</li>
 *   <li>{@code adhar.http.request.duration}</li>
 *   <li>{@code adhar.resilience.circuit_breaker.state}</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Via static singleton
 * PlatformMetrics.getInstance().recordQueryLatency("findById", "Order", 45, true);
 *
 * // Via injected bean
 * @Autowired PlatformMetrics platformMetrics;
 * platformMetrics.recordCacheAccess("orders", true);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class PlatformMetrics {

    private static final Logger log = LoggerFactory.getLogger(PlatformMetrics.class);

    private static volatile PlatformMetrics instance;

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    /**
     * Constructs a new PlatformMetrics instance backed by the given MeterRegistry.
     * Also sets this instance as the static singleton.
     *
     * @param registry the Micrometer MeterRegistry to use for recording metrics
     */
    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
        instance = this;
        log.info("PlatformMetrics initialized with registry: {}", registry.getClass().getSimpleName());
    }

    /**
     * Returns the static singleton instance of PlatformMetrics.
     * <p>
     * The instance is set when the Spring bean is created. If called before Spring
     * context initialization, this method returns {@code null}.
     * </p>
     *
     * @return the singleton PlatformMetrics instance, or null if not yet initialized
     */
    public static PlatformMetrics getInstance() {
        return instance;
    }

    /**
     * Returns the underlying MeterRegistry.
     *
     * @return the MeterRegistry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    // -------------------------------------------------------------------------
    // Persistence Metrics
    // -------------------------------------------------------------------------

    /**
     * Records the latency of a database query operation.
     *
     * @param operation  the query operation name (e.g., "findById", "findAll")
     * @param entity     the entity type name (e.g., "Order", "User")
     * @param durationMs execution time in milliseconds
     * @param success    whether the operation succeeded
     */
    public void recordQueryLatency(String operation, String entity, long durationMs, boolean success) {
        Timer.builder("adhar.persistence.query.duration")
                .description("Database query latency")
                .tag("operation", operation)
                .tag("entity", entity)
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.persistence.query.count")
                .description("Total database query count")
                .tag("operation", operation)
                .tag("entity", entity)
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();
    }

    /**
     * Records the duration of a database transaction.
     *
     * @param durationMs execution time in milliseconds
     * @param committed  whether the transaction was committed (true) or rolled back (false)
     */
    public void recordTransactionDuration(long durationMs, boolean committed) {
        Timer.builder("adhar.persistence.transaction.duration")
                .description("Database transaction duration")
                .tag("outcome", committed ? "committed" : "rolled_back")
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.persistence.transaction.count")
                .description("Total database transaction count")
                .tag("outcome", committed ? "committed" : "rolled_back")
                .register(registry)
                .increment();
    }

    /**
     * Records current connection pool statistics.
     *
     * @param active number of active connections
     * @param idle   number of idle connections
     * @param total  total number of connections in the pool
     */
    public void recordConnectionPoolStats(int active, int idle, int total) {
        registerGauge("adhar.persistence.connections.active", "Active DB connections", active);
        registerGauge("adhar.persistence.connections.idle", "Idle DB connections", idle);
        registerGauge("adhar.persistence.connections.total", "Total DB connections", total);
    }

    // -------------------------------------------------------------------------
    // Cache Metrics
    // -------------------------------------------------------------------------

    /**
     * Records a cache access (hit or miss).
     *
     * @param cacheName the name of the cache
     * @param hit       whether the access was a cache hit
     */
    public void recordCacheAccess(String cacheName, boolean hit) {
        Counter.builder("adhar.cache.access")
                .description("Cache access count")
                .tag("cache", cacheName)
                .tag("result", hit ? "hit" : "miss")
                .register(registry)
                .increment();
    }

    /**
     * Records a cache eviction event.
     *
     * @param cacheName the name of the cache
     */
    public void recordCacheEviction(String cacheName) {
        Counter.builder("adhar.cache.eviction")
                .description("Cache eviction count")
                .tag("cache", cacheName)
                .register(registry)
                .increment();
    }

    /**
     * Records the current size of a cache.
     *
     * @param cacheName the name of the cache
     * @param size      the current number of entries in the cache
     */
    public void recordCacheSize(String cacheName, long size) {
        registerGauge("adhar.cache.size." + cacheName, "Cache size for " + cacheName, size);
    }

    // -------------------------------------------------------------------------
    // Messaging Metrics
    // -------------------------------------------------------------------------

    /**
     * Records a successfully published message.
     *
     * @param topic      the topic or channel the message was published to
     * @param durationMs time taken to publish in milliseconds
     */
    public void recordMessagePublished(String topic, long durationMs) {
        Timer.builder("adhar.messaging.publish.duration")
                .description("Message publish latency")
                .tag("topic", topic)
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.messaging.publish.count")
                .description("Messages published count")
                .tag("topic", topic)
                .register(registry)
                .increment();
    }

    /**
     * Records a consumed message.
     *
     * @param topic      the topic or channel the message was consumed from
     * @param durationMs processing time in milliseconds
     * @param success    whether the message was processed successfully
     */
    public void recordMessageConsumed(String topic, long durationMs, boolean success) {
        Timer.builder("adhar.messaging.consume.duration")
                .description("Message consumption latency")
                .tag("topic", topic)
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.messaging.consume.count")
                .description("Messages consumed count")
                .tag("topic", topic)
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();
    }

    /**
     * Records a failed message processing attempt.
     *
     * @param topic     the topic or channel
     * @param errorType the type of error (e.g., "DeserializationError", "TimeoutException")
     */
    public void recordMessageFailed(String topic, String errorType) {
        Counter.builder("adhar.messaging.errors")
                .description("Message processing errors")
                .tag("topic", topic)
                .tag("error_type", errorType)
                .register(registry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // Resilience Metrics
    // -------------------------------------------------------------------------

    /**
     * Records the current state of a circuit breaker.
     *
     * @param name  the circuit breaker name
     * @param state the current state (e.g., "CLOSED", "OPEN", "HALF_OPEN")
     */
    public void recordCircuitBreakerState(String name, String state) {
        registerGauge("adhar.resilience.circuit_breaker." + name,
                "Circuit breaker state for " + name,
                stateToNumeric(state));

        Counter.builder("adhar.resilience.circuit_breaker.transitions")
                .description("Circuit breaker state transitions")
                .tag("name", name)
                .tag("state", state)
                .register(registry)
                .increment();
    }

    /**
     * Records a retry attempt.
     *
     * @param name    the retry configuration name
     * @param attempt the attempt number (1-based)
     * @param success whether this attempt succeeded
     */
    public void recordRetryAttempt(String name, int attempt, boolean success) {
        Counter.builder("adhar.resilience.retry.attempts")
                .description("Retry attempt count")
                .tag("name", name)
                .tag("attempt", String.valueOf(attempt))
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();
    }

    /**
     * Records a rate limit rejection.
     *
     * @param name the rate limiter name
     */
    public void recordRateLimitReject(String name) {
        Counter.builder("adhar.resilience.rate_limit.rejected")
                .description("Rate limit rejections")
                .tag("name", name)
                .register(registry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // HTTP / API Metrics
    // -------------------------------------------------------------------------

    /**
     * Records an HTTP request with its duration and response status.
     *
     * @param method     the HTTP method (GET, POST, etc.)
     * @param path       the request path
     * @param status     the HTTP response status code
     * @param durationMs request processing time in milliseconds
     */
    public void recordHttpRequest(String method, String path, int status, long durationMs) {
        Timer.builder("adhar.http.request.duration")
                .description("HTTP request latency")
                .tag("method", method)
                .tag("path", path)
                .tag("status", String.valueOf(status))
                .tag("success", String.valueOf(status >= 200 && status < 400))
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.http.request.count")
                .description("HTTP request count")
                .tag("method", method)
                .tag("path", path)
                .tag("status", String.valueOf(status))
                .register(registry)
                .increment();
    }

    /**
     * Records an HTTP error.
     *
     * @param method    the HTTP method
     * @param path      the request path
     * @param errorType the error type (e.g., exception class name)
     */
    public void recordHttpError(String method, String path, String errorType) {
        Counter.builder("adhar.http.errors")
                .description("HTTP error count")
                .tag("method", method)
                .tag("path", path)
                .tag("error_type", errorType)
                .register(registry)
                .increment();
    }

    // -------------------------------------------------------------------------
    // AI Metrics
    // -------------------------------------------------------------------------

    /**
     * Records the latency of an AI operation.
     *
     * @param provider   the AI provider name (e.g., "openai", "anthropic")
     * @param operation  the operation type (e.g., "completion", "embedding")
     * @param durationMs execution time in milliseconds
     */
    public void recordAiLatency(String provider, String operation, long durationMs) {
        Timer.builder("adhar.ai.operation.duration")
                .description("AI operation latency")
                .tag("provider", provider)
                .tag("operation", operation)
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.ai.operation.count")
                .description("AI operation count")
                .tag("provider", provider)
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    /**
     * Records the number of tokens used by an AI operation.
     *
     * @param provider the AI provider name
     * @param tokens   the number of tokens consumed
     */
    public void recordAiTokens(String provider, int tokens) {
        Counter.builder("adhar.ai.tokens.total")
                .description("Total AI tokens consumed")
                .tag("provider", provider)
                .register(registry)
                .increment(tokens);
    }

    // -------------------------------------------------------------------------
    // General Operation Metrics
    // -------------------------------------------------------------------------

    /**
     * Records a generic operation latency across any Adhar Kit module.
     * <p>
     * This is a catch-all method for modules that do not have a dedicated recording
     * method. Prefer the module-specific methods when available.
     * </p>
     *
     * @param module     the module name (e.g., "security", "analytics")
     * @param operation  the operation name (e.g., "authenticate", "trackEvent")
     * @param durationMs execution time in milliseconds
     * @param success    whether the operation succeeded
     */
    public void recordOperationLatency(String module, String operation, long durationMs, boolean success) {
        Timer.builder("adhar.operation.duration")
                .description("Operation latency")
                .tag("module", module)
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        Counter.builder("adhar.operation.count")
                .description("Operation invocation count")
                .tag("module", module)
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();

        if (!success) {
            Counter.builder("adhar.operation.errors")
                    .description("Operation error count")
                    .tag("module", module)
                    .tag("operation", operation)
                    .register(registry)
                    .increment();
        }
    }

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    /**
     * Registers or updates a gauge value, creating the gauge on first access.
     */
    private void registerGauge(String name, String description, long value) {
        AtomicLong holder = gaugeValues.computeIfAbsent(name, key -> {
            AtomicLong ref = new AtomicLong(value);
            Gauge.builder(key, ref, AtomicLong::doubleValue)
                    .description(description)
                    .register(registry);
            return ref;
        });
        holder.set(value);
    }

    /**
     * Converts a circuit breaker state string to a numeric value for gauge representation.
     */
    private long stateToNumeric(String state) {
        return switch (state.toUpperCase()) {
            case "CLOSED" -> 0;
            case "HALF_OPEN" -> 1;
            case "OPEN" -> 2;
            default -> -1;
        };
    }
}
