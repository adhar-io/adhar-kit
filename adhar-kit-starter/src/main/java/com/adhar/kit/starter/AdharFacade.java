package com.adhar.kit.starter;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.logging.LoggingFacade;
import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.batch.BatchFacade;
import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.core.CoreFacade;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.docs.ApiDocsFacade;
import com.adhar.kit.eventsourcing.EventSourcingFacade;
import com.adhar.kit.graphql.GraphQlFacade;
import com.adhar.kit.grpc.GrpcFacade;
import com.adhar.kit.health.HealthFacade;
import com.adhar.kit.kubernetes.KubernetesFacade;
import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.metrics.MetricsFacade;
import com.adhar.kit.notification.NotificationFacade;
import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.profiler.ProfilerFacade;
import com.adhar.kit.resilience.CircuitBreakerFacade;
import com.adhar.kit.rewrite.facade.RewriteFacade;
import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.tracing.TracingFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Unified Adhar Facade - Single entry point for all 27 Adhar Kit modules.
 *
 * <p>Provides both module accessors and convenience shortcuts that eliminate
 * boilerplate for the most common cross-cutting operations.</p>
 *
 * <h3>Module Accessors</h3>
 * <pre>{@code
 * adhar.getMetrics().increment("orders.created");
 * adhar.getResilience().execute("svc", () -> callService());
 * adhar.getSecurity().hasPermission("order:create");
 * }</pre>
 *
 * <h3>Convenience Shortcuts</h3>
 * <pre>{@code
 * // Traced + timed operation (auto metrics + tracing in one call)
 * Order order = adhar.traced("create-order", () -> processOrder(req));
 *
 * // Resilient call with fallback
 * Result r = adhar.resilient("payment", () -> charge(req), () -> queuePayment(req));
 *
 * // Cache-aside pattern
 * User user = adhar.cached("users", id, User.class, () -> db.findUser(id));
 *
 * // Quick notifications
 * adhar.notify("user@example.com", "Order Confirmed", "Your order #123 is confirmed");
 *
 * // Quick publish
 * adhar.publish("order-events", orderEvent);
 *
 * // Quick AI chat
 * String answer = adhar.chat("Summarize this document: " + text);
 *
 * // Quick config
 * int timeout = adhar.configInt("app.timeout", 30);
 * }</pre>
 *
 * @author Tapas Jena
 * @since 1.0.0
 */
@Slf4j
@Component
public class AdharFacade {

    private static volatile AdharFacade instance;

    // ========================================================================
    // Core facades (eager - most commonly used)
    // ========================================================================
    private final LoggingFacade logging;
    private final MetricsFacade metrics;
    private final TracingFacade tracing;
    private final CircuitBreakerFacade resilience;
    private final CacheFacade cache;
    private final HealthFacade health;
    private final MessagingFacade messaging;
    private final PersistenceFacade persistence;
    private final SecurityFacade security;
    private final ConfigFacade config;

    // ========================================================================
    // Advanced facades (lazy - loaded on first access)
    // ========================================================================
    private volatile ApiDocsFacade apiDocs;
    private volatile GrpcFacade grpc;
    private volatile AiFacade ai;
    private volatile AnalyticsFacade analytics;
    private volatile KubernetesFacade kubernetes;
    private volatile DaprFacade dapr;
    private volatile CoreFacade utils;
    private volatile GraphQlFacade graphQl;
    private volatile BatchFacade batch;
    private volatile NotificationFacade notification;
    private volatile EventSourcingFacade eventStore;
    private volatile ProfilerFacade profiler;
    private volatile RewriteFacade rewrite;

    public AdharFacade() {
        log.info("Initializing Adhar Facade - unified access to all 27 modules");

        this.logging = LoggingFacade.getLogger(AdharFacade.class);
        this.metrics = MetricsFacade.getInstance();
        this.tracing = TracingFacade.getInstance();
        this.resilience = CircuitBreakerFacade.getInstance();
        this.cache = CacheFacade.builder().cacheName("adhar-default").build();
        this.health = HealthFacade.getInstance();
        this.messaging = MessagingFacade.getInstance();
        this.persistence = PersistenceFacade.getInstance();
        this.security = SecurityFacade.getInstance();
        this.config = ConfigFacade.getInstance();

        if (instance == null) {
            instance = this;
        }

        log.info("Adhar Facade initialized - 27 modules available (10 eager, 12 lazy)");
    }

    public static AdharFacade getInstance() {
        if (instance == null) {
            synchronized (AdharFacade.class) {
                if (instance == null) {
                    instance = new AdharFacade();
                }
            }
        }
        return instance;
    }

    // ========================================================================
    // Module Accessors - Core (eager)
    // ========================================================================

    /** Structured logging with MDC context support. */
    public LoggingFacade getLogging() { return logging; }

    /** Micrometer metrics - counters, timers, gauges. */
    public MetricsFacade getMetrics() { return metrics; }

    /** Distributed tracing - spans, context propagation. */
    public TracingFacade getTracing() { return tracing; }

    /** Resilience patterns - circuit breaker, retry, rate limiter, bulkhead. */
    public CircuitBreakerFacade getResilience() { return resilience; }

    /** In-memory and distributed caching with Caffeine/Redis. */
    public CacheFacade getCache() { return cache; }

    /** Named cache access. Returns a CacheFacade for the given cache name. */
    public CacheFacade getCache(String cacheName) {
        return CacheFacade.getCache(cacheName);
    }

    /** Health checks - liveness, readiness, detailed status. */
    public HealthFacade getHealth() { return health; }

    /** Kafka/RabbitMQ messaging with CloudEvents. */
    public MessagingFacade getMessaging() { return messaging; }

    /** JPA persistence with auditing, multi-tenancy, soft delete. */
    public PersistenceFacade getPersistence() { return persistence; }

    /** OAuth2/JWT security - authentication, authorization, tokens. */
    public SecurityFacade getSecurity() { return security; }

    /** Dynamic configuration with refresh and encryption. */
    public ConfigFacade getConfig() { return config; }

    // ========================================================================
    // Module Accessors - Advanced (lazy)
    // ========================================================================

    /** OpenAPI/Swagger documentation. */
    public ApiDocsFacade getApiDocs() {
        return lazyInit(() -> apiDocs, () -> apiDocs = ApiDocsFacade.getInstance());
    }

    /** gRPC services - unary, streaming, metadata. */
    public GrpcFacade getGrpc() {
        return lazyInit(() -> grpc, () -> grpc = GrpcFacade.getInstance());
    }

    /** Multi-provider AI - chat, embeddings, RAG, image generation. */
    public AiFacade getAi() {
        return lazyInit(() -> ai, () -> ai = AiFacade.getInstance());
    }

    /** Product analytics - event tracking, feature flags. */
    public AnalyticsFacade getAnalytics() {
        return lazyInit(() -> analytics, () -> analytics = AnalyticsFacade.getInstance());
    }

    /** Kubernetes native - ConfigMaps, Secrets, scaling, pods. */
    public KubernetesFacade getKubernetes() {
        return lazyInit(() -> kubernetes, () -> kubernetes = KubernetesFacade.getInstance());
    }

    /** Dapr runtime - state, pub/sub, service invocation, actors. */
    public DaprFacade getDapr() {
        return lazyInit(() -> dapr, () -> dapr = DaprFacade.getInstance());
    }

    /** Core utilities - ID generation, JSON, retry, async, hashing. */
    public CoreFacade getUtils() {
        return lazyInit(() -> utils, () -> utils = CoreFacade.getInstance());
    }

    /** GraphQL - schema registry, pagination, validation, data loaders. */
    public GraphQlFacade getGraphQl() {
        return lazyInit(() -> graphQl, () -> graphQl = GraphQlFacade.getInstance());
    }

    /** Batch processing - job scheduling, readers/writers, metrics. */
    public BatchFacade getBatch() {
        return lazyInit(() -> batch, () -> batch = BatchFacade.getInstance());
    }

    /** Multi-channel notifications - email, webhook, in-app, SMS. */
    public NotificationFacade getNotification() {
        return lazyInit(() -> notification, () -> notification = NotificationFacade.getInstance());
    }

    /** Event sourcing - event store, aggregates, CQRS. */
    public EventSourcingFacade getEventStore() {
        return lazyInit(() -> eventStore, () -> eventStore = EventSourcingFacade.getInstance());
    }

    /** Performance profiler - hotspots, memory, method timing. */
    public ProfilerFacade getProfiler() {
        return lazyInit(() -> profiler, () -> profiler = ProfilerFacade.getInstance());
    }

    /** OpenRewrite code modernization - automated migrations and recipe catalog. */
    public RewriteFacade getRewrite() {
        return lazyInit(() -> rewrite, () -> rewrite = RewriteFacade.getInstance());
    }

    // ========================================================================
    // Convenience Shortcuts - Observability
    // ========================================================================

    /**
     * Execute an operation with automatic tracing and metrics timing.
     * Creates a span and records duration in one call.
     */
    public <T> T traced(String operationName, Supplier<T> operation) {
        return metrics.recordTime(operationName, () ->
                tracing.executeInSpan(operationName, operation));
    }

    /**
     * Execute a void operation with automatic tracing and metrics timing.
     */
    public void traced(String operationName, Runnable operation) {
        traced(operationName, () -> { operation.run(); return null; });
    }

    /**
     * Increment a metric counter.
     */
    public void count(String metricName, String... tags) {
        metrics.increment(metricName, tags);
    }

    /**
     * Log an info message via the facade logger.
     */
    public LoggingFacade log() {
        return logging;
    }

    /**
     * Log an info message directly.
     */
    public void logInfo(String message, Object... args) {
        logging.info(message, args);
    }

    /**
     * Manually profile a code block and record timing.
     */
    public <T> T profiled(String name, Supplier<T> operation) {
        return getProfiler().profile(name, operation);
    }

    // ========================================================================
    // Convenience Shortcuts - Resilience
    // ========================================================================

    /**
     * Execute with circuit breaker protection.
     */
    public <T> T resilient(String name, Supplier<T> operation) {
        return resilience.execute(name, operation);
    }

    /**
     * Execute with circuit breaker and fallback.
     */
    public <T> T resilient(String name, Supplier<T> operation, Supplier<T> fallback) {
        return resilience.executeWithFallback(name, operation, fallback);
    }

    /**
     * Execute with full resilience: tracing + metrics + circuit breaker + fallback.
     */
    public <T> T safe(String name, Supplier<T> operation, Supplier<T> fallback) {
        return traced(name, () -> resilience.executeWithFallback(name, operation, fallback));
    }

    // ========================================================================
    // Convenience Shortcuts - Caching
    // ========================================================================

    /**
     * Cache-aside pattern: get from cache or compute and store.
     */
    public <T> T cached(String cacheName, Object key, Class<T> type, Supplier<T> loader) {
        CacheFacade c = CacheFacade.getCache(cacheName);
        if (c == null) {
            c = CacheFacade.builder().cacheName(cacheName).build();
        }
        T value = c.get(key, type);
        if (value != null) {
            return value;
        }
        value = loader.get();
        if (value != null) {
            c.put(key, value);
        }
        return value;
    }

    /**
     * Evict a key from a named cache.
     */
    public void evict(String cacheName, Object key) {
        CacheFacade c = CacheFacade.getCache(cacheName);
        if (c != null) {
            c.evict(key);
        }
    }

    // ========================================================================
    // Convenience Shortcuts - Messaging
    // ========================================================================

    /**
     * Publish an event to a topic.
     */
    public <T> void publish(String topic, T event) {
        messaging.publish(topic, event);
    }

    /**
     * Publish a keyed event to a topic.
     */
    public <T> void publish(String topic, String key, T event) {
        messaging.publish(topic, key, event);
    }

    /**
     * Subscribe to a topic with a typed handler.
     */
    public <T> String subscribe(String topic, Class<T> type, java.util.function.Consumer<T> handler) {
        return messaging.subscribe(topic, type, handler);
    }

    // ========================================================================
    // Convenience Shortcuts - Persistence
    // ========================================================================

    /**
     * Save an entity.
     */
    public <T> T save(T entity) {
        return persistence.save(entity);
    }

    /**
     * Save multiple entities in batch.
     */
    public <T> java.util.List<T> saveAll(Iterable<T> entities) {
        return persistence.saveAll(entities);
    }

    /**
     * Find an entity by ID.
     */
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        return persistence.findById(entityClass, id);
    }

    /**
     * Find all entities of a type with pagination.
     */
    public <T> Object findAll(Class<T> entityClass, int page, int size) {
        return persistence.findAll(entityClass, page, size);
    }

    /**
     * Delete an entity.
     */
    public <T> void delete(T entity) {
        persistence.delete(entity);
    }

    /**
     * Execute within a transaction.
     */
    public <T> T transactional(Supplier<T> operation) {
        return persistence.executeInTransaction(operation);
    }

    /**
     * Execute a read-only operation (optimized, no dirty checking).
     */
    public <T> T readOnly(Supplier<T> operation) {
        return persistence.executeReadOnly(operation);
    }

    /**
     * Count entities of a type.
     */
    public <T> long count(Class<T> entityClass) {
        return persistence.count(entityClass);
    }

    /**
     * Check if entity exists by ID.
     */
    public <T, ID> boolean exists(Class<T> entityClass, ID id) {
        return persistence.existsById(entityClass, id);
    }

    // ========================================================================
    // Convenience Shortcuts - Security
    // ========================================================================

    /**
     * Check if current user has a permission.
     */
    public boolean hasPermission(String permission) {
        return security.hasPermission(permission);
    }

    /**
     * Check if current user has a role.
     */
    public boolean hasRole(String role) {
        return security.hasRole(role);
    }

    /**
     * Get the current authenticated user's ID.
     */
    public String currentUserId() {
        return security.getCurrentUserId();
    }

    /**
     * Check if the current request is authenticated.
     */
    public boolean isAuthenticated() {
        return security.isAuthenticated();
    }

    // ========================================================================
    // Convenience Shortcuts - Configuration
    // ========================================================================

    /**
     * Get a configuration value.
     */
    public String config(String key, String defaultValue) {
        return config.get(key, defaultValue);
    }

    /**
     * Get an integer configuration value.
     */
    public int configInt(String key, int defaultValue) {
        return config.getInt(key, defaultValue);
    }

    /**
     * Get a boolean configuration value.
     */
    public boolean configBool(String key, boolean defaultValue) {
        return config.getBoolean(key, defaultValue);
    }

    // ========================================================================
    // Convenience Shortcuts - AI
    // ========================================================================

    /**
     * Quick AI chat completion.
     */
    public String chat(String message) {
        return getAi().chat(message);
    }

    /**
     * AI chat with system prompt.
     */
    public String chat(String systemPrompt, String message) {
        return getAi().chat(systemPrompt, message);
    }

    /**
     * Async AI chat.
     */
    public CompletableFuture<String> chatAsync(String message) {
        return getAi().chatAsync(message);
    }

    // ========================================================================
    // Convenience Shortcuts - Notification
    // ========================================================================

    /**
     * Send a notification (auto-detects type from recipient format).
     */
    public void notify(String recipient, String subject, String body) {
        getNotification().sendEmail(recipient, subject, body);
    }

    /**
     * Send a webhook notification.
     */
    public void webhook(String url, String payload) {
        getNotification().sendWebhook(url, payload);
    }

    // ========================================================================
    // Convenience Shortcuts - Health & Diagnostics
    // ========================================================================

    /**
     * Quick health check - returns true if all checks pass.
     */
    public boolean isHealthy() {
        return health.getHealth() == com.adhar.kit.health.HealthFacade.HealthStatus.UP;
    }

    /**
     * Get detailed health for all components.
     */
    public Map<String, ?> healthDetails() {
        return health.getDetailedHealth();
    }

    // ========================================================================
    // Convenience Shortcuts - Utilities
    // ========================================================================

    /**
     * Generate a UUID.
     */
    public String uuid() {
        return getUtils().generateUUID();
    }

    /**
     * Generate a short ID.
     */
    public String shortId() {
        return getUtils().generateShortId();
    }

    /**
     * Convert object to JSON.
     */
    public String toJson(Object object) {
        return getUtils().toJson(object);
    }

    /**
     * Parse JSON to object.
     */
    public <T> T fromJson(String json, Class<T> type) {
        return getUtils().fromJson(json, type);
    }

    /**
     * Retry an operation with exponential backoff.
     */
    public <T> T retry(Supplier<T> operation, int maxRetries) {
        return getUtils().retryWithBackoff(operation, maxRetries, 100L);
    }

    /**
     * Execute async operation.
     */
    public <T> CompletableFuture<T> async(Supplier<T> task) {
        return getUtils().executeAsync(task);
    }

    // ========================================================================
    // Convenience Shortcuts - Event Sourcing
    // ========================================================================

    /**
     * Publish a domain event.
     */
    public void publishEvent(com.adhar.kit.eventsourcing.core.DomainEvent event) {
        getEventStore().publish(event);
    }

    /**
     * Subscribe to domain events of a specific type.
     */
    public void onEvent(String eventType, java.util.function.Consumer<com.adhar.kit.eventsourcing.core.DomainEvent> handler) {
        getEventStore().subscribe(eventType, handler);
    }

    // ========================================================================
    // Convenience Shortcuts - Kubernetes
    // ========================================================================

    /**
     * Check if running inside Kubernetes.
     */
    public boolean isInKubernetes() {
        return getKubernetes().isInKubernetes();
    }

    /**
     * Get a Kubernetes secret value.
     */
    public String secret(String secretName, String key) {
        return getKubernetes().getSecretValue(secretName, key);
    }

    // ========================================================================
    // Module Info
    // ========================================================================

    /**
     * Returns comprehensive information about all 27 available modules.
     */
    public String getModuleInfo() {
        return """
            Adhar Kit v%s - 28 Production-Ready Modules:

            TIER-1 Core: commons, core/utils, resilience, metrics, tracing, logging, cache
            TIER-2 Integration: health, test-commons, messaging, apiDocs, grpc, graphql
            TIER-3 Enterprise: persistence, security, config, ai, analytics, kubernetes,
                dapr, batch, notification, event-sourcing, perf-profiler, rewrite, starter, maven-plugin

            Framework Support: Spring Boot 4.0+, Quarkus 3.21+, Micronaut 4.8+, Helidon 4.2+, Vert.x 4.5+
            """.formatted(getVersion());
    }

    public String getVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null && !version.isEmpty()) return version;

        version = getClass().getPackage().getSpecificationVersion();
        if (version != null && !version.isEmpty()) return version;

        try {
            var props = new java.util.Properties();
            var stream = getClass().getResourceAsStream(
                    "/META-INF/maven/com.adhar.kit/adhar-kit-starter/pom.properties");
            if (stream != null) {
                props.load(stream);
                stream.close();
                version = props.getProperty("version");
                if (version != null && !version.isEmpty()) return version;
            }
        } catch (Exception e) {
            log.debug("Could not read version from pom.properties", e);
        }

        return "1.0.0-SNAPSHOT";
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    @SuppressWarnings("unchecked")
    private <T> T lazyInit(Supplier<T> getter, Runnable initializer) {
        T value = getter.get();
        if (value == null) {
            synchronized (this) {
                value = getter.get();
                if (value == null) {
                    initializer.run();
                    value = getter.get();
                }
            }
        }
        return value;
    }
}
