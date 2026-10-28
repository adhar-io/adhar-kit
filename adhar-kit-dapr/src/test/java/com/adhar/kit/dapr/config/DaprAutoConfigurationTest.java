package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprComponents;
import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.aspect.DaprBindingAspect;
import com.adhar.kit.dapr.aspect.DaprConfigurationAspect;
import com.adhar.kit.dapr.aspect.DaprInvokeAspect;
import com.adhar.kit.dapr.aspect.DaprLockAspect;
import com.adhar.kit.dapr.aspect.DaprPublishAspect;
import com.adhar.kit.dapr.aspect.DaprStateAspect;
import com.adhar.kit.dapr.client.AdharDaprClient;
import com.adhar.kit.dapr.health.DaprHealthIndicator;
import com.adhar.kit.dapr.invoke.DaprServiceClients;
import com.adhar.kit.dapr.pubsub.DaprEventDispatcher;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import com.adhar.kit.dapr.web.DaprAppCallbackHandlerMapping;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the wiring of {@link DaprAutoConfiguration}: one client (none in memory mode), the
 * facade as the shared instance, defaults from properties, and the callback endpoint only in
 * servlet applications.
 */
class DaprAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DaprAutoConfiguration.class));

    @AfterEach
    void clearSharedInstance() {
        DaprFacade.setInstance(null);
    }

    @Test
    void withoutASidecarItRunsInMemoryWithoutAClient() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DaprFacade.class);
            assertThat(context).doesNotHaveBean(DaprClient.class);
            assertThat(context).doesNotHaveBean(DaprPreviewClient.class);
            DaprFacade facade = context.getBean(DaprFacade.class);
            assertThat(facade.mode()).isEqualTo(DaprMode.MEMORY);
            assertThat(facade.isAvailable()).isTrue();
            assertThat(DaprFacade.getInstance()).isSameAs(facade);
            assertThat(context).hasSingleBean(AdharDaprClient.class);
            assertThat(context).hasSingleBean(DaprInvocationResilience.class);
            assertThat(context).hasSingleBean(DaprResilienceRegistry.class);
            assertThat(context).hasSingleBean(DaprStateAspect.class);
            assertThat(context).hasSingleBean(DaprPublishAspect.class);
            assertThat(context).hasSingleBean(DaprLockAspect.class);
            assertThat(context).hasSingleBean(DaprBindingAspect.class);
            assertThat(context).hasSingleBean(DaprConfigurationAspect.class);
            assertThat(context).hasSingleBean(DaprInvokeAspect.class);
            assertThat(context).hasSingleBean(DaprSubscriptionRegistrar.class);
            assertThat(context).hasSingleBean(DaprEventDispatcher.class);
            assertThat(context).hasSingleBean(DaprSidecarLifecycle.class);
            assertThat(context).hasSingleBean(DaprHealthIndicator.class);
            assertThat(context).hasSingleBean(DaprServiceClients.class);
            assertThat(context).doesNotHaveBean(DaprAppCallbackHandlerMapping.class);
        });
    }

    @Test
    void aSidecarPortSelectsSidecarModeWithOneClientSeenAsBothInterfaces() {
        contextRunner.withPropertyValues("DAPR_HTTP_PORT=3999", "DAPR_GRPC_PORT=3998",
                        "adhar.dapr.sidecar.wait-on-startup=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DaprClient.class);
                    assertThat(context).hasSingleBean(DaprPreviewClient.class);
                    assertThat(context.getBean(DaprPreviewClient.class)).isSameAs(context.getBean(DaprClient.class));
                    DaprFacade facade = context.getBean(DaprFacade.class);
                    assertThat(facade.mode()).isEqualTo(DaprMode.SIDECAR);
                    assertThat(facade.client()).isSameAs(context.getBean(DaprClient.class));
                    assertThat(facade.connection().httpEndpoint()).isEqualTo("http://127.0.0.1:3999");
                });
    }

    @Test
    void componentNamesAndModeComeFromProperties() {
        contextRunner.withPropertyValues("adhar.dapr.mode=memory", "adhar.dapr.pubsub=events",
                        "adhar.dapr.state-store=cache-store", "adhar.dapr.config-store=flags",
                        "adhar.dapr.app-id=orders", "adhar.dapr.service-invocation.timeout=2500")
                .run(context -> {
                    DaprComponents components = context.getBean(DaprComponents.class);
                    assertThat(components.pubsub()).isEqualTo("events");
                    assertThat(components.stateStore()).isEqualTo("cache-store");
                    assertThat(components.configStore()).isEqualTo("flags");
                    assertThat(context.getBean(DaprConnectionDetails.class).appId()).isEqualTo("orders");
                    assertThat(context.getBean(DaprResilienceRegistry.class).settings().timeout())
                            .isEqualTo(Duration.ofMillis(2500));
                });
    }

    @Test
    void supersededNestedComponentNamesAreStillHonoured() {
        contextRunner.withPropertyValues("adhar.dapr.secrets.store-name=vault",
                        "adhar.dapr.configuration-store=legacy-config")
                .run(context -> {
                    DaprComponents components = context.getBean(DaprComponents.class);
                    assertThat(components.secretStore()).isEqualTo("vault");
                    assertThat(components.configStore()).isEqualTo("legacy-config");
                });
    }

    @Test
    void memorySecretsAndConfigurationAreSeededFromProperties() {
        contextRunner.withPropertyValues("adhar.dapr.memory.secrets.db.password=s3cret",
                        "adhar.dapr.memory.configuration.feature.checkout=on")
                .run(context -> {
                    DaprFacade facade = context.getBean(DaprFacade.class);
                    assertThat(facade.secrets().get("db")).isEqualTo(Map.of("password", "s3cret"));
                    assertThat(facade.configuration().value("feature.checkout")).isEqualTo("on");
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

    @Test
    void servletApplicationsServeTheCallbackEndpoint() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DaprAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DaprAppCallbackHandlerMapping.class);
                    assertThat(context.getBean(DaprSubscriptionRegistrar.class).isHttpDelivery()).isTrue();
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
