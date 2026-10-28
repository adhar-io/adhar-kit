package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.dapr.DaprCacheManager;
import com.adhar.adharkit.cache.multilevel.DaprSecondLevelCache;
import com.adhar.adharkit.cache.multilevel.InMemorySecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharCacheDaprAutoConfiguration} activation, back-off and the
 * {@code adhar.cache.provider=dapr} cache manager.
 */
@DisplayName("AdharCacheDaprAutoConfiguration Tests")
class AdharCacheDaprAutoConfigurationTest {

    record Quote(String symbol, double price, Instant at) {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AdharCacheDaprAutoConfiguration.class,
            AdharCacheAspectsAutoConfiguration.class))
        .withUserConfiguration(MemoryDaprFacadeConfig.class);

    @Test
    @DisplayName("on by default, like adhar-kit-dapr itself: the L2 goes through Dapr")
    void activeByDefault() {
        runner.run(context -> {
            assertThat(context).getBean(SecondLevelCache.class).isInstanceOf(DaprSecondLevelCache.class);
            assertThat(context.getBean(DaprSecondLevelCache.class).getStateStoreName()).isEqualTo("statestore");
            assertThat(context).hasSingleBean(MultiLevelCacheService.class);
            assertThat(context).doesNotHaveBean(CacheManager.class);
        });
    }

    @Test
    @DisplayName("backs off when adhar.dapr.enabled=false or adhar.cache.dapr.enabled=false")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("adhar.dapr.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DaprSecondLevelCache.class);
            assertThat(context).getBean(SecondLevelCache.class).isInstanceOf(InMemorySecondLevelCache.class);
        });
        runner.withPropertyValues("adhar.cache.dapr.enabled=false").run(context ->
            assertThat(context).doesNotHaveBean(DaprSecondLevelCache.class));
    }

    @Test
    @DisplayName("the state store follows adhar.cache.dapr.state-store, the legacy key, then the service default")
    void stateStoreResolution() {
        runner.withPropertyValues("adhar.cache.dapr.state-store=cache-store").run(context ->
            assertThat(context.getBean(DaprSecondLevelCache.class).getStateStoreName()).isEqualTo("cache-store"));
        runner.withPropertyValues("adhar.kit.cache.dapr.state-store=legacy-store").run(context ->
            assertThat(context.getBean(DaprSecondLevelCache.class).getStateStoreName()).isEqualTo("legacy-store"));
    }

    @Test
    @DisplayName("adhar.cache.provider=dapr makes the Spring cache manager Dapr-backed and typed")
    void providerDaprServesTheSpringCacheAbstraction() {
        runner.withPropertyValues("adhar.cache.provider=dapr", "adhar.cache.dapr.time-to-live.quotes=1m")
            .run(context -> {
                CacheManager cacheManager = context.getBean("cacheManager", CacheManager.class);
                assertThat(cacheManager).isInstanceOf(DaprCacheManager.class);
                Cache quotes = cacheManager.getCache("quotes");
                Quote quote = new Quote("ADHR", 12.5, Instant.parse("2026-10-28T00:00:00Z"));
                quotes.put("ADHR", quote);
                assertThat(quotes.get("ADHR", Quote.class)).isEqualTo(quote);
                assertThat(quotes.get("ADHR").get()).isInstanceOf(Quote.class);
                assertThat(quotes.get("missing")).isNull();
                quotes.put("nothing", null);
                assertThat(quotes.get("nothing")).isNotNull();
                assertThat(quotes.get("nothing").get()).isNull();
                assertThat(quotes.get("loaded", () -> "computed")).isEqualTo("computed");
                assertThat(quotes.putIfAbsent("loaded", "other").get()).isEqualTo("computed");
                quotes.clear();
                assertThat(quotes.get("ADHR")).isNull();
                assertThat(cacheManager.getCacheNames()).contains("quotes");
            });
    }

    @Test
    @DisplayName("backs off to a user-supplied SecondLevelCache (@ConditionalOnMissingBean)")
    void userSuppliedSecondLevelCacheWins() {
        runner.withUserConfiguration(CustomSecondLevelCacheConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(SecondLevelCache.class);
                assertThat(context).doesNotHaveBean(DaprSecondLevelCache.class);
                assertThat(context).getBean(SecondLevelCache.class)
                    .isSameAs(context.getBean(CustomSecondLevelCacheConfig.class).cache);
            });
    }

    @Test
    @DisplayName("backs off when DaprFacade is not on the classpath")
    void backsOffWithoutDaprOnClasspath() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                AdharCacheDaprAutoConfiguration.class,
                AdharCacheAspectsAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(DaprFacade.class))
            .run(context -> {
                assertThat(context).doesNotHaveBean("daprSecondLevelCache");
                assertThat(context).getBean(SecondLevelCache.class)
                    .isInstanceOf(InMemorySecondLevelCache.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryDaprFacadeConfig {
        @Bean
        DaprFacade daprFacade() {
            return DaprFacade.builder()
                .connection(DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "memory")::get))
                .build();
        }
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
