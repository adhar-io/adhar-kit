package com.adhar.adharkit.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration of the caches kept in a Dapr state store: the Spring {@code CacheManager} of
 * {@code adhar.cache.provider=dapr} and the distributed L2 of the multi-level cache.
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   cache:
 *     provider: dapr          # @Cacheable goes through Dapr
 *     time-to-live: 30m
 *     dapr:
 *       state-store: statestore   # default: adhar.dapr.state-store
 *       time-to-live:
 *         prices: 5m
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.cache.dapr")
public class AdharCacheDaprProperties {

    /**
     * Whether the Dapr-backed caches are wired (needs adhar-kit-dapr, which is on by default).
     */
    private boolean enabled = true;

    /**
     * Name of the Dapr state store component holding the caches. Defaults to the service's state
     * store (adhar.dapr.state-store, statestore on the platform).
     */
    private String stateStore;

    /**
     * Prefix of every cache key in the state store, keeping caches apart from the service's state.
     */
    private String keyPrefix = "cache:";

    /**
     * Time-to-live per cache name, overriding adhar.cache.time-to-live.
     */
    private Map<String, Duration> timeToLive = new LinkedHashMap<>();

    /**
     * How long a replica trusts its view of a cache's generation; a clear() reaches the other
     * replicas within this time.
     */
    private Duration generationTtl = Duration.ofSeconds(2);
}
