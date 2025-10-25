package com.adhar.kit.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Adhar Config module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.config")
public class ConfigProperties {

    /**
     * Enable/disable config features.
     */
    private boolean enabled = true;

    /**
     * Spring Cloud Config settings.
     */
    private CloudConfig cloudConfig = new CloudConfig();

    /**
     * Vault settings.
     */
    private Vault vault = new Vault();

    /**
     * Encryption settings.
     */
    private Encryption encryption = new Encryption();

    /**
     * Refresh settings.
     */
    private Refresh refresh = new Refresh();

    // Manual getters for Java 25 compatibility (Lombok @Data not working properly)
    public Encryption getEncryption() {
        return encryption;
    }

    @Data
    public static class CloudConfig {
        private boolean enabled = false;
        private String uri = "http://localhost:8888";
        private String label = "master";
        private String profile = "default";
        private String username;
        private String password;
        private int connectTimeout = 5000;
        private int readTimeout = 5000;
        private boolean failFast = false;
    }

    @Data
    public static class Vault {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 8200;
        private String scheme = "http";
        private String token;
        private String backend = "secret";
        private String defaultContext = "application";
        private int connectionTimeout = 5000;
        private int readTimeout = 15000;
    }

    @Data
    public static class Encryption {
        private boolean enabled = false;
        private String password;
        private String algorithm = "PBEWithMD5AndDES";
        private String keyObtentionIterations = "1000";
        private String poolSize = "1";
        private String saltGeneratorClassName = "org.jasypt.salt.RandomSaltGenerator";
        private String stringOutputType = "base64";

        // Manual getters for Java 25 compatibility
        public boolean isEnabled() { return enabled; }
        public String getPassword() { return password; }
        public String getAlgorithm() { return algorithm; }
        public String getKeyObtentionIterations() { return keyObtentionIterations; }
        public String getPoolSize() { return poolSize; }
        public String getSaltGeneratorClassName() { return saltGeneratorClassName; }
        public String getStringOutputType() { return stringOutputType; }
    }

    @Data
    public static class Refresh {
        private boolean enabled = true;
        private boolean autoRefresh = false;
        private long refreshInterval = 60000; // 1 minute
    }
}

