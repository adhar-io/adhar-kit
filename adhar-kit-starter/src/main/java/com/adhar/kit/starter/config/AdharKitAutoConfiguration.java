package com.adhar.kit.starter.config;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.starter.AdharKitVersion;
import com.adhar.kit.starter.AdharModuleAccess;
import com.adhar.kit.starter.actuator.AdharKitEndpoint;
import com.adhar.kit.starter.lifecycle.AdharGracefulShutdown;
import com.adhar.kit.starter.lifecycle.AdharShutdownHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-configuration for Adhar Kit Starter.
 *
 * <p>Aggregates all Adhar Kit modules and provides centralized orchestration
 * for easy integration into Spring Boot applications.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic module discovery and initialization</li>
 *   <li>Module registry for runtime introspection</li>
 *   <li>Startup audit logging with module status</li>
 *   <li>Health integration across all modules</li>
 *   <li>Centralized configuration management</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   kit:
 *     enabled: true
 *     profile: production
 *     auto-configure-all: true
 *     modules:
 *       persistence: true
 *       cache: true
 *       security: true
 *       resilience: true
 *       tracing: true
 *       metrics: true
 *       health: true
 *       ai: false
 *       analytics: false
 * }</pre>
 *
 * @author Tapas Jena
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AdharKitProperties.class)
@ConditionalOnProperty(prefix = "adhar.kit", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        AdharKitAutoConfiguration.ModuleOrchestrationConfiguration.class,
        AdharKitAutoConfiguration.AdharKitEndpointConfiguration.class
})
public class AdharKitAutoConfiguration {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AdharKitProperties properties;
    private final Environment environment;
    private final ApplicationContext applicationContext;

    public AdharKitAutoConfiguration(AdharKitProperties properties,
                                      Environment environment,
                                      ApplicationContext applicationContext) {
        this.properties = properties;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        String startTime = FORMATTER.format(Instant.now());
        String appName = Optional.ofNullable(properties.getApplicationName())
                .orElse(environment.getProperty("spring.application.name", "adhar-app"));
        String activeProfiles = String.join(", ", environment.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = "default";
        }

        Framework runtime = FrameworkDetector.detect();

        log.info("");
        log.info("===============================================================================");
        log.info("                       ADHAR KIT - Enterprise Framework                        ");
        log.info("===============================================================================");
        log.info("  Application   : {}", appName);
        log.info("  Version       : {}", AdharKitVersion.getVersion());
        log.info("  Runtime       : {}", runtime);
        log.info("  Profile       : {}", properties.getProfile());
        log.info("  Spring Profile: {}", activeProfiles);
        log.info("  Started at    : {}", startTime);
        log.info("-------------------------------------------------------------------------------");
        log.info("  Active Modules:");

        var modules = properties.getModules();
        List<String> enabledModules = new ArrayList<>();
        List<String> disabledModules = new ArrayList<>();

        checkModule("Logging", modules.isLogging(), enabledModules, disabledModules);
        checkModule("Metrics", modules.isMetrics(), enabledModules, disabledModules);
        checkModule("Tracing", modules.isTracing(), enabledModules, disabledModules);
        checkModule("Resilience", modules.isResilience(), enabledModules, disabledModules);
        checkModule("Security", modules.isSecurity(), enabledModules, disabledModules);
        checkModule("Persistence", modules.isPersistence(), enabledModules, disabledModules);
        checkModule("Cache", modules.isCache(), enabledModules, disabledModules);
        checkModule("Messaging", modules.isMessaging(), enabledModules, disabledModules);
        checkModule("Configuration", modules.isConfig(), enabledModules, disabledModules);
        checkModule("Health", modules.isHealth(), enabledModules, disabledModules);
        checkModule("API Docs", modules.isDocs(), enabledModules, disabledModules);
        checkModule("AI/ML", modules.isAi(), enabledModules, disabledModules);
        checkModule("Analytics", modules.isAnalytics(), enabledModules, disabledModules);
        checkModule("Kubernetes", modules.isKubernetes(), enabledModules, disabledModules);
        checkModule("Dapr", modules.isDapr(), enabledModules, disabledModules);
        checkModule("gRPC", modules.isGrpc(), enabledModules, disabledModules);
        checkModule("GraphQL", modules.isGraphql(), enabledModules, disabledModules);
        checkModule("Batch", modules.isBatch(), enabledModules, disabledModules);
        checkModule("Notification", modules.isNotification(), enabledModules, disabledModules);
        checkModule("Event Sourcing", modules.isEventSourcing(), enabledModules, disabledModules);
        checkModule("Perf Profiler", modules.isPerfProfiler(), enabledModules, disabledModules);
        checkModule("Rewrite", modules.isRewrite(), enabledModules, disabledModules);

        // Log enabled modules
        for (String module : enabledModules) {
            log.info("    [+] {}", module);
        }

        // Log disabled modules (compact)
        if (!disabledModules.isEmpty()) {
            log.info("  Disabled: {}", String.join(", ", disabledModules));
        }

        log.info("-------------------------------------------------------------------------------");
        log.info("  {} modules enabled, {} disabled",
                enabledModules.size(), disabledModules.size());
        log.info("===============================================================================");
        log.info("");

        // Log module initialization to audit log
        logModuleAudit(appName, enabledModules, disabledModules);
    }

    private void checkModule(String name, boolean enabled,
                            List<String> enabledList, List<String> disabledList) {
        if (enabled) {
            enabledList.add(name);
        } else {
            disabledList.add(name);
        }
    }

    private void logModuleAudit(String appName, List<String> enabled, List<String> disabled) {
        // Structured audit logging for module initialization
        log.debug("ADHAR_KIT_INIT app={} enabled_modules=[{}] disabled_modules=[{}] total_enabled={} total_disabled={}",
                appName,
                String.join(",", enabled),
                String.join(",", disabled),
                enabled.size(),
                disabled.size());
    }

    /**
     * Module orchestration configuration.
     */
    @Configuration(proxyBeanMethods = false)
    static class ModuleOrchestrationConfiguration {

        /**
         * Creates the {@link AdharModuleAccess} - the single source of truth for
         * module enablement, consulted by both {@link AdharKitModuleRegistry} and,
         * via {@code SpringAdharFacadeConfiguration}, {@code AdharFacade}.
         */
        @Bean
        @ConditionalOnMissingBean
        public AdharModuleAccess adharModuleAccess(AdharKitProperties properties) {
            return buildModuleAccess(properties.getModules());
        }

        /**
         * Creates the AdharKitModuleRegistry for runtime module introspection.
         */
        @Bean
        @ConditionalOnMissingBean
        public AdharKitModuleRegistry adharKitModuleRegistry(
                AdharModuleAccess moduleAccess,
                ApplicationContext applicationContext) {
            return new AdharKitModuleRegistry(moduleAccess, applicationContext);
        }

        /**
         * Creates the coordinated graceful-shutdown orchestrator. It gathers all
         * {@link AdharShutdownHook} beans in the context (and merges in any
         * discovered via the {@link java.util.ServiceLoader}) and, on context
         * close, drives them through the stop-accepting-work / drain / close
         * phases in a deliberate order.
         *
         * @param shutdownHooks the module shutdown hooks contributed as beans
         * @return the graceful-shutdown lifecycle bean
         */
        @Bean
        @ConditionalOnMissingBean
        public AdharGracefulShutdown adharGracefulShutdown(ObjectProvider<AdharShutdownHook> shutdownHooks) {
            return new AdharGracefulShutdown(shutdownHooks.orderedStream().toList());
        }
    }

    /**
     * Maps {@link AdharKitProperties.Modules} onto the module-id vocabulary
     * shared with {@link AdharFacade}'s accessors (see its {@code MOD_*}
     * constants) and {@link AdharKitModuleRegistry}.
     */
    private static AdharModuleAccess buildModuleAccess(AdharKitProperties.Modules m) {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("logging", m.isLogging());
        toggles.put("metrics", m.isMetrics());
        toggles.put("tracing", m.isTracing());
        toggles.put("resilience", m.isResilience());
        toggles.put("security", m.isSecurity());
        toggles.put("persistence", m.isPersistence());
        toggles.put("cache", m.isCache());
        toggles.put("messaging", m.isMessaging());
        toggles.put("config", m.isConfig());
        toggles.put("health", m.isHealth());
        toggles.put("docs", m.isDocs());
        toggles.put("ai", m.isAi());
        toggles.put("analytics", m.isAnalytics());
        toggles.put("kubernetes", m.isKubernetes());
        toggles.put("dapr", m.isDapr());
        toggles.put("grpc", m.isGrpc());
        toggles.put("graphql", m.isGraphql());
        toggles.put("batch", m.isBatch());
        toggles.put("notification", m.isNotification());
        toggles.put("event-sourcing", m.isEventSourcing());
        toggles.put("perf-profiler", m.isPerfProfiler());
        toggles.put("rewrite", m.isRewrite());
        return AdharModuleAccess.of(toggles);
    }

    /**
     * Actuator wiring for {@link AdharKitEndpoint}. Only active when Spring Boot
     * Actuator is on the classpath, keeping it an optional dependency.
     *
     * <p>Note: intentionally does not use {@code @ConditionalOnBean(AdharKitModuleRegistry.class)}
     * here - that registry is registered by a sibling nested configuration
     * ({@link ModuleOrchestrationConfiguration}) imported in the very same
     * batch, and {@code @ConditionalOnBean} is not guaranteed to see bean
     * definitions from configuration classes processed in the same round.
     * The registry is instead taken as a plain constructor dependency, which
     * Spring resolves normally during bean instantiation. Since this whole
     * class is only imported alongside {@code ModuleOrchestrationConfiguration}
     * (both gated by {@code AdharKitAutoConfiguration}'s own
     * {@code adhar.kit.enabled} condition), the registry is always available
     * when this configuration is active.</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    static class AdharKitEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public AdharKitEndpoint adharKitEndpoint(AdharKitModuleRegistry registry) {
            return new AdharKitEndpoint(registry);
        }
    }

    /**
     * Module registry for runtime introspection of Adhar Kit modules.
     */
    @Slf4j
    public static class AdharKitModuleRegistry {

        private final Map<String, ModuleInfo> modules = new ConcurrentHashMap<>();
        private final AdharModuleAccess moduleAccess;
        private final ApplicationContext applicationContext;

        public AdharKitModuleRegistry(AdharModuleAccess moduleAccess,
                                      ApplicationContext applicationContext) {
            this.moduleAccess = moduleAccess;
            this.applicationContext = applicationContext;
            initializeModuleRegistry();
        }

        private void initializeModuleRegistry() {
            // Register core modules - "enabled" is always sourced from the shared
            // AdharModuleAccess so this registry and AdharFacade never disagree.
            registerModule("logging", "Logging",
                    "Structured logging with correlation IDs");
            registerModule("metrics", "Metrics",
                    "Micrometer metrics collection and export");
            registerModule("tracing", "Tracing",
                    "Distributed tracing with correlation propagation");
            registerModule("resilience", "Resilience",
                    "Circuit breaker, retry, rate limiter, bulkhead patterns");
            registerModule("security", "Security",
                    "Authentication, authorization, and security utilities");
            registerModule("persistence", "Persistence",
                    "JPA with auditing and multi-tenancy support");
            registerModule("cache", "Cache",
                    "Distributed caching with Kafka synchronization");
            registerModule("messaging", "Messaging",
                    "Async messaging with Kafka and RabbitMQ");
            registerModule("config", "Configuration",
                    "Dynamic configuration with refresh and encryption");
            registerModule("health", "Health",
                    "Health indicators for all services");
            registerModule("docs", "API Docs",
                    "OpenAPI documentation generation");
            registerModule("ai", "AI/ML",
                    "Multi-provider AI integration");
            registerModule("analytics", "Analytics",
                    "Product analytics and feature flags");
            registerModule("kubernetes", "Kubernetes",
                    "Kubernetes-native deployment support");
            registerModule("dapr", "Dapr",
                    "Dapr sidecar integration");
            registerModule("grpc", "gRPC",
                    "gRPC server and client support");
            registerModule("graphql", "GraphQL",
                    "GraphQL API support with query complexity limits");
            registerModule("batch", "Batch",
                    "Spring Batch integration for batch processing");
            registerModule("notification", "Notification",
                    "Multi-channel notifications (email, webhook, in-app)");
            registerModule("event-sourcing", "Event Sourcing",
                    "Event sourcing and CQRS patterns");
            registerModule("perf-profiler", "Perf Profiler",
                    "Method-level performance profiling and bottleneck detection");
            registerModule("rewrite", "Rewrite",
                    "Automated codebase modernization with OpenRewrite recipes");

            log.debug("Module registry initialized with {} modules", modules.size());
        }

        private void registerModule(String id, String name, String description) {
            modules.put(id, new ModuleInfo(id, name, moduleAccess.isEnabled(id), description));
        }

        /**
         * Gets all registered modules.
         */
        public Collection<ModuleInfo> getAllModules() {
            return Collections.unmodifiableCollection(modules.values());
        }

        /**
         * Gets enabled modules.
         */
        public List<ModuleInfo> getEnabledModules() {
            return modules.values().stream()
                    .filter(ModuleInfo::enabled)
                    .toList();
        }

        /**
         * Gets disabled modules.
         */
        public List<ModuleInfo> getDisabledModules() {
            return modules.values().stream()
                    .filter(m -> !m.enabled())
                    .toList();
        }

        /**
         * Checks if a module is enabled. Delegates to the same
         * {@link AdharModuleAccess} that gates {@code AdharFacade} sub-facade
         * access, so the two never disagree.
         */
        public boolean isModuleEnabled(String moduleId) {
            return moduleAccess.isEnabled(moduleId);
        }

        /**
         * Gets module info by ID.
         */
        public Optional<ModuleInfo> getModule(String moduleId) {
            return Optional.ofNullable(modules.get(moduleId));
        }

        /**
         * Module information record.
         */
        public record ModuleInfo(
                String id,
                String name,
                boolean enabled,
                String description
        ) {}
    }
}

