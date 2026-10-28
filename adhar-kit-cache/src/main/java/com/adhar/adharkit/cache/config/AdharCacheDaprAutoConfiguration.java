package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.dapr.DaprCacheManager;
import com.adhar.adharkit.cache.multilevel.DaprSecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.adharkit.cache.properties.AdharCacheDaprProperties;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Caches in a Dapr state store, through the application's {@link DaprFacade}:
 * <ul>
 *   <li>with {@code adhar.cache.provider=dapr}, the Spring {@link org.springframework.cache.CacheManager}
 *       ({@link DaprCacheManager}), so {@code @Cacheable} goes through the sidecar instead of a
 *       Redis connection of the service's own;</li>
 *   <li>the distributed L2 of {@link MultiLevelCacheService} ({@link DaprSecondLevelCache}),
 *       unless the application defines another {@link SecondLevelCache}.</li>
 * </ul>
 *
 * <p>On by default wherever adhar-kit-dapr is (same {@code adhar.dapr.enabled} default as that
 * module); {@code adhar.dapr.enabled=false} or {@code adhar.cache.dapr.enabled=false} turns it
 * off. In Dapr's memory mode the caches live in the process, like the default in-memory L2.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration(
    afterName = "com.adhar.kit.dapr.config.DaprAutoConfiguration",
    before = {AdharCacheAspectsAutoConfiguration.class, RedisConfig.class, AdharCacheAutoConfiguration.class},
    beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@ConditionalOnClass(DaprFacade.class)
@ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "adhar.cache.dapr", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({AdharCacheDaprProperties.class, AdharCacheProperties.class})
public class AdharCacheDaprAutoConfiguration {

    /** The superseded property name of the state store (before 0.2.0). */
    static final String LEGACY_STATE_STORE = "adhar.kit.cache.dapr.state-store";

    /**
     * Dapr state-store-backed L2 cache. Its cache manager is private: a {@code CacheManager} bean
     * would make the Redis, Kafka and Spring Boot cache managers back off even when
     * {@code @Cacheable} is not meant to go through Dapr.
     *
     * @param dapr        the application's facade, or the shared one
     * @param properties  the Dapr cache properties
     * @param cache       the general cache properties
     * @param environment the environment (for the legacy state store key)
     * @return the distributed second-level cache
     */
    @Bean
    @ConditionalOnMissingBean(SecondLevelCache.class)
    public SecondLevelCache daprSecondLevelCache(ObjectProvider<DaprFacade> dapr, AdharCacheDaprProperties properties,
                                                 AdharCacheProperties cache, Environment environment) {
        return new DaprSecondLevelCache(newCacheManager(dapr, properties, cache, environment));
    }

    /**
     * {@code @Cacheable} through Dapr, when {@code adhar.cache.provider=dapr}. Registered before
     * the Redis, Kafka and Spring Boot cache managers, which then back off.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.cache", name = "provider", havingValue = "dapr")
    static class DaprSpringCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean(org.springframework.cache.CacheManager.class)
        DaprCacheManager cacheManager(ObjectProvider<DaprFacade> dapr, AdharCacheDaprProperties properties,
                                      AdharCacheProperties cache, Environment environment) {
            return newCacheManager(dapr, properties, cache, environment);
        }
    }

    static DaprCacheManager newCacheManager(ObjectProvider<DaprFacade> dapr, AdharCacheDaprProperties properties,
                                            AdharCacheProperties cache, Environment environment) {
        DaprFacade facade = dapr.getIfAvailable(DaprFacade::getInstance);
        String store = stateStore(properties, environment, facade);
        log.debug("Dapr caches in state store '{}' ({} mode)", store, facade.mode().name().toLowerCase());
        return new DaprCacheManager(facade, store, properties.getKeyPrefix(), cache.getTimeToLive(),
                properties.getTimeToLive(), properties.getGenerationTtl(), cache.isAllowNullValues());
    }

    static String stateStore(AdharCacheDaprProperties properties, Environment environment, DaprFacade facade) {
        if (properties.getStateStore() != null && !properties.getStateStore().isBlank()) {
            return properties.getStateStore();
        }
        String legacy = environment.getProperty(LEGACY_STATE_STORE);
        if (legacy != null && !legacy.isBlank()) {
            log.warn("{} is superseded by adhar.cache.dapr.state-store", LEGACY_STATE_STORE);
            return legacy;
        }
        return facade.components().stateStore();
    }
}
