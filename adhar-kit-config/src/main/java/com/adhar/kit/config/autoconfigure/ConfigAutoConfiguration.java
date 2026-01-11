package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.properties.ConfigProperties;
import com.adhar.kit.config.source.impl.EnvironmentConfigSource;

/**
 * Auto-configuration for Adhar Config module.
 *
 * <p>Automatically configures configuration management with multiple sources.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Environment variable configuration</li>
 *   <li>File-based configuration</li>
 *   <li>Dynamic configuration refresh</li>
 *   <li>Property encryption support</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ConfigAutoConfiguration {

    private final ConfigProperties properties;

    /**
     * Constructor.
     *
     * @param properties configuration properties
     */
    public ConfigAutoConfiguration(ConfigProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the config manager bean.
     *
     * @return configured config manager
     */
    public ConfigManager configManager() {
        ConfigManager manager = new ConfigManager();

        // Add environment variable source
        manager.addSource(new EnvironmentConfigSource());

        return manager;
    }
}

