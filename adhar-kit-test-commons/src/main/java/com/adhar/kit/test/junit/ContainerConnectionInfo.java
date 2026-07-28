package com.adhar.kit.test.junit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-JVM holder for the connection properties of containers started by {@link AdharKitExtension}.
 *
 * <p>The extension populates this once per JVM as containers start; it is then the bridge to two
 * consumers: fields/parameters injected by the extension, and the {@link DynamicPropertyRegistrar}
 * bean in {@link AdharKitDynamicPropertyConfiguration} that copies these into the Spring test
 * context as dynamic properties.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public final class ContainerConnectionInfo {

    private static final ContainerConnectionInfo INSTANCE = new ContainerConnectionInfo();

    private final Map<String, String> properties = new ConcurrentHashMap<>();

    private ContainerConnectionInfo() {
    }

    /**
     * The singleton, shared for the lifetime of the JVM.
     */
    public static ContainerConnectionInfo getInstance() {
        return INSTANCE;
    }

    /**
     * Store a property. {@code null} values are ignored.
     */
    public void put(String key, String value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    /**
     * Store every entry of {@code values}, ignoring {@code null} values.
     */
    public void putAll(Map<String, String> values) {
        values.forEach(this::put);
    }

    /**
     * The value for {@code key}, or {@code null} if absent.
     */
    public String get(String key) {
        return properties.get(key);
    }

    /**
     * Whether a value is stored for {@code key}.
     */
    public boolean has(String key) {
        return properties.containsKey(key);
    }

    /**
     * An immutable snapshot of all stored properties.
     */
    public Map<String, String> asMap() {
        return Map.copyOf(properties);
    }

    /**
     * Remove all stored properties (mainly for test isolation).
     */
    public void clear() {
        properties.clear();
    }
}
