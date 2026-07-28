package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.aspect.CacheCircuitBreakerAspect;
import com.adhar.adharkit.cache.aspect.CacheLockAspect;
import com.adhar.adharkit.cache.aspect.CacheMonitorAspect;
import com.adhar.adharkit.cache.aspect.CachePartitionAspect;
import com.adhar.adharkit.cache.aspect.CacheRateLimitAspect;
import com.adhar.adharkit.cache.aspect.CacheRefreshAspect;
import com.adhar.adharkit.cache.aspect.CachingAspect;
import com.adhar.adharkit.cache.aspect.MultiLevelCacheAspect;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.metrics.CacheMetricsBinder;
import com.adhar.adharkit.cache.multilevel.InMemorySecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.adharkit.cache.partition.KeyPartitionResolver;
import com.adhar.adharkit.cache.partition.ThreadLocalKeyPartitionResolver;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.adhar.adharkit.cache.refresh.CacheRefreshScheduler;
import com.adhar.adharkit.cache.warmup.CacheWarmupProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ObjectProvider;
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
 * {@link CacheRefreshAspect} (@CacheRefresh), {@link CacheCircuitBreakerAspect}
 * (@CacheCircuitBreaker), {@link CacheRateLimitAspect} (@CacheRateLimit),
 * {@link CachePartitionAspect} (@CachePartition), {@link CacheMonitorAspect}
 * (@CacheMonitor), the {@link CacheWarmupProcessor} (@CacheWarmup), plus the
 * {@link CacheMetricsBinder}, {@link KeyPartitionResolver} and
 * {@link CacheRefreshScheduler} support components.
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
     * Default partition resolver: the module's thread-local tenant holder.
     * Override with an adapter over your own tenant source (e.g. commons'
     * {@code TenantContext}) by supplying a {@link KeyPartitionResolver} bean.
     *
     * @return the partition resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyPartitionResolver keyPartitionResolver() {
        return new ThreadLocalKeyPartitionResolver();
    }

    /**
     * SpEL-based key generator shared by all aspects, configured for tenant
     * key partitioning from {@code adhar.cache.partitioning.*}.
     *
     * @param properties           the cache properties
     * @param keyPartitionResolver the partition resolver
     * @return the key generator
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheKeyGenerator cacheKeyGenerator(AdharCacheProperties properties,
                                               KeyPartitionResolver keyPartitionResolver) {
        CacheKeyGenerator generator = new CacheKeyGenerator();
        generator.setPartitionResolver(keyPartitionResolver);
        generator.setPartitioningEnabled(properties.getPartitioning().isEnabled());
        generator.setPartitionSeparator(properties.getPartitioning().getSeparator());
        return generator;
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
     * Self-contained circuit breaker for @CacheCircuitBreaker.
     *
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheCircuitBreakerAspect cacheCircuitBreakerAspect() {
        return new CacheCircuitBreakerAspect();
    }

    /**
     * Token-bucket throughput and concurrency limiting for @CacheRateLimit.
     *
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheRateLimitAspect cacheRateLimitAspect() {
        return new CacheRateLimitAspect();
    }

    /**
     * Tenant-partitioned get-or-compute caching for @CachePartition.
     *
     * @param adharKitCacheManager the cache manager
     * @param cacheKeyGenerator    the key generator
     * @param keyPartitionResolver fallback tenant resolver
     * @param properties           the cache properties (partition separator)
     * @return the aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CachePartitionAspect cachePartitionAspect(CacheManager adharKitCacheManager,
                                                     CacheKeyGenerator cacheKeyGenerator,
                                                     KeyPartitionResolver keyPartitionResolver,
                                                     AdharCacheProperties properties) {
        return new CachePartitionAspect(adharKitCacheManager, cacheKeyGenerator,
            keyPartitionResolver, properties.getPartitioning().getSeparator());
    }

    /**
     * Startup warmup runtime for @CacheWarmup: on {@code ApplicationReadyEvent}
     * every annotated bean method is invoked (async on a dedicated executor by
     * default) to pre-populate its cache.
     *
     * @param adharKitCacheManager the cache manager
     * @return the warmup processor (executor shut down on context close)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CacheWarmupProcessor cacheWarmupProcessor(CacheManager adharKitCacheManager) {
        return new CacheWarmupProcessor(adharKitCacheManager);
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

        /**
         * Hit/miss/latency monitoring and alerting for @CacheMonitor. Records
         * into the application's {@code MeterRegistry} when one is present,
         * falling back to a private {@link SimpleMeterRegistry} otherwise.
         *
         * @param adharKitCacheManager the cache manager
         * @param cacheKeyGenerator    the key generator
         * @param meterRegistry        the registry provider (may be empty)
         * @param cacheMetricsBinder   the shared cache metrics binder
         * @return the aspect
         */
        @Bean
        @ConditionalOnMissingBean
        public CacheMonitorAspect cacheMonitorAspect(CacheManager adharKitCacheManager,
                                                     CacheKeyGenerator cacheKeyGenerator,
                                                     ObjectProvider<MeterRegistry> meterRegistry,
                                                     CacheMetricsBinder cacheMetricsBinder) {
            return new CacheMonitorAspect(adharKitCacheManager, cacheKeyGenerator,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new), cacheMetricsBinder);
        }
    }
}
