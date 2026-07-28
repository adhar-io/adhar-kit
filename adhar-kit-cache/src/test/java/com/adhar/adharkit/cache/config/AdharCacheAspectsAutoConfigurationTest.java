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
import com.adhar.adharkit.cache.refresh.CacheRefreshScheduler;
import com.adhar.adharkit.cache.warmup.CacheWarmupProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharCacheAspectsAutoConfiguration} wiring.
 */
@DisplayName("AdharCacheAspectsAutoConfiguration Tests")
class AdharCacheAspectsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AdharCacheAspectsAutoConfiguration.class));

    @Test
    @DisplayName("registers all aspects and support beans by default")
    void registersEverythingByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CacheKeyGenerator.class);
            assertThat(context).hasSingleBean(CachingAspect.class);
            assertThat(context).hasSingleBean(CacheLockAspect.class);
            assertThat(context).hasSingleBean(MultiLevelCacheAspect.class);
            assertThat(context).hasSingleBean(MultiLevelCacheService.class);
            assertThat(context).hasSingleBean(CacheRefreshAspect.class);
            assertThat(context).hasSingleBean(CacheRefreshScheduler.class);
            assertThat(context).hasSingleBean(CacheMetricsBinder.class);
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(CacheCircuitBreakerAspect.class);
            assertThat(context).hasSingleBean(CacheRateLimitAspect.class);
            assertThat(context).hasSingleBean(CachePartitionAspect.class);
            assertThat(context).hasSingleBean(CacheMonitorAspect.class);
            assertThat(context).hasSingleBean(CacheWarmupProcessor.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isInstanceOf(InMemorySecondLevelCache.class);
            assertThat(context).getBean(KeyPartitionResolver.class)
                .isInstanceOf(ThreadLocalKeyPartitionResolver.class);
        });
    }

    @Test
    @DisplayName("adhar.cache.aspects.enabled=false disables everything")
    void disabledByProperty() {
        runner.withPropertyValues("adhar.cache.aspects.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CachingAspect.class);
            assertThat(context).doesNotHaveBean(CacheLockAspect.class);
            assertThat(context).doesNotHaveBean(MultiLevelCacheAspect.class);
            assertThat(context).doesNotHaveBean(CacheRefreshScheduler.class);
            assertThat(context).doesNotHaveBean(CacheMetricsBinder.class);
            assertThat(context).doesNotHaveBean(CacheCircuitBreakerAspect.class);
            assertThat(context).doesNotHaveBean(CacheRateLimitAspect.class);
            assertThat(context).doesNotHaveBean(CachePartitionAspect.class);
            assertThat(context).doesNotHaveBean(CacheMonitorAspect.class);
            assertThat(context).doesNotHaveBean(CacheWarmupProcessor.class);
        });
    }

    @Test
    @DisplayName("user-supplied beans win over the defaults")
    void conditionalOnMissingBean() {
        runner.withUserConfiguration(CustomSecondLevelCacheConfig.class).run(context -> {
            assertThat(context).hasSingleBean(SecondLevelCache.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isSameAs(context.getBean(CustomSecondLevelCacheConfig.class).cache);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSecondLevelCacheConfig {
        final InMemorySecondLevelCache cache = new InMemorySecondLevelCache();

        @Bean
        SecondLevelCache customSecondLevelCache() {
            return cache;
        }
    }
}
