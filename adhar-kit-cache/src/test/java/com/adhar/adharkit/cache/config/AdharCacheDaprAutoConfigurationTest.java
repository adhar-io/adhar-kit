package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.multilevel.DaprSecondLevelCache;
import com.adhar.adharkit.cache.multilevel.InMemorySecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import com.adhar.adharkit.cache.multilevel.SecondLevelCache;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharCacheDaprAutoConfiguration} activation and back-off.
 */
@DisplayName("AdharCacheDaprAutoConfiguration Tests")
class AdharCacheDaprAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AdharCacheDaprAutoConfiguration.class,
            AdharCacheAspectsAutoConfiguration.class))
        .withUserConfiguration(MockDaprFacadeConfig.class);

    @Test
    @DisplayName("backs off when adhar.dapr.enabled is not set")
    void backsOffByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DaprSecondLevelCache.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isInstanceOf(InMemorySecondLevelCache.class);
        });
    }

    @Test
    @DisplayName("backs off when adhar.dapr.enabled=false")
    void backsOffWhenDisabled() {
        runner.withPropertyValues("adhar.dapr.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DaprSecondLevelCache.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isInstanceOf(InMemorySecondLevelCache.class);
        });
    }

    @Test
    @DisplayName("adhar.dapr.enabled=true registers the Dapr L2 with the default state store")
    void activatesWhenEnabled() {
        runner.withPropertyValues("adhar.dapr.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(SecondLevelCache.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isInstanceOf(DaprSecondLevelCache.class);
            assertThat(context.getBean(DaprSecondLevelCache.class).getStateStoreName())
                .isEqualTo("statestore");
        });
    }

    @Test
    @DisplayName("adhar.kit.cache.dapr.state-store overrides the state store name")
    void customStateStore() {
        runner.withPropertyValues(
            "adhar.dapr.enabled=true",
            "adhar.kit.cache.dapr.state-store=my-store"
        ).run(context ->
            assertThat(context.getBean(DaprSecondLevelCache.class).getStateStoreName())
                .isEqualTo("my-store"));
    }

    @Test
    @DisplayName("the MultiLevelCacheService composition uses the Dapr L2")
    void multiLevelServiceUsesDaprL2() {
        runner.withPropertyValues("adhar.dapr.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(MultiLevelCacheService.class);
            assertThat(context).getBean(SecondLevelCache.class)
                .isInstanceOf(DaprSecondLevelCache.class);
        });
    }

    @Test
    @DisplayName("backs off to a user-supplied SecondLevelCache (@ConditionalOnMissingBean)")
    void userSuppliedSecondLevelCacheWins() {
        runner.withUserConfiguration(CustomSecondLevelCacheConfig.class)
            .withPropertyValues("adhar.dapr.enabled=true")
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
            .withPropertyValues("adhar.dapr.enabled=true")
            .run(context -> {
                assertThat(context).doesNotHaveBean("daprSecondLevelCache");
                assertThat(context).getBean(SecondLevelCache.class)
                    .isInstanceOf(InMemorySecondLevelCache.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockDaprFacadeConfig {
        @Bean
        DaprFacade daprFacade() {
            return Mockito.mock(DaprFacade.class);
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
