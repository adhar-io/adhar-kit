package com.adhar.kit.config.api;

import java.util.Map;
import java.util.Optional;

/**
 * Framework-agnostic Configuration Service API.
 *
 * <p>This interface provides a unified configuration management abstraction
 * across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>Property retrieval with type safety</li>
 *   <li>Configuration refresh at runtime</li>
 *   <li>Environment-specific configurations</li>
 *   <li>Encrypted configuration values</li>
 *   <li>Configuration profiles</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ConfigService config = ConfigFacade.getInstance();
 *
 * // String properties
 * String apiKey = config.get("api.key");
 * String apiUrl = config.get("api.url", "https://default.api.com");
 *
 * // Typed properties
 * int maxRetries = config.getInt("resilience.max-retries", 3);
 * boolean featureEnabled = config.getBoolean("features.new-ui.enabled", false);
 * long timeout = config.getLong("http.timeout.ms", 5000L);
 *
 * // Optional properties
 * Optional<String> customProp = config.getOptional("custom.property");
 *
 * // Refresh configuration
 * config.refresh();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.config.ConfigFacade
 */
public interface ConfigService {

    /**
     * Gets a configuration property.
     *
     * @param key the property key
     * @return the property value
     * @throws IllegalArgumentException if key not found
     */
    String get(String key);

    /**
     * Gets a configuration property with a default value.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value or default
     */
    String get(String key, String defaultValue);

    /**
     * Gets an optional configuration property.
     *
     * @param key the property key
     * @return Optional containing the value if found
     */
    Optional<String> getOptional(String key);

    /**
     * Gets an integer configuration property.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found or invalid
     * @return the property value as integer
     */
    int getInt(String key, int defaultValue);

    /**
     * Gets a long configuration property.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found or invalid
     * @return the property value as long
     */
    long getLong(String key, long defaultValue);

    /**
     * Gets a boolean configuration property.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found or invalid
     * @return the property value as boolean
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Gets a double configuration property.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found or invalid
     * @return the property value as double
     */
    double getDouble(String key, double defaultValue);

    /**
     * Gets all configuration properties with a specific prefix.
     *
     * @param prefix the property prefix
     * @return map of properties
     */
    Map<String, String> getPropertiesWithPrefix(String prefix);

    /**
     * Checks if a configuration property exists.
     *
     * @param key the property key
     * @return true if property exists, false otherwise
     */
    boolean containsKey(String key);

    /**
     * Gets the active configuration profile.
     *
     * @return active profile name (e.g., "dev", "prod")
     */
    String getActiveProfile();

    /**
     * Refreshes the configuration from the source.
     *
     * <p>This triggers a reload of configuration properties,
     * useful for runtime configuration changes.</p>
     */
    void refresh();

    /**
     * Checks if configuration refresh is supported.
     *
     * @return true if refresh is supported, false otherwise
     */
    boolean isRefreshable();
}

