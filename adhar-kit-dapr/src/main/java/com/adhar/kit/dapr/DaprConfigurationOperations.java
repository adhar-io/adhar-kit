package com.adhar.kit.dapr;

import com.adhar.kit.dapr.configuration.ConfigItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration from one configuration store (the default {@code configstore} unless
 * {@link #withStore} picks another).
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprConfigurationOperations {

    private final DaprFacade dapr;
    private final String store;

    DaprConfigurationOperations(DaprFacade dapr, String store) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public String store() {
        return store;
    }

    public DaprConfigurationOperations withStore(String storeName) {
        return new DaprConfigurationOperations(dapr, storeName);
    }

    /** The items for {@code keys} (every item when empty), with versions and metadata. */
    public Map<String, ConfigItem> get(List<String> keys) {
        return dapr.getConfigurationItems(store, keys);
    }

    /** One value, or {@code null}. */
    public String value(String key) {
        return dapr.getConfiguration(store, key);
    }

    /**
     * Follows changes of {@code keys} (every key when empty) until the returned subscription is
     * ended; a deleted key arrives with a {@code null} value.
     */
    public DaprSubscription subscribe(List<String> keys, Consumer<Map<String, String>> onChange) {
        return dapr.subscribeConfiguration(store, keys, changed -> onChange.accept(new HashMap<>(changed)));
    }
}
