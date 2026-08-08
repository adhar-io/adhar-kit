package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.multilevel.DaprSecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.adharkit.cache.properties.AdharCacheDaprProperties;
import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration contributing a Dapr state-store-backed
 * {@link SecondLevelCache}, so the {@link MultiLevelCacheService} composition
 * behind {@code CacheFacade}/{@code @MultiLevelCache} becomes distributed
 * (Caffeine L1 + Dapr state store L2).
 *
 * <p>Activates only when:</p>
 * <ul>
 *   <li>{@link DaprFacade} (adhar-kit-dapr) is on the classpath,</li>
 *   <li>{@code adhar.dapr.enabled=true} is set, and</li>
 *   <li>no other {@link SecondLevelCache} bean is configured
 *       ({@code @ConditionalOnMissingBean}) — a user-supplied distributed
 *       backend (e.g. a Redis-backed
 *       {@link com.adhar.adharkit.cache.multilevel.SpringCacheSecondLevelCache})
 *       always wins.</li>
 * </ul>
 *
 * <p>Runs before {@link AdharCacheAspectsAutoConfiguration} so its default
 * in-memory L2 backs off. The target state store component is configured via
 * {@code adhar.kit.cache.dapr.state-store} (default {@code "statestore"}).
 * Default local-only behavior is unchanged when Dapr is absent or disabled.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration(before = AdharCacheAspectsAutoConfiguration.class)
@ConditionalOnClass(DaprFacade.class)
@ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AdharCacheDaprProperties.class)
public class AdharCacheDaprAutoConfiguration {

    /**
     * Dapr state-store-backed L2 cache. Uses the application's
     * {@link DaprFacade} bean when one is present (e.g. from adhar-kit-dapr's
     * own auto-configuration), falling back to {@link DaprFacade#getInstance()}.
     *
     * @param properties the Dapr cache properties
     * @param daprFacade provider for an existing {@link DaprFacade} bean
     * @return the distributed second-level cache
     */
    @Bean
    @ConditionalOnMissingBean(SecondLevelCache.class)
    public SecondLevelCache daprSecondLevelCache(AdharCacheDaprProperties properties,
                                                 ObjectProvider<DaprFacade> daprFacade) {
        log.info("Configuring Dapr state-store-backed L2 cache (state store: '{}')",
            properties.getStateStore());
        return new DaprSecondLevelCache(
            daprFacade.getIfAvailable(DaprFacade::getInstance),
            properties.getStateStore());
    }
}
