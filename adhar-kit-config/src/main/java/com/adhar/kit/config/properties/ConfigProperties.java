package com.adhar.kit.config.properties;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for centralized configuration management.
 *
 * <p>Supports multiple configuration sources:</p>
 * <ul>
 *   <li><b>File-based</b> - application.yml, application.properties</li>
 *   <li><b>Environment</b> - Environment variables</li>
 *   <li><b>Spring Cloud Config</b> - Centralized config server</li>
 *   <li><b>Consul</b> - HashiCorp Consul KV store</li>
 *   <li><b>Vault</b> - HashiCorp Vault for secrets</li>
 *   <li><b>Kubernetes</b> - ConfigMaps and Secrets</li>
 * </ul>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   config:
 *     enabled: true
 *     sources:
 *       - type: file
 *         location: classpath:config/
 *       - type: consul
 *         url: http://localhost:8500
 *         prefix: config/myapp
 *     refresh:
 *       enabled: true
 *       interval: 30s
 *     encryption:
 *       enabled: true
 *       algorithm: AES
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class ConfigProperties {

    /**
     * Enable configuration management.
     */
    private boolean enabled = true;

    /**
     * Application name for configuration namespacing.
     */
    private String applicationName;

    /**
     * Active profiles.
     */
    private String[] profiles = new String[]{"default"};

    /**
     * Configuration sources.
     */
    private Map<String, SourceConfig> sources = new HashMap<>();

    /**
     * Refresh configuration.
     */
    private RefreshConfig refresh = new RefreshConfig();

    /**
     * Encryption configuration.
     */
    private EncryptionConfig encryption = new EncryptionConfig();

    /**
     * Validation configuration.
     */
    private ValidationConfig validation = new ValidationConfig();

    /**
     * Configuration source settings.
     */
    @Data
    public static class SourceConfig {
        /**
         * Source type (file, consul, vault, k8s, springcloud).
         */
        private String type;

        /**
         * Source location/URL.
         */
        private String location;

        /**
         * Key prefix for namespacing.
         */
        private String prefix;

        /**
         * Priority (higher = more important).
         */
        private int priority = 100;

        /**
         * Enable source.
         */
        private boolean enabled = true;

        /**
         * Authentication settings.
         */
        private Map<String, String> auth = new HashMap<>();
    }

    /**
     * Refresh configuration.
     */
    @Data
    public static class RefreshConfig {
        /**
         * Enable automatic refresh.
         */
        private boolean enabled = false;

        /**
         * Refresh interval.
         */
        private Duration interval = Duration.ofSeconds(30);

        /**
         * Retry configuration.
         */
        private int maxRetries = 3;

        /**
         * Retry delay.
         */
        private Duration retryDelay = Duration.ofSeconds(5);
    }

    /**
     * Encryption configuration.
     */
    @Data
    public static class EncryptionConfig {
        /**
         * Enable encryption for sensitive properties.
         */
        private boolean enabled = false;

        /**
         * Encryption algorithm (AES, RSA).
         */
        private String algorithm = "AES";

        /**
         * Encryption key (direct value - prefer using environment variable ADHAR_CONFIG_ENCRYPTION_KEY).
         */
        private String key;

        /**
         * Key location (file path to encryption key).
         */
        private String keyLocation;

        /**
         * Key alias (for keystore-based encryption).
         */
        private String keyAlias;

        /**
         * PBKDF2 salt for key derivation (set a deployment-specific value in production).
         */
        private String salt = "adhar-kit-config-salt";

        /**
         * PBKDF2 iteration count for key derivation.
         */
        private int keyIterations = 210_000;

        /**
         * Prefix for encrypted values (default: ENC()).
         */
        private String encryptedPrefix = "ENC(";

        /**
         * Suffix for encrypted values (default: )).
         */
        private String encryptedSuffix = ")";
    }

    /**
     * Validation configuration.
     */
    @Data
    public static class ValidationConfig {
        /**
         * Enable property validation.
         */
        private boolean enabled = true;

        /**
         * Fail on validation errors.
         */
        private boolean failOnError = true;

        /**
         * Log validation warnings.
         */
        private boolean logWarnings = true;

        /**
         * Required property keys (validation fails when missing).
         */
        private List<String> required = new ArrayList<>();

        /**
         * Regex pattern rules per property key (validated when the key is present).
         */
        private Map<String, String> patterns = new HashMap<>();
    }
}

