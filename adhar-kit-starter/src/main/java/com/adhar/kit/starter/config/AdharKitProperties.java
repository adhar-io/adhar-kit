package com.adhar.kit.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Adhar Kit Starter.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.kit")
public class AdharKitProperties {

    /**
     * Enable/disable Adhar Kit features.
     */
    private boolean enabled = true;

    /**
     * Application name.
     */
    private String applicationName;

    /**
     * Application version.
     */
    private String applicationVersion;

    /**
     * Application profile/environment.
     */
    private String profile = "default";

    /**
     * Enable auto-configuration for all modules.
     */
    private boolean autoConfigureAll = true;

    /**
     * Module-specific configurations.
     */
    private Modules modules = new Modules();

    @Data
    public static class Modules {
        private boolean persistence = true;
        private boolean cache = true;
        private boolean caching = true;
        private boolean messaging = true;
        private boolean security = true;
        private boolean resilience = true;
        private boolean tracing = true;
        private boolean metrics = true;
        private boolean logging = true;
        private boolean health = true;
        private boolean config = true;
        private boolean docs = true;
        private boolean ai = false;
        private boolean analytics = false;
        private boolean kubernetes = false;
        private boolean dapr = false;
        private boolean grpc = false;
    }
}

