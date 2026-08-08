package com.adhar.adharkit.cache.multilevel;

import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;

/**
 * {@link SecondLevelCache} backed by a Dapr state store via
 * {@link DaprFacade}, making the L1 Caffeine / L2 composition of
 * {@link MultiLevelCacheService} distributed through the Dapr sidecar
 * (Redis, Cosmos DB, DynamoDB, ... — whatever the configured Dapr state
 * component is).
 *
 * <p>Keys are namespaced per logical cache as {@code "cacheName:key"} so
 * multiple caches can safely share one state store. Per-entry TTL is
 * honored via {@link DaprFacade#saveStateWithTTL} ({@code ttlInSeconds}
 * metadata); a null/zero/negative TTL stores the entry without expiry.</p>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *   <li>{@link #clear(String)} is unsupported and throws
 *       {@link UnsupportedOperationException}: the Dapr state API has no
 *       native key scan/enumeration, so the entries of a cache cannot be
 *       discovered for bulk removal (the module's Redis-backed L2 likewise
 *       keeps no key index). Evict entries individually instead.</li>
 *   <li>{@link #get(String, Object)} deserializes with {@code Object.class};
 *       Dapr's JSON serializer therefore returns complex POJOs as generic
 *       structures (e.g. {@code Map}). Prefer simple values (Strings,
 *       numbers) or types that round-trip through JSON untyped.</li>
 *   <li>Reads degrade gracefully: a sidecar/store failure on {@code get} is
 *       logged and treated as a cache miss so the multi-level read-through
 *       can still fall back to the loader. Writes and evictions propagate
 *       failures, since silently losing them would risk stale data.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprSecondLevelCache implements SecondLevelCache {

    private final DaprFacade daprFacade;
    private final String stateStoreName;

    /**
     * Creates the Dapr-backed L2.
     *
     * @param daprFacade     the Dapr facade used for state operations
     * @param stateStoreName the Dapr state store component name (e.g. "statestore")
     */
    public DaprSecondLevelCache(DaprFacade daprFacade, String stateStoreName) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.stateStoreName = Objects.requireNonNull(stateStoreName, "stateStoreName must not be null");
    }

    @Override
    public Object get(String cacheName, Object key) {
        String stateKey = stateKey(cacheName, key);
        try {
            return daprFacade.getState(stateStoreName, stateKey, Object.class);
        } catch (Exception e) {
            log.warn("Dapr L2 get failed for store='{}' key='{}', treating as miss: {}",
                stateStoreName, stateKey, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String cacheName, Object key, Object value, Duration ttl) {
        String stateKey = stateKey(cacheName, key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            daprFacade.saveState(stateStoreName, stateKey, value);
        } else {
            daprFacade.saveStateWithTTL(stateStoreName, stateKey, value, ttl);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        daprFacade.deleteState(stateStoreName, stateKey(cacheName, key));
    }

    /**
     * Unsupported: Dapr state stores expose no key enumeration, so the
     * entries belonging to {@code cacheName} cannot be discovered.
     *
     * @param cacheName logical cache name
     * @throws UnsupportedOperationException always
     */
    @Override
    public void clear(String cacheName) {
        throw new UnsupportedOperationException(
            "Dapr state stores do not support key enumeration; clear('" + cacheName
                + "') cannot be implemented. Evict entries individually.");
    }

    /**
     * The Dapr state store component name this L2 writes to.
     *
     * @return the state store name
     */
    public String getStateStoreName() {
        return stateStoreName;
    }

    private String stateKey(String cacheName, Object key) {
        return cacheName + ":" + key;
    }
}
