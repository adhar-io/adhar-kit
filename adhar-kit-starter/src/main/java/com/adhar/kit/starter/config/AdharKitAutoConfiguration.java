package com.adhar.kit.starter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
@Import(AdharKitAutoConfiguration.ModuleOrchestrationConfiguration.class)
public class AdharKitAutoConfiguration {

    private static final String VERSION = "1.0.0-SNAPSHOT";
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

        log.info("");
        log.info("===============================================================================");
        log.info("                       ADHAR KIT - Enterprise Framework                        ");
        log.info("===============================================================================");
        log.info("  Application   : {}", appName);
        log.info("  Version       : {}", VERSION);
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
         * Creates the AdharKitModuleRegistry for runtime module introspection.
         */
        @Bean
        @ConditionalOnMissingBean
        public AdharKitModuleRegistry adharKitModuleRegistry(
                AdharKitProperties properties,
                ApplicationContext applicationContext) {
            return new AdharKitModuleRegistry(properties, applicationContext);
        }
    }

    /**
     * Module registry for runtime introspection of Adhar Kit modules.
     */
    @Slf4j
    public static class AdharKitModuleRegistry {

        private final Map<String, ModuleInfo> modules = new ConcurrentHashMap<>();
        private final AdharKitProperties properties;
        private final ApplicationContext applicationContext;

        public AdharKitModuleRegistry(AdharKitProperties properties,
                                      ApplicationContext applicationContext) {
            this.properties = properties;
            this.applicationContext = applicationContext;
            initializeModuleRegistry();
        }

        private void initializeModuleRegistry() {
            var moduleConfig = properties.getModules();

            // Register core modules
            registerModule("logging", "Logging", moduleConfig.isLogging(),
                    "Structured logging with correlation IDs");
            registerModule("metrics", "Metrics", moduleConfig.isMetrics(),
                    "Micrometer metrics collection and export");
            registerModule("tracing", "Tracing", moduleConfig.isTracing(),
                    "Distributed tracing with correlation propagation");
            registerModule("resilience", "Resilience", moduleConfig.isResilience(),
                    "Circuit breaker, retry, rate limiter, bulkhead patterns");
            registerModule("security", "Security", moduleConfig.isSecurity(),
                    "Authentication, authorization, and security utilities");
            registerModule("persistence", "Persistence", moduleConfig.isPersistence(),
                    "JPA with auditing and multi-tenancy support");
            registerModule("cache", "Cache", moduleConfig.isCache(),
                    "Distributed caching with Kafka synchronization");
            registerModule("messaging", "Messaging", moduleConfig.isMessaging(),
                    "Async messaging with Kafka and RabbitMQ");
            registerModule("config", "Configuration", moduleConfig.isConfig(),
                    "Dynamic configuration with refresh and encryption");
            registerModule("health", "Health", moduleConfig.isHealth(),
                    "Health indicators for all services");
            registerModule("docs", "API Docs", moduleConfig.isDocs(),
                    "OpenAPI documentation generation");
            registerModule("ai", "AI/ML", moduleConfig.isAi(),
                    "Multi-provider AI integration");
            registerModule("analytics", "Analytics", moduleConfig.isAnalytics(),
                    "Product analytics and feature flags");
            registerModule("kubernetes", "Kubernetes", moduleConfig.isKubernetes(),
                    "Kubernetes-native deployment support");
            registerModule("dapr", "Dapr", moduleConfig.isDapr(),
                    "Dapr sidecar integration");
            registerModule("grpc", "gRPC", moduleConfig.isGrpc(),
                    "gRPC server and client support");
            registerModule("graphql", "GraphQL", moduleConfig.isGraphql(),
                    "GraphQL API support with query complexity limits");
            registerModule("batch", "Batch", moduleConfig.isBatch(),
                    "Spring Batch integration for batch processing");
            registerModule("notification", "Notification", moduleConfig.isNotification(),
                    "Multi-channel notifications (email, webhook, in-app)");
            registerModule("event-sourcing", "Event Sourcing", moduleConfig.isEventSourcing(),
                    "Event sourcing and CQRS patterns");
            registerModule("perf-profiler", "Perf Profiler", moduleConfig.isPerfProfiler(),
                    "Method-level performance profiling and bottleneck detection");

            log.debug("Module registry initialized with {} modules", modules.size());
        }

        private void registerModule(String id, String name, boolean enabled, String description) {
            modules.put(id, new ModuleInfo(id, name, enabled, description));
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
         * Checks if a module is enabled.
         */
        public boolean isModuleEnabled(String moduleId) {
            ModuleInfo info = modules.get(moduleId);
            return info != null && info.enabled();
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

