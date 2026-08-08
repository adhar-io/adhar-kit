package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dapr configuration-store source.
 *
 * <p>Reads configuration through the Dapr configuration building block via
 * {@link DaprFacade}, so the actual backend (Redis, Postgres, Azure App
 * Configuration, ...) is whatever the sidecar's configuration component is.
 * When {@code subscribe} is enabled the source registers a Dapr configuration
 * subscription and keeps its cache up to date as keys change.</p>
 *
 * <p>Lookups never propagate sidecar failures: a failed read logs a warning
 * and reports a miss ({@link Optional#empty()}), letting lower-priority
 * sources or defaults take over. {@link #isHealthy()} reflects the last load
 * attempt.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprConfigSource implements ConfigSource {

    private final DaprFacade daprFacade;
    private final String storeName;
    private final List<String> keys;
    private final int priority;
    private final boolean subscribe;

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile boolean healthy;

    /**
     * Creates the source and performs the initial load (plus subscription when
     * {@code subscribe} is true and {@code keys} is non-empty).
     *
     * @param daprFacade the Dapr facade used for configuration reads
     * @param storeName  the Dapr configuration store component name
     * @param keys       keys to load/subscribe to; an empty list loads all keys the store returns
     * @param priority   source priority (higher overrides lower)
     * @param subscribe  whether to subscribe to configuration changes
     */
    public DaprConfigSource(DaprFacade daprFacade, String storeName, List<String> keys,
                            int priority, boolean subscribe) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.storeName = Objects.requireNonNull(storeName, "storeName must not be null");
        this.keys = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
        this.priority = priority;
        this.subscribe = subscribe;
        load();
        if (subscribe && !this.keys.isEmpty()) {
            subscribeToChanges();
        }
    }

    @Override
    public String getType() {
        return "dapr";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Map<String, Object> loadConfig() {
        return new HashMap<>(cache);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        Object cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Not part of the initial key set - try a direct read so ad-hoc keys work.
        try {
            String value = daprFacade.getConfiguration(storeName, key);
            if (value != null) {
                cache.put(key, value);
            }
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Dapr configuration read failed for store='{}' key='{}', treating as miss: {}",
                    storeName, key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        return load();
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    private boolean load() {
        try {
            Map<String, String> loaded = daprFacade.getConfiguration(storeName, keys);
            cache.clear();
            loaded.forEach((k, v) -> {
                if (v != null) {
                    cache.put(k, v);
                }
            });
            healthy = true;
            log.debug("Loaded {} configuration entries from Dapr store '{}'", cache.size(), storeName);
            return true;
        } catch (Exception e) {
            healthy = false;
            log.warn("Failed to load configuration from Dapr store '{}': {}", storeName, e.getMessage());
            return false;
        }
    }

    private void subscribeToChanges() {
        try {
            daprFacade.subscribeConfiguration(storeName, keys, changed -> {
                changed.forEach((k, v) -> {
                    if (v != null) {
                        cache.put(k, v);
                    } else {
                        cache.remove(k);
                    }
                });
                log.debug("Applied {} configuration change(s) from Dapr store '{}'",
                        changed.size(), storeName);
            });
        } catch (Exception e) {
            log.warn("Failed to subscribe to Dapr configuration changes for store '{}': {}",
                    storeName, e.getMessage());
        }
    }
}
