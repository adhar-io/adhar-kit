package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.config.audit.ConfigChangeAuditPublisher;
import com.adhar.kit.config.encryption.PropertyEncryptor;
import com.adhar.kit.config.endpoint.AdharConfigEndpoint;
import com.adhar.kit.config.featureflag.FeatureFlag;
import com.adhar.kit.config.featureflag.FeatureFlagService;
import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.properties.ConfigProperties;
import com.adhar.kit.config.refresh.ConfigRefreshManager;
import com.adhar.kit.config.refresh.RefreshConfigBeanPostProcessor;
import com.adhar.kit.config.source.impl.ConfigMapConfigSource;
import com.adhar.kit.config.source.impl.ConsulConfigSource;
import com.adhar.kit.config.source.impl.EnvironmentConfigSource;
import com.adhar.kit.config.source.impl.FileConfigSource;
import com.adhar.kit.config.source.impl.VaultConfigSource;
import com.adhar.kit.config.validator.ConfigValidationRunner;
import com.adhar.kit.config.validator.ConfigValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Auto-configuration for Adhar Config module.
 *
 * <p>Automatically configures configuration management with multiple sources,
 * encryption support, and dynamic refresh capabilities.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Environment variable configuration</li>
 *   <li>File-based configuration (YAML, Properties)</li>
 *   <li>Dynamic configuration refresh with scheduling</li>
 *   <li>Property encryption/decryption (AES, RSA)</li>
 *   <li>Configuration validation</li>
 *   <li>Spring Cloud Config integration</li>
 *   <li>HashiCorp Vault integration</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b></p>
 * <pre>{@code
 * adhar:
 *   config:
 *     enabled: true
 *     sources:
 *       file:
 *         type: file
 *         location: classpath:application.yml
 *         priority: 100
 *       env:
 *         type: environment
 *         priority: 200
 *     refresh:
 *       enabled: true
 *       interval: 30s
 *     encryption:
 *       enabled: true
 *       algorithm: AES
 *       key-location: classpath:encryption.key
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(afterName = "com.adhar.kit.dapr.config.DaprAutoConfiguration")
@EnableConfigurationProperties(ConfigProperties.class)
@ConditionalOnProperty(prefix = "adhar.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({ConfigAutoConfiguration.RefreshConfiguration.class, ConfigAutoConfiguration.EncryptionConfiguration.class})
@RequiredArgsConstructor
public class ConfigAutoConfiguration {

    private final ConfigProperties properties;
    private final Environment environment;

    @PostConstruct
    public void logConfigurationStatus() {
        log.info("Adhar Config module initialized - enabled: {}, refresh: {}, encryption: {}",
                properties.isEnabled(),
                properties.getRefresh().isEnabled(),
                properties.getEncryption().isEnabled());
    }

    /**
     * Creates the central ConfigManager bean.
     *
     * <p>Configures multiple sources based on properties with priority-based merging.
     * When encryption is enabled, the PropertyEncryptor is attached so ENC(...) values
     * are transparently decrypted in the read path.</p>
     *
     * @param encryptorProvider optional PropertyEncryptor (present when encryption is enabled)
     * @return configured ConfigManager instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigManager configManager(ObjectProvider<PropertyEncryptor> encryptorProvider,
                                       ObjectProvider<com.adhar.kit.config.source.ConfigSource> contributedSources) {
        ConfigManager manager = new ConfigManager();

        // Add environment variable source (highest priority by default)
        EnvironmentConfigSource envSource = new EnvironmentConfigSource();
        manager.addSource(envSource);
        log.debug("Added EnvironmentConfigSource with priority {}", envSource.getPriority());

        // Add configured sources
        if (properties.getSources() != null) {
            properties.getSources().forEach((name, sourceConfig) -> {
                if (sourceConfig.isEnabled()) {
                    addConfigSource(manager, name, sourceConfig);
                }
            });
        }

        // Add sources contributed as beans (e.g. the Dapr configuration/secret
        // sources, or application-defined custom sources)
        contributedSources.orderedStream().forEach(source -> {
            manager.addSource(source);
            log.debug("Added contributed {} config source with priority {}",
                    source.getType(), source.getPriority());
        });

        // Attach encryptor for transparent decryption when encryption is enabled
        encryptorProvider.ifAvailable(encryptor -> {
            manager.setEncryptor(encryptor);
            log.info("ConfigManager linked to PropertyEncryptor for transparent decryption");
        });

        log.info("ConfigManager initialized with {} sources", manager.getHealthStatus().size());
        return manager;
    }

    /**
     * Creates the ConfigFacade singleton accessor bean, linked to the ConfigManager.
     *
     * <p>Provides a simplified interface for configuration access throughout the application.
     * All facade lookups and refreshes delegate to the ConfigManager.</p>
     *
     * @param configManager the config manager to delegate to
     * @return configured ConfigFacade instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigFacade configFacade(ConfigManager configManager) {
        ConfigFacade facade = ConfigFacade.getInstance();
        facade.setConfigManager(configManager);
        log.info("ConfigFacade initialized and linked to ConfigManager");
        return facade;
    }

    /**
     * Creates the BeanPostProcessor that powers the {@code @RefreshConfig} annotation.
     *
     * <p>Static so the post-processor is created early without triggering premature
     * initialization of this configuration class.</p>
     *
     * @param configManagerProvider lazy provider for the ConfigManager
     * @return RefreshConfigBeanPostProcessor instance
     */
    @Bean
    @ConditionalOnMissingBean
    public static RefreshConfigBeanPostProcessor refreshConfigBeanPostProcessor(
            ObjectProvider<ConfigManager> configManagerProvider) {
        return new RefreshConfigBeanPostProcessor(configManagerProvider::getObject);
    }

    /**
     * Creates the ConfigValidator bean for validating configuration properties.
     *
     * @return ConfigValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.config.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigValidator configValidator() {
        ConfigValidator validator = new ConfigValidator();
        log.info("ConfigValidator initialized - fail-on-error: {}", properties.getValidation().isFailOnError());
        return validator;
    }

    /**
     * Creates the startup validation gate.
     *
     * <p>Runs ConfigValidator rules (including required/pattern rules declared under
     * {@code adhar.config.validation}) against the merged configuration during startup
     * and fails fast when {@code fail-on-error} is true.</p>
     *
     * @param configManager the config manager holding merged configuration
     * @param configValidator the validator carrying the rules
     * @return ConfigValidationRunner instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.config.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigValidationRunner configValidationRunner(ConfigManager configManager,
                                                         ConfigValidator configValidator) {
        return new ConfigValidationRunner(configManager, configValidator, properties.getValidation());
    }

    /**
     * Creates the FeatureFlagService, seeded from configured flag definitions.
     *
     * @return FeatureFlagService instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.config.feature-flags", name = "enabled", havingValue = "true")
    public FeatureFlagService featureFlagService() {
        FeatureFlagService service = new FeatureFlagService();
        properties.getFeatureFlags().getFlags().forEach((flagName, cfg) ->
                service.setFlag(new FeatureFlag(
                        flagName,
                        cfg.isEnabled(),
                        cfg.getRolloutPercentage(),
                        new LinkedHashSet<>(cfg.getAllowList()),
                        new LinkedHashSet<>(cfg.getDenyList()))));
        log.info("FeatureFlagService initialized with {} flags", service.getFlags().size());
        return service;
    }

    /**
     * Creates the config-change audit publisher and registers it on the
     * ConfigManager so every property change is emitted as a
     * {@link com.adhar.kit.config.audit.ConfigChangeEvent}.
     *
     * @param configManager the config manager to observe
     * @param eventPublisher Spring application event publisher
     * @return ConfigChangeAuditPublisher instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.config.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConfigChangeAuditPublisher configChangeAuditPublisher(ConfigManager configManager,
                                                                 ApplicationEventPublisher eventPublisher) {
        ConfigProperties.AuditConfig auditConfig = properties.getAudit();
        ConfigChangeAuditPublisher publisher = new ConfigChangeAuditPublisher(
                eventPublisher, auditConfig.getSecretKeyPatterns(), auditConfig.getMaxEvents());
        configManager.addChangeListener(publisher);
        log.info("Config-change audit publisher registered (maxEvents={})", auditConfig.getMaxEvents());
        return publisher;
    }

    /**
     * Adds a configuration source based on source config.
     */
    private void addConfigSource(ConfigManager manager, String name, ConfigProperties.SourceConfig sourceConfig) {
        String type = sourceConfig.getType();
        int priority = sourceConfig.getPriority();

        try {
            switch (type.toLowerCase()) {
                case "file":
                    String location = sourceConfig.getLocation();
                    if (location != null && !location.isEmpty()) {
                        manager.addSource(new FileConfigSource(location, priority));
                        log.debug("Added FileConfigSource '{}' from {} with priority {}", name, location, priority);
                    }
                    break;

                case "environment":
                    // Already added by default
                    log.debug("Environment source already configured");
                    break;

                case "vault": {
                    Map<String, String> auth = sourceConfig.getAuth();
                    String token = auth.get("token");
                    String namespace = auth.get("namespace");
                    int kvVersion = auth.containsKey("kvVersion")
                            ? Integer.parseInt(auth.get("kvVersion")) : 2;
                    VaultConfigSource vault = new VaultConfigSource(
                            sourceConfig.getLocation(), sourceConfig.getPrefix(),
                            namespace, token, priority, true, kvVersion);
                    manager.addSource(vault);
                    log.debug("Added VaultConfigSource '{}' from {} path {} with priority {}",
                            name, sourceConfig.getLocation(), sourceConfig.getPrefix(), priority);
                    break;
                }

                case "consul": {
                    String token = sourceConfig.getAuth().get("token");
                    ConsulConfigSource consul = new ConsulConfigSource(
                            sourceConfig.getLocation(), sourceConfig.getPrefix(), token, priority, true);
                    manager.addSource(consul);
                    log.debug("Added ConsulConfigSource '{}' from {} prefix {} with priority {}",
                            name, sourceConfig.getLocation(), sourceConfig.getPrefix(), priority);
                    break;
                }

                case "configmap":
                case "k8s": {
                    boolean watch = Boolean.parseBoolean(
                            sourceConfig.getAuth().getOrDefault("watch", "false"));
                    ConfigMapConfigSource configMap = new ConfigMapConfigSource(
                            sourceConfig.getLocation(), priority, watch);
                    manager.addSource(configMap);
                    log.debug("Added ConfigMapConfigSource '{}' from {} (watch={}) with priority {}",
                            name, sourceConfig.getLocation(), watch, priority);
                    break;
                }

                case "springcloud":
                    log.info("Source type '{}' handled by Spring Cloud Config integration", type);
                    break;

                default:
                    log.warn("Unknown config source type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to add config source '{}' of type '{}'", name, type, e);
        }
    }

    /**
     * Refresh configuration for dynamic config updates.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.config.refresh", name = "enabled", havingValue = "true")
    @EnableScheduling
    static class RefreshConfiguration {

        /**
         * Creates the ConfigRefreshManager for scheduled refresh.
         *
         * @param configManager the config manager
         * @param properties the config properties
         * @return ConfigRefreshScheduler instance
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnClass(name = "org.springframework.cloud.context.refresh.ContextRefresher")
        public ConfigRefreshScheduler configRefreshScheduler(
                ConfigManager configManager,
                ConfigProperties properties) {
            return new ConfigRefreshScheduler(configManager, properties);
        }
    }

    /**
     * Scheduled configuration refresh handler.
     */
    @Slf4j
    @RequiredArgsConstructor
    public static class ConfigRefreshScheduler {

        private final ConfigManager configManager;
        private final ConfigProperties properties;

        /**
         * Scheduled refresh task.
         */
        @org.springframework.scheduling.annotation.Scheduled(
                fixedDelayString = "${adhar.config.refresh.interval:PT30S}",
                initialDelayString = "${adhar.config.refresh.initial-delay:PT10S}")
        public void scheduledRefresh() {
            if (properties.getRefresh().isEnabled()) {
                try {
                    log.debug("Executing scheduled configuration refresh");
                    configManager.refreshAll();
                } catch (Exception e) {
                    int maxRetries = properties.getRefresh().getMaxRetries();
                    log.warn("Scheduled config refresh failed, will retry (max {} retries)", maxRetries, e);
                }
            }
        }
    }

    /**
     * Encryption configuration for sensitive properties.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.config.encryption", name = "enabled", havingValue = "true")
    static class EncryptionConfiguration {

        /**
         * Creates the PropertyEncryptor for encrypting/decrypting config values.
         *
         * @param properties the config properties
         * @param environment Spring environment for key resolution
         * @return PropertyEncryptor instance
         */
        @Bean
        @ConditionalOnMissingBean
        public PropertyEncryptor propertyEncryptor(ConfigProperties properties, Environment environment) {
            ConfigProperties.EncryptionConfig encryptionConfig = properties.getEncryption();

            // Get encryption key from environment or key location
            String encryptionKey = resolveEncryptionKey(encryptionConfig, environment);

            if (encryptionKey == null || encryptionKey.isEmpty()) {
                throw new IllegalStateException(
                        "Encryption is enabled but no key provided. " +
                        "Set adhar.config.encryption.key or adhar.config.encryption.key-location");
            }

            String algorithm = encryptionConfig.getAlgorithm();
            log.info("PropertyEncryptor initialized with algorithm: {} (AES/GCM v2 format, PBKDF2 key derivation)",
                    algorithm);

            return new PropertyEncryptor(encryptionKey, algorithm,
                    encryptionConfig.getSalt(), encryptionConfig.getKeyIterations());
        }

        /**
         * Resolves the encryption key from environment or file.
         */
        private String resolveEncryptionKey(ConfigProperties.EncryptionConfig config, Environment environment) {
            // First try direct key from environment
            String key = environment.getProperty("adhar.config.encryption.key");
            if (key != null && !key.isEmpty()) {
                return key;
            }

            // Try key from ADHAR_CONFIG_ENCRYPTION_KEY env var
            key = System.getenv("ADHAR_CONFIG_ENCRYPTION_KEY");
            if (key != null && !key.isEmpty()) {
                return key;
            }

            // Key location would need file reading logic
            String keyLocation = config.getKeyLocation();
            if (keyLocation != null && !keyLocation.isEmpty()) {
                log.info("Encryption key location configured: {} - implement key file loading", keyLocation);
            }

            return null;
        }
    }

    /**
     * Dapr configuration/secret sources, contributed as {@code ConfigSource}
     * beans and picked up by {@link #configManager}. Active only when the
     * optional {@code adhar-kit-dapr} module is on the classpath and Dapr is
     * explicitly enabled ({@code adhar.dapr.enabled=true}); each source can be
     * individually disabled via {@code adhar.config.dapr.*}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.adhar.kit.dapr.DaprFacade")
    @ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true")
    static class DaprConfigSourcesConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "daprConfigSource")
        @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.adhar.kit.dapr.DaprFacade.class)
        @ConditionalOnProperty(prefix = "adhar.config.dapr", name = "config-enabled",
                havingValue = "true", matchIfMissing = true)
        public com.adhar.kit.config.source.ConfigSource daprConfigSource(
                com.adhar.kit.dapr.DaprFacade daprFacade, ConfigProperties properties) {
            ConfigProperties.DaprConfig dapr = properties.getDapr();
            log.info("Adding Dapr configuration source (store '{}')", dapr.getConfigStore());
            return new com.adhar.kit.config.source.impl.DaprConfigSource(daprFacade,
                    dapr.getConfigStore(), dapr.getKeys(), dapr.getConfigPriority(), dapr.isSubscribe());
        }

        @Bean
        @ConditionalOnMissingBean(name = "daprSecretConfigSource")
        @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.adhar.kit.dapr.DaprFacade.class)
        @ConditionalOnProperty(prefix = "adhar.config.dapr", name = "secrets-enabled",
                havingValue = "true", matchIfMissing = true)
        public com.adhar.kit.config.source.ConfigSource daprSecretConfigSource(
                com.adhar.kit.dapr.DaprFacade daprFacade, ConfigProperties properties) {
            ConfigProperties.DaprConfig dapr = properties.getDapr();
            log.info("Adding Dapr secret source (store '{}')", dapr.getSecretStore());
            return new com.adhar.kit.config.source.impl.DaprSecretConfigSource(daprFacade,
                    dapr.getSecretStore(), dapr.getSecretPriority());
        }
    }

    /**
     * Spring Cloud Config integration (conditional on classpath).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.config.client.ConfigClientProperties")
    @ConditionalOnProperty(prefix = "spring.cloud.config", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SpringCloudConfigConfiguration {

        @PostConstruct
        public void logSpringCloudConfigIntegration() {
            log.info("Spring Cloud Config integration enabled - config server will be used as source");
        }
    }

    /**
     * Registers the {@code adharconfig} actuator endpoint when Spring Boot
     * Actuator is on the classpath and the endpoint is enabled.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnProperty(prefix = "adhar.config.endpoint", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ConfigEndpointConfiguration {

        /**
         * Creates the config actuator endpoint.
         *
         * @param configManager the config manager
         * @param featureFlagService optional feature-flag service
         * @param auditPublisher optional audit publisher
         * @return AdharConfigEndpoint instance
         */
        @Bean
        @ConditionalOnMissingBean
        public AdharConfigEndpoint adharConfigEndpoint(
                ConfigManager configManager,
                ObjectProvider<FeatureFlagService> featureFlagService,
                ObjectProvider<ConfigChangeAuditPublisher> auditPublisher) {
            log.info("Initializing 'adharconfig' actuator endpoint");
            return new AdharConfigEndpoint(configManager, featureFlagService, auditPublisher);
        }
    }
}

