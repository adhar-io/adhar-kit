package com.adhar.kit.config;

import com.adhar.kit.config.api.ConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Universal Configuration facade for application configuration.
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
    private final Map<String, String> properties = new HashMap<>();

    private ConfigFacade() {
        log.info("Initialized ConfigFacade");
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

    @Override
    public String get(String key) {
        log.debug("Getting configuration: {}", key);
        String value = properties.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Configuration key not found: " + key);
        }
        return value;
    }

    @Override
    public String get(String key, String defaultValue) {
        log.debug("Getting configuration: {} (default: {})", key, defaultValue);
        return properties.getOrDefault(key, defaultValue);
    }

    @Override
    public Optional<String> getOptional(String key) {
        log.debug("Getting optional configuration: {}", key);
        return Optional.ofNullable(properties.get(key));
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            String value = properties.get(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key: {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        try {
            String value = properties.get(key);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for key: {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        try {
            String value = properties.get(key);
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

        properties.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefixWithDot))
            .forEach(e -> result.put(
                e.getKey().substring(prefixWithDot.length()),
                e.getValue()
            ));

        return result;
    }

    @Override
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    @Override
    public String getActiveProfile() {
        return get("spring.profiles.active", "default");
    }

    @Override
    public void refresh() {
        log.info("Refreshing configuration");
        // Framework-specific implementation will override this
    }

    @Override
    public boolean isRefreshable() {
        return true;
    }
}

