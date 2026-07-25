package com.adhar.kit.starter;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.logging.LoggingFacade;
import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.batch.BatchFacade;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkDetector;
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

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Unified Adhar Facade - single entry point for all Adhar Kit modules across
 * Spring Boot, Quarkus, Micronaut, Helidon, and Vert.x.
 *
 * <p>The facade itself is framework-neutral: it has no DI annotations and no
 * compile-time coupling to any framework. Each supported runtime ships a thin
 * adapter (in {@code starter.spring}, {@code starter.quarkus},
 * {@code starter.micronaut}, {@code starter.helidon}, {@code starter.vertx})
 * that exposes {@code AdharFacade} as a managed bean. Outside a managed
 * container use {@link #getInstance()}.</p>
 *
 * <h3>Module accessors</h3>
 * <pre>{@code
 * adhar.getMetrics().increment("orders.created");
 * adhar.getResilience().execute("svc", () -> callService());
 * adhar.getSecurity().hasPermission("order:create");
 * }</pre>
 *
 * <h3>Convenience shortcuts</h3>
 * <pre>{@code
 * Order order = adhar.traced("create-order", () -> processOrder(req));
 * Result r    = adhar.resilient("payment", () -> charge(req), () -> queue(req));
 * User user   = adhar.cached("users", id, User.class, () -> db.findUser(id));
 * adhar.notify("user@example.com", "Order Confirmed", "Order #123 confirmed");
 * adhar.publish("order-events", orderEvent);
 * String answer = adhar.chat("Summarize this document: " + text);
 * int timeout   = adhar.configInt("app.timeout", 30);
 * }</pre>
 *
 * <h3>Module toggles</h3>
 * <p>Every sub-facade accessor is gated by an {@link AdharModuleAccess}. When
 * a module is disabled (e.g. {@code adhar.kit.modules.ai.enabled=false} on
 * Spring Boot), the corresponding sub-facade is never constructed and its
 * accessor throws {@link IllegalStateException} instead of returning a live
 * instance. All facades - including the previously "eager" core set - are
 * now lazily constructed on first access.</p>
 *
 * @author Tapas Jena
 * @since 0.1.0
 */
@Slf4j
public class AdharFacade {

    // Module ids - shared vocabulary with AdharKitModuleRegistry / adhar.kit.modules.*
    private static final String MOD_LOGGING = "logging";
    private static final String MOD_METRICS = "metrics";
    private static final String MOD_TRACING = "tracing";
    private static final String MOD_RESILIENCE = "resilience";
    private static final String MOD_CACHE = "cache";
    private static final String MOD_HEALTH = "health";
    private static final String MOD_MESSAGING = "messaging";
    private static final String MOD_PERSISTENCE = "persistence";
    private static final String MOD_SECURITY = "security";
    private static final String MOD_CONFIG = "config";
    private static final String MOD_DOCS = "docs";
    private static final String MOD_GRPC = "grpc";
    private static final String MOD_AI = "ai";
    private static final String MOD_ANALYTICS = "analytics";
    private static final String MOD_KUBERNETES = "kubernetes";
    private static final String MOD_DAPR = "dapr";
    private static final String MOD_GRAPHQL = "graphql";
    private static final String MOD_BATCH = "batch";
    private static final String MOD_NOTIFICATION = "notification";
    private static final String MOD_EVENT_SOURCING = "event-sourcing";
    private static final String MOD_PERF_PROFILER = "perf-profiler";
    private static final String MOD_REWRITE = "rewrite";

    private static volatile AdharFacade instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final AdharModuleAccess moduleAccess;

    // ========================================================================
    // Core facades (lazy, gated)
    // ========================================================================
    private volatile LoggingFacade logging;
    private volatile MetricsFacade metrics;
    private volatile TracingFacade tracing;
    private volatile CircuitBreakerFacade resilience;
    private volatile CacheFacade cache;
    private volatile HealthFacade health;
    private volatile MessagingFacade messaging;
    private volatile PersistenceFacade persistence;
    private volatile SecurityFacade security;
    private volatile ConfigFacade config;

    // ========================================================================
    // Advanced facades (lazy, gated)
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
        this(AdharModuleAccess.ALL_ENABLED);
    }

    public AdharFacade(AdharModuleAccess moduleAccess) {
        this.moduleAccess = moduleAccess != null ? moduleAccess : AdharModuleAccess.ALL_ENABLED;
        Framework fw = FrameworkDetector.detect();
        log.info("Initializing Adhar Facade on {} - modules gated by AdharModuleAccess", fw);
    }

    /** Returns the framework Adhar Kit detected at runtime. */
    public Framework currentFramework() {
        return FrameworkDetector.detect();
    }

    /**
     * Returns the process-wide singleton, creating it - with every module
     * enabled - on first call if necessary.
     */
    public static AdharFacade getInstance() {
        return getInstance(AdharModuleAccess.ALL_ENABLED);
    }

    /**
     * Returns the process-wide singleton, creating it with the given module
     * access on first call. If the singleton already exists, {@code
     * moduleAccess} is ignored (a warning is logged) since module gating is
     * fixed for the lifetime of the singleton.
     */
    public static AdharFacade getInstance(AdharModuleAccess moduleAccess) {
        AdharFacade result = instance;
        if (result == null) {
            synchronized (INSTANCE_LOCK) {
                result = instance;
                if (result == null) {
                    result = new AdharFacade(moduleAccess);
                    applyServiceLoaderCustomizers(result);
                    instance = result;
                } else {
                    log.debug("AdharFacade already initialized; ignoring supplied AdharModuleAccess");
                }
            }
        }
        return result;
    }

    private static void applyServiceLoaderCustomizers(AdharFacade facade) {
        for (AdharFacadeCustomizer customizer : ServiceLoader.load(AdharFacadeCustomizer.class)) {
            try {
                customizer.customize(facade);
            } catch (RuntimeException e) {
                log.warn("AdharFacadeCustomizer {} failed", customizer.getClass().getName(), e);
            }
        }
    }

    /**
     * Clears the process-wide singleton so the next {@link #getInstance()} call
     * creates a fresh instance. Test-only escape hatch - production code should
     * never call this, since it can race with concurrent {@code getInstance()}
     * callers holding a reference to the old instance.
     */
    public static void resetForTesting() {
        synchronized (INSTANCE_LOCK) {
            instance = null;
        }
    }

    // ========================================================================
    // Module Accessors - Core
    // ========================================================================

    /** Structured logging with MDC context support. */
    public LoggingFacade getLogging() {
        requireEnabled(MOD_LOGGING);
        return lazyInit(() -> logging, () -> logging = LoggingFacade.getLogger(AdharFacade.class));
    }

    /** Micrometer metrics - counters, timers, gauges. */
    public MetricsFacade getMetrics() {
        requireEnabled(MOD_METRICS);
        return lazyInit(() -> metrics, () -> metrics = MetricsFacade.getInstance());
    }

    /** Distributed tracing - spans, context propagation. */
    public TracingFacade getTracing() {
        requireEnabled(MOD_TRACING);
        return lazyInit(() -> tracing, () -> tracing = TracingFacade.getInstance());
    }

    /** Resilience patterns - circuit breaker, retry, rate limiter, bulkhead. */
    public CircuitBreakerFacade getResilience() {
        requireEnabled(MOD_RESILIENCE);
        return lazyInit(() -> resilience, () -> resilience = CircuitBreakerFacade.getInstance());
    }

    /** In-memory and distributed caching with Caffeine/Redis. */
    public CacheFacade getCache() {
        requireEnabled(MOD_CACHE);
        return lazyInit(() -> cache, () -> cache = CacheFacade.builder().cacheName("adhar-default").build());
    }

    /** Named cache access. Returns a CacheFacade for the given cache name. */
    public CacheFacade getCache(String cacheName) {
        requireEnabled(MOD_CACHE);
        return CacheFacade.getCache(cacheName);
    }

    /** Health checks - liveness, readiness, detailed status. */
    public HealthFacade getHealth() {
        requireEnabled(MOD_HEALTH);
        return lazyInit(() -> health, () -> health = HealthFacade.getInstance());
    }

    /** Kafka/RabbitMQ messaging with CloudEvents. */
    public MessagingFacade getMessaging() {
        requireEnabled(MOD_MESSAGING);
        return lazyInit(() -> messaging, () -> messaging = MessagingFacade.getInstance());
    }

    /** JPA persistence with auditing, multi-tenancy, soft delete. */
    public PersistenceFacade getPersistence() {
        requireEnabled(MOD_PERSISTENCE);
        return lazyInit(() -> persistence, () -> persistence = PersistenceFacade.getInstance());
    }

    /** OAuth2/JWT security - authentication, authorization, tokens. */
    public SecurityFacade getSecurity() {
        requireEnabled(MOD_SECURITY);
        return lazyInit(() -> security, () -> security = SecurityFacade.getInstance());
    }

    /** Dynamic configuration with refresh and encryption. */
    public ConfigFacade getConfig() {
        requireEnabled(MOD_CONFIG);
        return lazyInit(() -> config, () -> config = ConfigFacade.getInstance());
    }

    // ========================================================================
    // Module Accessors - Advanced
    // ========================================================================

    /** OpenAPI/Swagger documentation. */
    public ApiDocsFacade getApiDocs() {
        requireEnabled(MOD_DOCS);
        return lazyInit(() -> apiDocs, () -> apiDocs = ApiDocsFacade.getInstance());
    }

    /** gRPC services - unary, streaming, metadata. */
    public GrpcFacade getGrpc() {
        requireEnabled(MOD_GRPC);
        return lazyInit(() -> grpc, () -> grpc = GrpcFacade.getInstance());
    }

    /** Multi-provider AI - chat, embeddings, RAG, image generation. */
    public AiFacade getAi() {
        requireEnabled(MOD_AI);
        return lazyInit(() -> ai, () -> ai = AiFacade.getInstance());
    }

    /** Product analytics - event tracking, feature flags. */
    public AnalyticsFacade getAnalytics() {
        requireEnabled(MOD_ANALYTICS);
        return lazyInit(() -> analytics, () -> analytics = AnalyticsFacade.getInstance());
    }

    /** Kubernetes native - ConfigMaps, Secrets, scaling, pods. */
    public KubernetesFacade getKubernetes() {
        requireEnabled(MOD_KUBERNETES);
        return lazyInit(() -> kubernetes, () -> kubernetes = KubernetesFacade.getInstance());
    }

    /** Dapr runtime - state, pub/sub, service invocation, actors. */
    public DaprFacade getDapr() {
        requireEnabled(MOD_DAPR);
        return lazyInit(() -> dapr, () -> dapr = DaprFacade.getInstance());
    }

    /** Core utilities - ID generation, JSON, retry, async, hashing. Not gated by a module toggle. */
    public CoreFacade getUtils() {
        return lazyInit(() -> utils, () -> utils = CoreFacade.getInstance());
    }

    /** GraphQL - schema registry, pagination, validation, data loaders. */
    public GraphQlFacade getGraphQl() {
        requireEnabled(MOD_GRAPHQL);
        return lazyInit(() -> graphQl, () -> graphQl = GraphQlFacade.getInstance());
    }

    /** Batch processing - job scheduling, readers/writers, metrics. */
    public BatchFacade getBatch() {
        requireEnabled(MOD_BATCH);
        return lazyInit(() -> batch, () -> batch = BatchFacade.getInstance());
    }

    /** Multi-channel notifications - email, webhook, in-app, SMS. */
    public NotificationFacade getNotification() {
        requireEnabled(MOD_NOTIFICATION);
        return lazyInit(() -> notification, () -> notification = NotificationFacade.getInstance());
    }

    /** Event sourcing - event store, aggregates, CQRS. */
    public EventSourcingFacade getEventStore() {
        requireEnabled(MOD_EVENT_SOURCING);
        return lazyInit(() -> eventStore, () -> eventStore = EventSourcingFacade.getInstance());
    }

    /** Performance profiler - hotspots, memory, method timing. */
    public ProfilerFacade getProfiler() {
        requireEnabled(MOD_PERF_PROFILER);
        return lazyInit(() -> profiler, () -> profiler = ProfilerFacade.getInstance());
    }

    /** OpenRewrite code modernization - automated migrations and recipe catalog. */
    public RewriteFacade getRewrite() {
        requireEnabled(MOD_REWRITE);
        return lazyInit(() -> rewrite, () -> rewrite = RewriteFacade.getInstance());
    }

    // ========================================================================
    // Sub-facade overrides (AdharFacadeCustomizer support)
    // ========================================================================

    public void setLogging(LoggingFacade logging) { this.logging = logging; }
    public void setMetrics(MetricsFacade metrics) { this.metrics = metrics; }
    public void setTracing(TracingFacade tracing) { this.tracing = tracing; }
    public void setResilience(CircuitBreakerFacade resilience) { this.resilience = resilience; }
    public void setCache(CacheFacade cache) { this.cache = cache; }
    public void setHealth(HealthFacade health) { this.health = health; }
    public void setMessaging(MessagingFacade messaging) { this.messaging = messaging; }
    public void setPersistence(PersistenceFacade persistence) { this.persistence = persistence; }
    public void setSecurity(SecurityFacade security) { this.security = security; }
    public void setConfig(ConfigFacade config) { this.config = config; }
    public void setApiDocs(ApiDocsFacade apiDocs) { this.apiDocs = apiDocs; }
    public void setGrpc(GrpcFacade grpc) { this.grpc = grpc; }
    public void setAi(AiFacade ai) { this.ai = ai; }
    public void setAnalytics(AnalyticsFacade analytics) { this.analytics = analytics; }
    public void setKubernetes(KubernetesFacade kubernetes) { this.kubernetes = kubernetes; }
    public void setDapr(DaprFacade dapr) { this.dapr = dapr; }
    public void setUtils(CoreFacade utils) { this.utils = utils; }
    public void setGraphQl(GraphQlFacade graphQl) { this.graphQl = graphQl; }
    public void setBatch(BatchFacade batch) { this.batch = batch; }
    public void setNotification(NotificationFacade notification) { this.notification = notification; }
    public void setEventStore(EventSourcingFacade eventStore) { this.eventStore = eventStore; }
    public void setProfiler(ProfilerFacade profiler) { this.profiler = profiler; }
    public void setRewrite(RewriteFacade rewrite) { this.rewrite = rewrite; }

    // ========================================================================
    // Convenience Shortcuts - Observability
    // ========================================================================

    /**
     * Execute an operation with automatic tracing and metrics timing.
     * Creates a span and records duration in one call.
     */
    public <T> T traced(String operationName, Supplier<T> operation) {
        return getMetrics().recordTime(operationName, () ->
                getTracing().executeInSpan(operationName, operation));
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
        getMetrics().increment(metricName, tags);
    }

    /**
     * Log an info message via the facade logger.
     */
    public LoggingFacade log() {
        return getLogging();
    }

    /**
     * Log an info message directly.
     */
    public void logInfo(String message, Object... args) {
        getLogging().info(message, args);
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
        return getResilience().execute(name, operation);
    }

    /**
     * Execute with circuit breaker and fallback.
     */
    public <T> T resilient(String name, Supplier<T> operation, Supplier<T> fallback) {
        return getResilience().executeWithFallback(name, operation, fallback);
    }

    /**
     * Execute with full resilience: tracing + metrics + circuit breaker + fallback.
     */
    public <T> T safe(String name, Supplier<T> operation, Supplier<T> fallback) {
        return traced(name, () -> getResilience().executeWithFallback(name, operation, fallback));
    }

    // ========================================================================
    // Convenience Shortcuts - Caching
    // ========================================================================

    /**
     * Cache-aside pattern: get from cache or compute and store.
     */
    public <T> T cached(String cacheName, Object key, Class<T> type, Supplier<T> loader) {
        requireEnabled(MOD_CACHE);
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
        requireEnabled(MOD_CACHE);
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
        getMessaging().publish(topic, event);
    }

    /**
     * Publish a keyed event to a topic.
     */
    public <T> void publish(String topic, String key, T event) {
        getMessaging().publish(topic, key, event);
    }

    /**
     * Subscribe to a topic with a typed handler.
     */
    public <T> String subscribe(String topic, Class<T> type, java.util.function.Consumer<T> handler) {
        return getMessaging().subscribe(topic, type, handler);
    }

    // ========================================================================
    // Convenience Shortcuts - Persistence
    // ========================================================================

    /**
     * Save an entity.
     */
    public <T> T save(T entity) {
        return getPersistence().save(entity);
    }

    /**
     * Save multiple entities in batch.
     */
    public <T> java.util.List<T> saveAll(Iterable<T> entities) {
        return getPersistence().saveAll(entities);
    }

    /**
     * Find an entity by ID.
     */
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        return getPersistence().findById(entityClass, id);
    }

    /**
     * Find all entities of a type with pagination.
     */
    public <T> Object findAll(Class<T> entityClass, int page, int size) {
        return getPersistence().findAll(entityClass, page, size);
    }

    /**
     * Delete an entity.
     */
    public <T> void delete(T entity) {
        getPersistence().delete(entity);
    }

    /**
     * Execute within a transaction.
     */
    public <T> T transactional(Supplier<T> operation) {
        return getPersistence().executeInTransaction(operation);
    }

    /**
     * Execute a read-only operation (optimized, no dirty checking).
     */
    public <T> T readOnly(Supplier<T> operation) {
        return getPersistence().executeReadOnly(operation);
    }

    /**
     * Count entities of a type.
     */
    public <T> long count(Class<T> entityClass) {
        return getPersistence().count(entityClass);
    }

    /**
     * Check if entity exists by ID.
     */
    public <T, ID> boolean exists(Class<T> entityClass, ID id) {
        return getPersistence().existsById(entityClass, id);
    }

    // ========================================================================
    // Convenience Shortcuts - Security
    // ========================================================================

    /**
     * Check if current user has a permission.
     */
    public boolean hasPermission(String permission) {
        return getSecurity().hasPermission(permission);
    }

    /**
     * Check if current user has a role.
     */
    public boolean hasRole(String role) {
        return getSecurity().hasRole(role);
    }

    /**
     * Get the current authenticated user's ID.
     */
    public String currentUserId() {
        return getSecurity().getCurrentUserId();
    }

    /**
     * Check if the current request is authenticated.
     */
    public boolean isAuthenticated() {
        return getSecurity().isAuthenticated();
    }

    // ========================================================================
    // Convenience Shortcuts - Configuration
    // ========================================================================

    /**
     * Get a configuration value.
     */
    public String config(String key, String defaultValue) {
        return getConfig().get(key, defaultValue);
    }

    /**
     * Get an integer configuration value.
     */
    public int configInt(String key, int defaultValue) {
        return getConfig().getInt(key, defaultValue);
    }

    /**
     * Get a boolean configuration value.
     */
    public boolean configBool(String key, boolean defaultValue) {
        return getConfig().getBoolean(key, defaultValue);
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
        return getHealth().getHealth() == com.adhar.kit.health.HealthFacade.HealthStatus.UP;
    }

    /**
     * Get detailed health for all components.
     */
    public Map<String, ?> healthDetails() {
        return getHealth().getDetailedHealth();
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
     * Returns information about Adhar Kit modules and the detected runtime framework.
     */
    public String getModuleInfo() {
        return """
            Adhar Kit v%s - Production-Ready Modules running on %s:

            TIER-1 Core: commons, core/utils, resilience, metrics, tracing, logging, cache
            TIER-2 Integration: health, test-commons, messaging, apiDocs, grpc, graphql
            TIER-3 Enterprise: persistence, security, config, ai, analytics, kubernetes,
                dapr, batch, notification, event-sourcing, perf-profiler, rewrite, starter, maven-plugin

            Framework Support: Spring Boot 4.0+, Quarkus 3.21+, Micronaut 4.8+, Helidon 4.2+, Vert.x 4.5+
            """.formatted(getVersion(), currentFramework());
    }

    /** Returns the running Adhar Kit version, resolved from the build (see {@link AdharKitVersion}). */
    public String getVersion() {
        return AdharKitVersion.getVersion();
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private void requireEnabled(String moduleId) {
        if (!moduleAccess.isEnabled(moduleId)) {
            throw new IllegalStateException(
                    "Adhar Kit module '" + moduleId + "' is disabled "
                            + "(adhar.kit.modules." + moduleId + "=false); "
                            + "enable it to use this facade.");
        }
    }

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
