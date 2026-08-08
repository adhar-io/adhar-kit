package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dapr secret-store source, exposing secrets as configuration properties the
 * same way {@link VaultConfigSource} does for Vault.
 *
 * <p>All secrets are bulk-loaded once at construction (and on
 * {@link #refresh()}): a single-entry secret is exposed under its secret name,
 * a multi-entry secret under {@code "name.key"}. Individual lookups that miss
 * the cache fall through to a direct {@link DaprFacade#getSecret} call so
 * stores that disallow bulk reads still resolve per-key.</p>
 *
 * <p>Secret values are never logged. Sidecar failures are reported as misses
 * so lower-priority sources or defaults take over.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprSecretConfigSource implements ConfigSource {

    private final DaprFacade daprFacade;
    private final String storeName;
    private final int priority;

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile boolean healthy;

    /**
     * Creates the source and performs the initial bulk load.
     *
     * @param daprFacade the Dapr facade used for secret reads
     * @param storeName  the Dapr secret store component name
     * @param priority   source priority (higher overrides lower)
     */
    public DaprSecretConfigSource(DaprFacade daprFacade, String storeName, int priority) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.storeName = Objects.requireNonNull(storeName, "storeName must not be null");
        this.priority = priority;
        load();
    }

    @Override
    public String getType() {
        return "dapr-secrets";
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
        try {
            String value = daprFacade.getSecret(storeName, key);
            if (value != null) {
                cache.put(key, value);
            }
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Dapr secret read failed for store='{}' key='{}', treating as miss: {}",
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
            Map<String, String> secrets = daprFacade.getBulkSecrets(storeName);
            cache.clear();
            secrets.forEach((k, v) -> {
                if (v != null) {
                    cache.put(k, v);
                }
            });
            healthy = true;
            log.debug("Loaded {} secret entries from Dapr store '{}'", cache.size(), storeName);
            return true;
        } catch (Exception e) {
            // Some secret stores disallow bulk reads; per-key getProperty still works.
            healthy = false;
            log.warn("Bulk secret load from Dapr store '{}' failed (per-key lookups still available): {}",
                    storeName, e.getMessage());
            return false;
        }
    }
}
