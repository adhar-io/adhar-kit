package com.adhar.kit.config.source;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for configuration sources.
 *
 * <p>Defines contract for reading configuration from various sources:</p>
 * <ul>
 *   <li>File-based (YAML, Properties)</li>
 *   <li>Environment variables</li>
 *   <li>Spring Cloud Config Server</li>
 *   <li>HashiCorp Consul</li>
 *   <li>HashiCorp Vault</li>
 *   <li>Kubernetes ConfigMaps/Secrets</li>
 * </ul>
 *
 * <p><b>Example - Custom Implementation:</b></p>
 * <pre>{@code
 * public class DatabaseConfigSource implements ConfigSource {
 *
 *     private final JdbcTemplate jdbcTemplate;
 *
 *     @Override
 *     public String getType() {
 *         return "database";
 *     }
 *
 *     @Override
 *     public Map<String, Object> loadConfig() {
 *         return jdbcTemplate.query(
 *             "SELECT key, value FROM app_config",
 *             rs -> {
 *                 Map<String, Object> config = new HashMap<>();
 *                 while (rs.next()) {
 *                     config.put(rs.getString("key"), rs.getString("value"));
 *                 }
 *                 return config;
 *             }
 *         );
 *     }
 *
 *     @Override
 *     public Optional<Object> getProperty(String key) {
 *         return Optional.ofNullable(loadConfig().get(key));
 *     }
 *
 *     @Override
 *     public boolean supportsRefresh() {
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface ConfigSource {

    /**
     * Gets the configuration source type.
     *
     * @return source type (e.g., "file", "consul", "vault")
     */
    String getType();

    /**
     * Gets the source priority.
     * Higher priority sources override lower priority ones.
     *
     * @return priority value (default: 100)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks if this source is enabled.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Loads all configuration from this source.
     *
     * @return map of configuration key-value pairs
     */
    Map<String, Object> loadConfig();

    /**
     * Gets a specific property value.
     *
     * @param key property key
     * @return property value if found
     */
    Optional<Object> getProperty(String key);

    /**
     * Checks if this source supports automatic refresh.
     *
     * @return true if refresh is supported
     */
    boolean supportsRefresh();

    /**
     * Refreshes configuration from source.
     *
     * @return true if refresh was successful
     */
    default boolean refresh() {
        return false;
    }

    /**
     * Checks connection health to this source.
     *
     * @return true if source is reachable
     */
    default boolean isHealthy() {
        try {
            loadConfig();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

