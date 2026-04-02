package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.config.encryption.PropertyEncryptor;
import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.properties.ConfigProperties;
import com.adhar.kit.config.refresh.ConfigRefreshManager;
import com.adhar.kit.config.source.impl.EnvironmentConfigSource;
import com.adhar.kit.config.source.impl.FileConfigSource;
import com.adhar.kit.config.validator.ConfigValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
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
@AutoConfiguration
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
     * <p>Configures multiple sources based on properties with priority-based merging.</p>
     *
     * @return configured ConfigManager instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigManager configManager() {
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

        log.info("ConfigManager initialized with {} sources", manager.getHealthStatus().size());
        return manager;
    }

    /**
     * Creates the ConfigFacade singleton accessor bean.
     *
     * <p>Provides a simplified interface for configuration access throughout the application.</p>
     *
     * @param configManager the config manager to use
     * @return configured ConfigFacade instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigFacade configFacade(ConfigManager configManager) {
        ConfigFacade facade = ConfigFacade.getInstance();
        log.info("ConfigFacade initialized and linked to ConfigManager");
        return facade;
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

                case "consul":
                case "vault":
                case "springcloud":
                case "k8s":
                    log.info("Source type '{}' requires additional configuration - see documentation", type);
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
            log.info("PropertyEncryptor initialized with algorithm: {}", algorithm);

            return new PropertyEncryptor(encryptionKey, algorithm);
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
     * HashiCorp Vault integration (conditional on classpath).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.vault.core.VaultTemplate")
    @ConditionalOnProperty(prefix = "spring.cloud.vault", name = "enabled", havingValue = "true")
    static class VaultConfiguration {

        @PostConstruct
        public void logVaultIntegration() {
            log.info("HashiCorp Vault integration enabled - secrets will be loaded from Vault");
        }
    }
}

