package com.adhar.kit.dapr.configuration;

import java.util.Map;

/**
 * One configuration value from a Dapr configuration store.
 *
 * @param key      the key
 * @param value    the value, or {@code null} when the key was deleted
 * @param version  the store's version of the value, or {@code null}
 * @param metadata item metadata
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record ConfigItem(String key, String value, String version, Map<String, String> metadata) {

    public ConfigItem {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
