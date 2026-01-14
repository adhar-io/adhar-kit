package com.adhar.kit.starter;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.docs.ApiDocsFacade;
import com.adhar.kit.grpc.GrpcFacade;
import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.resilience.CircuitBreakerFacade;
import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.health.HealthFacade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified Adhar Kit Facade - Single entry point for all Adhar Kit modules.
 *
 * <p>This facade provides convenient access to all 14+ Adhar Kit modules through
 * a single interface, simplifying integration and reducing boilerplate code.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     private final AdharKitFacade adhar = AdharKitFacade.getInstance();
 *
 *     public Order createOrder(OrderRequest request) {
 *         // Security check
 *         if (!adhar.getSecurity().hasPermission("order:create")) {
 *             throw new ForbiddenException();
 *         }
 *
 *         // Log with context
 *         adhar.getLogging().info("Creating order");
 *         adhar.getMetrics().increment("orders.created");
 *
 *         // Execute with tracing and resilience
 *         return adhar.getMetrics().recordTime("order.creation", () ->
 *             adhar.getTracing().executeInSpan("create-order", () -> {
 *
 *                 // Get from cache or database
 *                 Customer customer = adhar.getCache().getOrCompute("customers",
 *                     request.getCustomerId(), Customer.class,
 *                     () -> adhar.getPersistence().findById(Customer.class,
 *                         request.getCustomerId()).orElseThrow());
 *
 *                 // Process with circuit breaker
 *                 return adhar.getCircuitBreaker().executeWithFallback("order-processor",
 *                     () -> processOrder(request, customer),
 *                     () -> queueOrder(request));
 *             })
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>Simplified Configuration:</b></p>
 * <pre>{@code
 * @PostConstruct
 * public void init() {
 *     AdharKitFacade adhar = AdharKitFacade.getInstance();
 *
 *     // Configure API docs
 *     adhar.getDocs().setTitle("Order Service API");
 *     adhar.getDocs().setVersion("1.0.0");
 *
 *     // Register health checks
 *     adhar.getHealth().registerReadinessCheck("database", this::checkDB);
 *
 *     // Subscribe to events
 *     adhar.getMessaging().subscribe("order-events", OrderEvent.class,
 *         this::handleEvent);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@Getter
public class AdharKitFacade {

    private static volatile AdharKitFacade instance;

    // Core facades
    private final com.adhar.adharkit.logging.LoggingFacade logging;
    private final com.adhar.kit.metrics.MetricsFacade metrics;
    private final com.adhar.kit.tracing.TracingFacade tracing;
    private final CircuitBreakerFacade circuitBreaker;
    private final CacheFacade cache;

    // Integration facades
    private final HealthFacade health;
    private final MessagingFacade messaging;
    private final ApiDocsFacade docs;
    private final GrpcFacade grpc;

    // Enterprise facades
    private final PersistenceFacade persistence;
    private final SecurityFacade security;
    private final ConfigFacade config;

    // Advanced facades (lazy initialization for optional modules)
    private volatile com.adhar.kit.ai.AiFacade ai;
    private volatile com.adhar.kit.analytics.AnalyticsFacade analytics;
    private volatile com.adhar.kit.kubernetes.KubernetesFacade kubernetes;
    private volatile com.adhar.kit.dapr.DaprFacade dapr;
    private volatile com.adhar.kit.core.CoreFacade core;

    private AdharKitFacade() {
        log.info("Initializing Adhar Kit Facade v1.0.0 - unified access to all 22 modules");

        // Initialize core facades eagerly (most commonly used)
        this.logging = com.adhar.adharkit.logging.LoggingFacade.getLogger(AdharKitFacade.class);
        this.metrics = com.adhar.kit.metrics.MetricsFacade.getInstance();
        this.tracing = com.adhar.kit.tracing.TracingFacade.getInstance();
        this.circuitBreaker = CircuitBreakerFacade.getInstance();
        this.cache = CacheFacade.builder().cacheName("adhar-kit-default").build();
        this.health = HealthFacade.getInstance();
        this.messaging = MessagingFacade.getInstance();
        this.docs = ApiDocsFacade.getInstance();
        this.grpc = GrpcFacade.getInstance();
        this.persistence = PersistenceFacade.getInstance();
        this.security = SecurityFacade.getInstance();
        this.config = ConfigFacade.getInstance();

        log.info("Adhar Kit Facade initialized - 22 modules available (13 loaded, 9 lazy)");
    }

    public static AdharKitFacade getInstance() {
        if (instance == null) {
            synchronized (AdharKitFacade.class) {
                if (instance == null) {
                    instance = new AdharKitFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Gets the AI facade for multi-model AI integration.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return AI facade instance
     */
    public com.adhar.kit.ai.AiFacade getAi() {
        if (ai == null) {
            synchronized (this) {
                if (ai == null) {
                    ai = com.adhar.kit.ai.AiFacade.getInstance();
                    log.debug("AI facade initialized on demand");
                }
            }
        }
        return ai;
    }

    /**
     * Gets the Analytics facade for business analytics and event tracking.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return Analytics facade instance
     */
    public com.adhar.kit.analytics.AnalyticsFacade getAnalytics() {
        if (analytics == null) {
            synchronized (this) {
                if (analytics == null) {
                    analytics = com.adhar.kit.analytics.AnalyticsFacade.getInstance();
                    log.debug("Analytics facade initialized on demand");
                }
            }
        }
        return analytics;
    }

    /**
     * Gets the Kubernetes facade for cloud-native operations.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return Kubernetes facade instance
     */
    public com.adhar.kit.kubernetes.KubernetesFacade getKubernetes() {
        if (kubernetes == null) {
            synchronized (this) {
                if (kubernetes == null) {
                    kubernetes = com.adhar.kit.kubernetes.KubernetesFacade.getInstance();
                    log.debug("Kubernetes facade initialized on demand");
                }
            }
        }
        return kubernetes;
    }

    /**
     * Gets the Dapr facade for distributed application runtime.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return Dapr facade instance
     */
    public com.adhar.kit.dapr.DaprFacade getDapr() {
        if (dapr == null) {
            synchronized (this) {
                if (dapr == null) {
                    dapr = com.adhar.kit.dapr.DaprFacade.getInstance();
                    log.debug("Dapr facade initialized on demand");
                }
            }
        }
        return dapr;
    }

    /**
     * Gets the Core utilities facade.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return Core facade instance
     */
    public com.adhar.kit.core.CoreFacade getCore() {
        if (core == null) {
            synchronized (this) {
                if (core == null) {
                    core = com.adhar.kit.core.CoreFacade.getInstance();
                    log.debug("Core facade initialized on demand");
                }
            }
        }
        return core;
    }

    /**
     * Gets comprehensive information about all available modules.
     *
     * @return module information string with tiers
     */
    public String getModuleInfo() {
        return """
            Adhar Kit v1.0.0 - 22 Production-Ready Modules:
            
            TIER-1 (Core Foundation):
            - commons, resilience, metrics, tracing, logging, cache
            
            TIER-2 (Integration & Communication):
            - health, test-commons, messaging, docs, grpc
            
            TIER-3 (Enterprise & Advanced):
            - persistence, security, config, starter, ai, analytics,
              kubernetes, dapr, mers, core, graphql
            
            Framework Support: Spring Boot 4.0, Quarkus 3.6+, Micronaut 4.2+
            """;
    }

    /**
     * Gets the version of Adhar Kit.
     *
     * @return version string
     */
    public String getVersion() {
        return "1.0.0";
    }
}


