package com.adhar.kit.starter;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.logging.LoggingFacade;
import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.core.CoreFacade;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.docs.ApiDocsFacade;
import com.adhar.kit.grpc.GrpcFacade;
import com.adhar.kit.kubernetes.KubernetesFacade;
import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.metrics.MetricsFacade;
import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.resilience.CircuitBreakerFacade;
import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.health.HealthFacade;
import com.adhar.kit.tracing.TracingFacade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Unified Adhar Facade - Single entry point for all Adhar Kit modules.
 *
 * <p>This facade provides convenient access to all 21 Adhar Kit modules through
 * a single interface, simplifying integration and reducing boilerplate code.</p>
 *
 * <p><b>Usage Options:</b></p>
 *
 * <p><b>Option 1: Constructor Injection (Recommended)</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final AdharFacade adhar;
 *
 *     public OrderService(AdharFacade adhar) {
 *         this.adhar = adhar;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Option 2: Field Injection</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     @Autowired
 *     private AdharFacade adhar;
 * }
 * }</pre>
 *
 * <p><b>Option 3: Static getInstance() (Non-Spring contexts)</b></p>
 * <pre>{@code
 * public class OrderService {
 *     private final AdharFacade adhar = AdharFacade.getInstance();
 * }
 * }</pre>
 *
 * <p><b>Full Usage Example:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     private final AdharFacade adhar;
 *
 *     public OrderService(AdharFacade adhar) {
 *         this.adhar = adhar;
 *     }
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
 * @author Tapas Jena
 * @since 1.0.0
 */
@Slf4j
@Getter
@Component
public class AdharFacade {

    private static volatile AdharFacade instance;

    // Core facades
    private final LoggingFacade logging;
    private final MetricsFacade metrics;
    private final TracingFacade tracing;
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
    private volatile AiFacade ai;
    private volatile AnalyticsFacade analytics;
    private volatile KubernetesFacade kubernetes;
    private volatile DaprFacade dapr;
    private volatile CoreFacade core;

    /**
     * Constructs the AdharFacade. This constructor is used by Spring for dependency injection.
     * It also sets the static instance for non-Spring access via getInstance().
     */
    public AdharFacade() {
        log.info("Initializing Adhar Facade - unified access to all 21 modules");

        // Initialize core facades eagerly (most commonly used)
        this.logging = LoggingFacade.getLogger(AdharFacade.class);
        this.metrics = MetricsFacade.getInstance();
        this.tracing = TracingFacade.getInstance();
        this.circuitBreaker = CircuitBreakerFacade.getInstance();
        this.cache = CacheFacade.builder().cacheName("adhar-default").build();
        this.health = HealthFacade.getInstance();
        this.messaging = MessagingFacade.getInstance();
        this.docs = ApiDocsFacade.getInstance();
        this.grpc = GrpcFacade.getInstance();
        this.persistence = PersistenceFacade.getInstance();
        this.security = SecurityFacade.getInstance();
        this.config = ConfigFacade.getInstance();

        // Set static instance for non-Spring access
        if (instance == null) {
            instance = this;
        }

        log.info("Adhar Facade initialized - 21 modules available (12 loaded, 5 lazy)");
    }

    /**
     * Gets the singleton instance of AdharFacade.
     * <p>
     * In Spring contexts, prefer using constructor or field injection instead.
     * This method is provided for non-Spring contexts or utility classes.
     * </p>
     *
     * @return the AdharFacade singleton instance
     */
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

    /**
     * Gets the AI facade for multi-model AI integration.
     * Lazy initialization - only loaded when first accessed.
     *
     * @return AI facade instance
     */
    public AiFacade getAi() {
        if (ai == null) {
            synchronized (this) {
                if (ai == null) {
                    ai = AiFacade.getInstance();
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
    public AnalyticsFacade getAnalytics() {
        if (analytics == null) {
            synchronized (this) {
                if (analytics == null) {
                    analytics = AnalyticsFacade.getInstance();
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
    public KubernetesFacade getKubernetes() {
        if (kubernetes == null) {
            synchronized (this) {
                if (kubernetes == null) {
                    kubernetes = KubernetesFacade.getInstance();
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
    public DaprFacade getDapr() {
        if (dapr == null) {
            synchronized (this) {
                if (dapr == null) {
                    dapr = DaprFacade.getInstance();
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
    public CoreFacade getCore() {
        if (core == null) {
            synchronized (this) {
                if (core == null) {
                    core = CoreFacade.getInstance();
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
        return String.format("""
            Adhar Kit v%s - 21 Production-Ready Modules:
            
            TIER-1 (Core Foundation):
            - commons, resilience, metrics, tracing, logging, cache
            
            TIER-2 (Integration & Communication):
            - health, test-commons, messaging, docs, grpc
            
            TIER-3 (Enterprise & Advanced):
            - persistence, security, config, starter, ai, analytics,
              kubernetes, dapr, core
            
            Framework Support: Spring Boot 4.0, Quarkus 3.6+, Micronaut 4.2+
            """, getVersion());
    }

    /**
     * Gets the version of Adhar Kit dynamically from various sources.
     * <p>
     * Tries the following in order:
     * <ol>
     *   <li>Package implementation version (from JAR manifest)</li>
     *   <li>Package specification version (from JAR manifest)</li>
     *   <li>Maven pom.properties file (automatically generated)</li>
     *   <li>Custom adhar-kit.properties file</li>
     *   <li>Default fallback value</li>
     * </ol>
     *
     * @return version string
     */
    public String getVersion() {
        // Try to get version from package (set by Maven/Gradle during build)
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null && !version.isEmpty()) {
            return version;
        }

        // Try to get version from specification version
        version = getClass().getPackage().getSpecificationVersion();
        if (version != null && !version.isEmpty()) {
            return version;
        }

        // Try to read from Maven's auto-generated pom.properties
        try {
            var props = new java.util.Properties();
            var stream = getClass().getResourceAsStream(
                "/META-INF/maven/com.adhar.kit/adhar-kit-starter/pom.properties");
            if (stream != null) {
                props.load(stream);
                stream.close();
                version = props.getProperty("version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read version from pom.properties", e);
        }

        // Fallback to reading from custom properties file if available
        try {
            var props = new java.util.Properties();
            var stream = getClass().getResourceAsStream("/META-INF/adhar-kit.properties");
            if (stream != null) {
                props.load(stream);
                stream.close();
                version = props.getProperty("adhar-kit.version");
                if (version != null && !version.isEmpty() && !version.contains("${")) {
                    return version;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read version from adhar-kit.properties", e);
        }

        // Default fallback
        return "1.0.0-SNAPSHOT";
    }
}

