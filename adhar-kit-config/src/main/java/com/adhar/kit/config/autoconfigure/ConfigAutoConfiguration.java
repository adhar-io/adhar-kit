package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.client.ConfigServerClient;
import com.adhar.kit.config.client.VaultClient;
import com.adhar.kit.config.encryption.EncryptionConfiguration;
import com.adhar.kit.config.properties.ConfigProperties;
import com.adhar.kit.config.refresh.ConfigRefreshManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigClientAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for Adhar Config module.
 * Automatically configures Spring Cloud Config, Vault, and encryption support.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration(after = ConfigClientAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.cloud.config.client.ConfigClientProperties")
@ConditionalOnProperty(prefix = "adhar.config", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConfigProperties.class)
@Import({
    ConfigServerClient.class,
    VaultClient.class,
    EncryptionConfiguration.class,
    ConfigRefreshManager.class
})
public class ConfigAutoConfiguration {

    /**
     * Default constructor.
     */
    public ConfigAutoConfiguration() {
        // Auto-configuration class
    }
}

