package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.aspect.DaprPublishAspect;
import com.adhar.kit.dapr.aspect.DaprStateAspect;
import com.adhar.kit.dapr.client.AdharDaprClient;
import com.adhar.kit.dapr.pubsub.DaprEventDispatcher;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionController;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link DaprAutoConfiguration} wires a working Spring context: default beans
 * present, and the module can be disabled entirely via {@code adhar.dapr.enabled=false}.
 *
 * <p>{@code DaprAutoConfiguration} itself is excluded from the jacoco coverage gate
 * ({@code **}{@code /*AutoConfiguration.class} and its nested classes), so this test exists
 * purely to catch real Spring wiring regressions that unit tests on the individual beans
 * cannot.</p>
 */
class DaprAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DaprAutoConfiguration.class));

    @Test
    void contextLoads_withDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AdharDaprClient.class);
            assertThat(context).hasSingleBean(DaprFacade.class);
            assertThat(context).hasSingleBean(DaprInvocationResilience.class);
            assertThat(context).hasSingleBean(DaprStateAspect.class);
            assertThat(context).hasSingleBean(DaprPublishAspect.class);
            assertThat(context).hasSingleBean(DaprSubscriptionRegistrar.class);
            assertThat(context).hasSingleBean(DaprEventDispatcher.class);
            // The subscription controller is @ConditionalOnMissingClass("io.dapr.springboot.DaprController"):
            // when the Dapr Spring Boot SDK is on the classpath (it is a dependency of this module),
            // Dapr's own controller serves the subscription endpoints and ours must NOT be registered.
            // Mirror that condition here instead of asserting the bean unconditionally.
            boolean daprControllerPresent = ClassUtils.isPresent(
                    "io.dapr.springboot.DaprController", getClass().getClassLoader());
            if (daprControllerPresent) {
                assertThat(context).doesNotHaveBean(DaprSubscriptionController.class);
            } else {
                assertThat(context).hasSingleBean(DaprSubscriptionController.class);
            }
        });
    }

    @Test
    void moduleDisabled_doesNotCreateAnyBeans() {
        contextRunner.withPropertyValues("adhar.dapr.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DaprFacade.class));
    }

    @Test
    void userSuppliedFacadeBean_isRespected() {
        contextRunner.withUserConfiguration(CustomFacadeConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DaprFacade.class);
                    assertThat(context.getBean(DaprFacade.class)).isSameAs(CustomFacadeConfig.FACADE);
                });
    }

    @org.springframework.context.annotation.Configuration
    static class CustomFacadeConfig {
        static final DaprFacade FACADE =
                new DaprFacade(org.mockito.Mockito.mock(io.dapr.client.DaprClient.class));

        @org.springframework.context.annotation.Bean
        DaprFacade daprFacade() {
            return FACADE;
        }
    }
}
