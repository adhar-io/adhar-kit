package com.adhar.adharkit.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Dapr state-store-backed distributed cache
 * tier ({@code com.adhar.adharkit.cache.multilevel.DaprSecondLevelCache}).
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   dapr:
 *     enabled: true          # activates the Dapr L2 (owned by adhar-kit-dapr)
 *   kit:
 *     cache:
 *       dapr:
 *         state-store: statestore
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.kit.cache.dapr")
public class AdharCacheDaprProperties {

    /**
     * Name of the Dapr state store component used as the distributed L2 cache.
     */
    private String stateStore = "statestore";
}
