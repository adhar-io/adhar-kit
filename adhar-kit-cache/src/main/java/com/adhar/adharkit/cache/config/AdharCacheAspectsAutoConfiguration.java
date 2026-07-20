package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.aspect.CacheLockAspect;
import com.adhar.adharkit.cache.aspect.CacheRefreshAspect;
import com.adhar.adharkit.cache.aspect.CachingAspect;
import com.adhar.adharkit.cache.aspect.MultiLevelCacheAspect;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.metrics.CacheMetricsBinder;
import com.adhar.adharkit.cache.multilevel.InMemorySecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.refresh.CacheRefreshScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration wiring the runtime behavior behind the caching annotations:
 * {@link CachingAspect} (@Cacheable/@CachePut/@CacheEvict), {@link CacheLockAspect}
 * (@CacheLock), {@link MultiLevelCacheAspect} (@MultiLevelCache),
 * {@link CacheRefreshAspect} (@CacheRefresh), plus the {@link CacheMetricsBinder}
 * and {@link CacheRefreshScheduler} support components.
 *
 * <p>Enabled by default; disable with {@code adhar.cache.aspects.enabled=false}.
 * Every bean is {@code @ConditionalOnMissingBean} so applications can override
 * any piece (e.g. supply a Redis-backed {@link SecondLevelCache} via
 * {@link com.adhar.adharkit.cache.multilevel.SpringCacheSecondLevelCache}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(AdharCacheProperties.class)
@ConditionalOnClass(ProceedingJoinPoint.class)
@ConditionalOnProperty(prefix = "adhar.cache.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdharCacheAspectsAutoConfiguration {

    /**
     * The module's Caffeine cache manager singleton, exposed as a bean.
     *
     * @return the cache manager
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager adharKitCacheManager() {
        return CacheManager.getInstance();
    }

    /**
     * SpEL-based key generator shared by all aspects.
     *
     * @return the key generator
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheKeyGenerator cacheKeyGenerator() {
        return new CacheKeyGenerator();
    }

    /**
     * Core caching aspect for @Cacheable, @CachePut and @CacheEvict.
     *
     * @param adharKitCacheManager the cache manager
     * @param cacheKeyGenerator    the key generator
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CachingAspect cachingAspect(CacheManager adharKitCacheManager,
                                       CacheKeyGenerator cacheKeyGenerator) {
        return new CachingAspect(adharKitCacheManager, cacheKeyGenerator);
    }

    /**
     * Single-flight stampede protection for @CacheLock.
     *
     * @param cacheKeyGenerator the key generator
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheLockAspect cacheLockAspect(CacheKeyGenerator cacheKeyGenerator) {
        return new CacheLockAspect(cacheKeyGenerator);
    }

    /**
     * Default in-memory L2; override with a distributed implementation
     * (e.g. {@code SpringCacheSecondLevelCache} over a Redis CacheManager).
     *
     * @return the second-level cache
     */
    @Bean
    @ConditionalOnMissingBean
    public SecondLevelCache secondLevelCache() {
        return new InMemorySecondLevelCache();
    }

    /**
     * L1/L2 read-through and write-through composition.
     *
     * @param adharKitCacheManager the L1 cache manager
     * @param secondLevelCache     the L2 backend
     * @return the multi-level cache service
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCacheService multiLevelCacheService(CacheManager adharKitCacheManager,
                                                         SecondLevelCache secondLevelCache) {
        return new MultiLevelCacheService(adharKitCacheManager, secondLevelCache);
    }

    /**
     * Aspect for @MultiLevelCache.
     *
     * @param multiLevelCacheService the L1/L2 composition
     * @param cacheKeyGenerator      the key generator
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCacheAspect multiLevelCacheAspect(MultiLevelCacheService multiLevelCacheService,
                                                       CacheKeyGenerator cacheKeyGenerator) {
        return new MultiLevelCacheAspect(multiLevelCacheService, cacheKeyGenerator);
    }

    /**
     * Background refresh scheduler for @CacheRefresh registrations.
     *
     * @param adharKitCacheManager the cache manager
     * @return the scheduler (closed on context shutdown)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CacheRefreshScheduler cacheRefreshScheduler(CacheManager adharKitCacheManager) {
        return new CacheRefreshScheduler(adharKitCacheManager);
    }

    /**
     * Aspect for @CacheRefresh.
     *
     * @param cacheRefreshScheduler the scheduler
     * @param cacheKeyGenerator     the key generator
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheRefreshAspect cacheRefreshAspect(CacheRefreshScheduler cacheRefreshScheduler,
                                                 CacheKeyGenerator cacheKeyGenerator) {
        return new CacheRefreshAspect(cacheRefreshScheduler, cacheKeyGenerator);
    }

    /**
     * Micrometer binding, active only when micrometer-core is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    public static class CacheMetricsConfiguration {

        /**
         * Binds cache statistics to Micrometer. Spring Boot's metrics
         * auto-configuration applies all {@code MeterBinder} beans to the
         * registry automatically.
         *
         * @param adharKitCacheManager the cache manager
         * @return the metrics binder
         */
        @Bean
        @ConditionalOnMissingBean
        public CacheMetricsBinder cacheMetricsBinder(CacheManager adharKitCacheManager) {
            return new CacheMetricsBinder(adharKitCacheManager);
        }
    }
}
