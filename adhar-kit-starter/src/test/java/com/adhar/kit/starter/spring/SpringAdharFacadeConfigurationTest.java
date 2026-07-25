package com.adhar.kit.starter.spring;

import com.adhar.kit.metrics.MetricsFacade;
import com.adhar.kit.starter.AdharFacade;
import com.adhar.kit.starter.AdharFacadeCustomizer;
import com.adhar.kit.starter.config.AdharKitAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link AdharFacade} bean wiring in {@link SpringAdharFacadeConfiguration}:
 * module gating sourced from {@code adhar.kit.modules.*}, and {@link AdharFacadeCustomizer}
 * application.
 */
class SpringAdharFacadeConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AdharKitAutoConfiguration.class, SpringAdharFacadeConfiguration.class));

    @BeforeEach
    @AfterEach
    void resetFacadeSingleton() {
        AdharFacade.resetForTesting();
    }

    @Test
    void facadeBean_isGatedByModuleProperties() {
        runner.withPropertyValues("adhar.kit.modules.ai.enabled=false").run(context -> {
            AdharFacade facade = context.getBean(AdharFacade.class);
            assertThatThrownBy(facade::getAi).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void facadeBean_fallsBackToAllEnabled_whenModuleAccessBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringAdharFacadeConfiguration.class))
                .run(context -> {
                    AdharFacade facade = context.getBean(AdharFacade.class);
                    assertThat(facade.getAi()).isNotNull();
                });
    }

    @Test
    void customizerBean_overridesSubFacade() {
        runner.withUserConfiguration(CustomizerConfig.class).run(context -> {
            AdharFacade facade = context.getBean(AdharFacade.class);
            assertThat(facade.getMetrics()).isSameAs(CustomizerConfig.STUB_METRICS);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfig {
        static final MetricsFacade STUB_METRICS = MetricsFacade.getInstance();

        @Bean
        AdharFacadeCustomizer metricsOverrideCustomizer() {
            return facade -> facade.setMetrics(STUB_METRICS);
        }
    }
}
