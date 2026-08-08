package com.adhar.kit.config;

import com.adhar.kit.config.api.ConfigService;
import com.adhar.kit.config.manager.ConfigManager;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Configuration facade for application configuration.
 *
 * <p>Operates in one of two modes:</p>
 * <ul>
 *   <li><b>Delegation mode</b> - when linked to a {@link ConfigManager} (see
 *       {@link #setConfigManager(ConfigManager)}), all lookups and refreshes are
 *       delegated to the manager (multi-source, priority merged, auto-decrypted).
 *       The local property map remains available as a fallback for keys the
 *       manager does not know about.</li>
 *   <li><b>Standalone mode</b> - without a manager, lookups use the local
 *       in-memory property map only.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ConfigFacade config = ConfigFacade.getInstance();
 *
 * // Application configuration
 * @Service
 * public class PaymentService {
 *
 *     private final ConfigFacade config = ConfigFacade.getInstance();
 *
 *     public void processPayment(Payment payment) {
 *         // Get configuration values
 *         String gatewayUrl = config.get("payment.gateway.url");
 *         String apiKey = config.get("payment.gateway.api-key");
 *         int maxRetries = config.getInt("payment.max-retries", 3);
 *         long timeout = config.getLong("payment.timeout.ms", 30000L);
 *
 *         // Use configuration
 *         PaymentGatewayClient client = new PaymentGatewayClient(
 *             gatewayUrl, apiKey, timeout);
 *
 *         // Feature flags
 *         if (config.getBoolean("features.fraud-detection.enabled", false)) {
 *             fraudDetectionService.check(payment);
 *         }
 *     }
 * }
 *
 * // Environment-specific config
 * String activeProfile = config.getActiveProfile(); // dev, staging, prod
 *
 * // Runtime refresh
 * @Scheduled(fixedDelay = 60000)
 * public void refreshConfig() {
 *     if (config.isRefreshable()) {
 *         config.refresh();
 *     }
 * }
 *
 * // Group properties
 * Map<String, String> dbProps = config.getPropertiesWithPrefix("database");
 * // Returns: {url=..., username=..., password=...}
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ConfigFacade implements ConfigService {

    private static volatile ConfigFacade instance;

    /**
     * Standalone-mode fallback properties, seeded from system properties and
     * environment variables (env keys normalized the same way
     * {@code EnvironmentConfigSource} does: {@code DATABASE_URL -> database.url})
     * so lookups work even without a backing {@link ConfigManager}.
     */
    private final Map<String, String> properties = new ConcurrentHashMap<>();

    /**
     * Optional backing ConfigManager (delegation mode when non-null).
     */
    private volatile ConfigManager configManager;

    private ConfigFacade() {
        seedStandaloneProperties();
        log.info("Initialized ConfigFacade with {} standalone fallback properties", properties.size());
    }

    private void seedStandaloneProperties() {
        System.getenv().forEach((key, value) -> {
            if (key != null && value != null) {
                properties.put(key.toLowerCase().replace('_', '.'), value);
            }
        });
        // System properties win over environment variables, matching Spring's ordering.
        System.getProperties().forEach((key, value) -> {
            if (key != null && value != null) {
                properties.put(String.valueOf(key), String.valueOf(value));
            }
        });
    }

    public static ConfigFacade getInstance() {
        if (instance == null) {
            synchronized (ConfigFacade.class) {
                if (instance == null) {
                    instance = new ConfigFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Links this facade to a {@link ConfigManager} (delegation mode).
     *
     * <p>Once linked, lookups delegate to the manager first and fall back to the
     * local property map; {@link #refresh()} triggers {@link ConfigManager#refreshAll()}.</p>
     *
     * @param configManager the backing config manager (null reverts to standalone mode)
     */
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
        log.info("ConfigFacade {} ConfigManager delegation",
                configManager != null ? "enabled" : "disabled");
    }

    /**
     * Looks up a raw value: ConfigManager first (delegation mode), local map fallback.
     */
    private String lookup(String key) {
        ConfigManager manager = this.configManager;
        if (manager != null) {
            Object value = manager.getProperty(key);
            if (value != null) {
                return value.toString();
            }
        }
        return properties.get(key);
    }

    @Override
    public String get(String key) {
        log.debug("Getting configuration: {}", key);
        String value = lookup(key);
        if (value == null) {
            throw new IllegalArgumentException("Configuration key not found: " + key);
        }
        return value;
    }

    @Override
    public String get(String key, String defaultValue) {
        log.debug("Getting configuration: {} (default: {})", key, defaultValue);
        String value = lookup(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Optional<String> getOptional(String key) {
        log.debug("Getting optional configuration: {}", key);
        return Optional.ofNullable(lookup(key));
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            String value = lookup(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key: {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        try {
            String value = lookup(key);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for key: {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = lookup(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        try {
            String value = lookup(key);
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid double value for key: {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> getPropertiesWithPrefix(String prefix) {
        log.debug("Getting properties with prefix: {}", prefix);
        Map<String, String> result = new HashMap<>();
        String prefixWithDot = prefix.endsWith(".") ? prefix : prefix + ".";

        // Local map first so delegated values override on key collision
        properties.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefixWithDot))
            .forEach(e -> result.put(
                e.getKey().substring(prefixWithDot.length()),
                e.getValue()
            ));

        ConfigManager manager = this.configManager;
        if (manager != null) {
            manager.getPropertiesWithPrefix(prefixWithDot)
                .forEach((key, value) -> result.put(
                    key.substring(prefixWithDot.length()),
                    value != null ? value.toString() : null
                ));
        }

        return result;
    }

    @Override
    public boolean containsKey(String key) {
        ConfigManager manager = this.configManager;
        if (manager != null && manager.containsProperty(key)) {
            return true;
        }
        return properties.containsKey(key);
    }

    @Override
    public String getActiveProfile() {
        return get("spring.profiles.active", "default");
    }

    @Override
    public void refresh() {
        log.info("Refreshing configuration");
        ConfigManager manager = this.configManager;
        if (manager != null) {
            manager.refreshAll();
        } else {
            log.debug("No ConfigManager linked - refresh is a no-op in standalone mode");
        }
    }

    @Override
    public boolean isRefreshable() {
        return configManager != null;
    }
}

