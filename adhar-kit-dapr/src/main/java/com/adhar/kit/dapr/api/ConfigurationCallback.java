package com.adhar.kit.dapr.api;

import java.util.Map;

/**
 * Callback for configuration changes.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface ConfigurationCallback {

    /**
     * Called when configuration changes.
     *
     * @param changedConfiguration map of changed configuration key-value pairs
     */
    void onConfigurationChange(Map<String, String> changedConfiguration);
}

