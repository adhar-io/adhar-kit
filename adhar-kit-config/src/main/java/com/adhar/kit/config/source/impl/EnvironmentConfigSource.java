package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Environment variable configuration source.
 *
 * <p>Loads configuration from system environment variables.</p>
 *
 * <p><b>Key Mapping:</b></p>
 * <ul>
 *   <li>ENV: <code>DATABASE_URL</code> → Key: <code>database.url</code></li>
 *   <li>ENV: <code>APP_SERVER_PORT</code> → Key: <code>app.server.port</code></li>
 *   <li>ENV: <code>SPRING_DATASOURCE_URL</code> → Key: <code>spring.datasource.url</code></li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Create source
 * ConfigSource envSource = new EnvironmentConfigSource();
 *
 * // Or with prefix filter
 * ConfigSource envSource = new EnvironmentConfigSource("APP_", 200);
 *
 * // Get value
 * Optional<Object> dbUrl = envSource.getProperty("database.url");
 * // Reads from DATABASE_URL environment variable
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class EnvironmentConfigSource implements ConfigSource {

    private final String prefix;
    private final int priority;
    private Map<String, Object> config;

    /**
     * Constructor with default priority.
     */
    public EnvironmentConfigSource() {
        this("", 200);
    }

    /**
     * Constructor with prefix.
     *
     * @param prefix environment variable prefix (e.g., "APP_")
     */
    public EnvironmentConfigSource(String prefix) {
        this(prefix, 200);
    }

    /**
     * Constructor with prefix and priority.
     *
     * @param prefix environment variable prefix
     * @param priority source priority
     */
    public EnvironmentConfigSource(String prefix, int priority) {
        this.prefix = prefix != null ? prefix : "";
        this.priority = priority;
        this.config = new HashMap<>();
        loadEnvironmentVariables();
    }

    @Override
    public String getType() {
        return "environment";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Map<String, Object> loadConfig() {
        return new HashMap<>(config);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(config.get(key));
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        try {
            loadEnvironmentVariables();
            log.debug("Refreshed environment variables");
            return true;
        } catch (Exception e) {
            log.error("Failed to refresh environment variables", e);
            return false;
        }
    }

    /**
     * Loads environment variables into configuration.
     */
    private void loadEnvironmentVariables() {
        Map<String, Object> newConfig = new HashMap<>();

        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String envKey = entry.getKey();

            // Filter by prefix if specified
            if (!prefix.isEmpty() && !envKey.startsWith(prefix)) {
                continue;
            }

            // Remove prefix
            String keyWithoutPrefix = prefix.isEmpty() ? envKey :
                envKey.substring(prefix.length());

            // Convert ENV_VAR_NAME to env.var.name
            String configKey = envKeyToConfigKey(keyWithoutPrefix);

            newConfig.put(configKey, entry.getValue());
        }

        this.config = newConfig;
        log.info("Loaded {} environment variables with prefix '{}'",
                newConfig.size(), prefix);
    }

    /**
     * Converts environment variable name to config key.
     * <p>
     * Examples:
     * <ul>
     *   <li>DATABASE_URL → database.url</li>
     *   <li>APP_SERVER_PORT → app.server.port</li>
     *   <li>SPRING_DATASOURCE_USERNAME → spring.datasource.username</li>
     * </ul>
     *
     * @param envKey environment variable name
     * @return configuration key
     */
    private String envKeyToConfigKey(String envKey) {
        return envKey.toLowerCase().replace('_', '.');
    }

    /**
     * Converts config key to environment variable name.
     * <p>
     * Examples:
     * <ul>
     *   <li>database.url → DATABASE_URL</li>
     *   <li>app.server.port → APP_SERVER_PORT</li>
     * </ul>
     *
     * @param configKey configuration key
     * @return environment variable name
     */
    public static String configKeyToEnvKey(String configKey) {
        return configKey.toUpperCase().replace('.', '_');
    }
}

