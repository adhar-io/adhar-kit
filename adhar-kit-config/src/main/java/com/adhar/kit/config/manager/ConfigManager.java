package com.adhar.kit.config.manager;

import com.adhar.kit.config.encryption.PropertyEncryptor;
import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Central configuration manager.
 *
 * <p>Manages multiple configuration sources with priority-based merging.</p>
 *
 * <p><b>Example - Usage:</b></p>
 * <pre>{@code
 * // Create manager
 * ConfigManager manager = new ConfigManager();
 *
 * // Add sources (higher priority = 200)
 * manager.addSource(new FileConfigSource("classpath:application.yml", 100));
 * manager.addSource(new ConsulConfigSource("http://localhost:8500", 150));
 * manager.addSource(new EnvironmentConfigSource(200));
 *
 * // Get properties (environment overrides consul, consul overrides file)
 * String dbUrl = manager.getProperty("database.url", String.class);
 * Integer poolSize = manager.getProperty("database.pool.size", Integer.class, 10);
 *
 * // Refresh all sources
 * manager.refreshAll();
 *
 * // Listen for changes
 * manager.addChangeListener((key, oldValue, newValue) -> {
 *     log.info("Config changed: {} = {} -> {}", key, oldValue, newValue);
 * });
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConfigManager {

    /**
     * Registered configuration sources (sorted by priority).
     */
    private final List<ConfigSource> sources = new CopyOnWriteArrayList<>();

    /**
     * Cached configuration (merged from all sources).
     */
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * Change listeners.
     */
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Optional encryptor for transparent decryption of ENC(...) values.
     */
    private volatile PropertyEncryptor encryptor;

    /**
     * Sets the property encryptor used to transparently decrypt {@code ENC(...)}
     * values in the read path ({@link #getProperty(String)} and friends).
     *
     * @param encryptor the property encryptor (null disables decryption)
     */
    public void setEncryptor(PropertyEncryptor encryptor) {
        this.encryptor = encryptor;
        log.info("PropertyEncryptor {} on ConfigManager", encryptor != null ? "enabled" : "disabled");
    }

    /**
     * Adds a configuration source.
     *
     * @param source the configuration source
     */
    public void addSource(ConfigSource source) {
        if (source == null || !source.isEnabled()) {
            log.warn("Skipping disabled or null config source");
            return;
        }

        sources.add(source);
        sortSources();

        log.info("Added config source: {} with priority {}",
                source.getType(), source.getPriority());

        refreshCache();
    }

    /**
     * Removes a configuration source.
     *
     * @param sourceType the source type to remove
     */
    public void removeSource(String sourceType) {
        sources.removeIf(s -> s.getType().equals(sourceType));
        refreshCache();

        log.info("Removed config source: {}", sourceType);
    }

    /**
     * Gets a property value.
     *
     * @param key property key
     * @return property value or null
     */
    public Object getProperty(String key) {
        return decryptIfNeeded(cache.get(key));
    }

    /**
     * Gets a property value with type conversion.
     *
     * @param key property key
     * @param type target type
     * @param <T> property type
     * @return property value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = getProperty(key);
        if (value == null) {
            return null;
        }

        return convertValue(value, type);
    }

    /**
     * Gets a property value with default.
     *
     * @param key property key
     * @param type target type
     * @param defaultValue default value
     * @param <T> property type
     * @return property value or default
     */
    public <T> T getProperty(String key, Class<T> type, T defaultValue) {
        T value = getProperty(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets required property (throws if missing).
     *
     * @param key property key
     * @param type target type
     * @param <T> property type
     * @return property value
     * @throws IllegalArgumentException if property not found
     */
    public <T> T getRequiredProperty(String key, Class<T> type) {
        T value = getProperty(key, type);
        if (value == null) {
            throw new IllegalArgumentException("Required property not found: " + key);
        }
        return value;
    }

    /**
     * Gets all properties matching a prefix.
     *
     * @param prefix key prefix
     * @return matching properties
     */
    public Map<String, Object> getPropertiesWithPrefix(String prefix) {
        return cache.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .collect(Collectors.toMap(Map.Entry::getKey, e -> decryptIfNeeded(e.getValue())));
    }

    /**
     * Checks if property exists.
     *
     * @param key property key
     * @return true if exists
     */
    public boolean containsProperty(String key) {
        return cache.containsKey(key);
    }

    /**
     * Refreshes all configuration sources.
     */
    public void refreshAll() {
        log.info("Refreshing all configuration sources");

        Map<String, Object> oldCache = new HashMap<>(cache);

        sources.stream()
            .filter(ConfigSource::supportsRefresh)
            .forEach(source -> {
                try {
                    if (source.refresh()) {
                        log.debug("Refreshed config source: {}", source.getType());
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh config source: {}", source.getType(), e);
                }
            });

        refreshCache();
        notifyChanges(oldCache, cache);
    }

    /**
     * Refreshes specific source.
     *
     * @param sourceType source type to refresh
     */
    public void refreshSource(String sourceType) {
        sources.stream()
            .filter(s -> s.getType().equals(sourceType))
            .filter(ConfigSource::supportsRefresh)
            .findFirst()
            .ifPresent(source -> {
                Map<String, Object> oldCache = new HashMap<>(cache);

                if (source.refresh()) {
                    refreshCache();
                    notifyChanges(oldCache, cache);
                    log.info("Refreshed config source: {}", sourceType);
                }
            });
    }

    /**
     * Adds a change listener.
     *
     * @param listener the change listener
     */
    public void addChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a change listener.
     *
     * @param listener the change listener
     */
    public void removeChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Gets health status of all sources.
     *
     * @return health status map
     */
    public Map<String, Boolean> getHealthStatus() {
        return sources.stream()
            .collect(Collectors.toMap(
                ConfigSource::getType,
                ConfigSource::isHealthy
            ));
    }

    // ==================== PRIVATE METHODS ====================

    /**
     * Sorts sources by priority (descending).
     */
    private void sortSources() {
        sources.sort((s1, s2) -> Integer.compare(s2.getPriority(), s1.getPriority()));
    }

    /**
     * Refreshes the configuration cache from all sources.
     */
    private void refreshCache() {
        cache.clear();

        // Load from all sources (reverse order so high priority overrides)
        List<ConfigSource> reversedSources = new ArrayList<>(sources);
        Collections.reverse(reversedSources);

        for (ConfigSource source : reversedSources) {
            try {
                Map<String, Object> config = source.loadConfig();
                cache.putAll(config);

                log.debug("Loaded {} properties from {}", config.size(), source.getType());
            } catch (Exception e) {
                log.error("Failed to load config from {}", source.getType(), e);
            }
        }

        log.info("Configuration cache refreshed: {} properties", cache.size());
    }

    /**
     * Notifies listeners of configuration changes.
     */
    private void notifyChanges(Map<String, Object> oldCache, Map<String, Object> newCache) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(oldCache.keySet());
        allKeys.addAll(newCache.keySet());

        for (String key : allKeys) {
            Object oldValue = oldCache.get(key);
            Object newValue = newCache.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                notifyChange(key, oldValue, newValue);
            }
        }
    }

    /**
     * Notifies listeners of a single property change.
     */
    private void notifyChange(String key, Object oldValue, Object newValue) {
        listeners.forEach(listener -> {
            try {
                listener.onConfigChange(key, oldValue, newValue);
            } catch (Exception e) {
                log.error("Error notifying listener of config change", e);
            }
        });
    }

    /**
     * Transparently decrypts ENC(...) string values when an encryptor is configured.
     */
    private Object decryptIfNeeded(Object value) {
        PropertyEncryptor enc = this.encryptor;
        if (enc != null && value instanceof String strValue) {
            return enc.decryptIfNeeded(strValue);
        }
        return value;
    }

    /**
     * Converts value to target type.
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> targetType) {
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        // String conversions
        String strValue = value.toString();

        if (targetType == String.class) {
            return (T) strValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return (T) Integer.valueOf(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return (T) Long.valueOf(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return (T) Boolean.valueOf(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return (T) Double.valueOf(strValue);
        }

        return (T) value;
    }

    /**
     * Configuration change listener interface.
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        /**
         * Called when a configuration property changes.
         *
         * @param key property key
         * @param oldValue old value
         * @param newValue new value
         */
        void onConfigChange(String key, Object oldValue, Object newValue);
    }
}

