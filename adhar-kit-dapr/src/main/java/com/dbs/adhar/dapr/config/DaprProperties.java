package com.dbs.adhar.dapr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Dapr integration.
 */
@ConfigurationProperties(prefix = "adhar.dapr")
public class DaprProperties {

    /**
     * Whether to enable Dapr integration.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

