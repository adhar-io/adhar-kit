package com.adhar.kit.starter.config;

import com.adhar.kit.starter.AdharFacade;
import com.adhar.kit.starter.AdharModuleAccess;
import com.adhar.kit.starter.actuator.AdharKitEndpoint;
import com.adhar.kit.starter.lifecycle.AdharGracefulShutdown;
import com.adhar.kit.starter.lifecycle.AdharShutdownHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link AdharKitAutoConfiguration} wiring: the shared
 * {@link AdharModuleAccess}, the {@code AdharKitModuleRegistry}, and the
 * optional {@link AdharKitEndpoint}.
 */
class AdharKitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharKitAutoConfiguration.class));

    @BeforeEach
    @AfterEach
    void resetFacadeSingleton() {
        AdharFacade.resetForTesting();
    }

    @Test
    void defaultConfiguration_registersModuleAccessAndRegistry() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AdharModuleAccess.class);
            assertThat(context).hasSingleBean(AdharKitAutoConfiguration.AdharKitModuleRegistry.class);

            AdharModuleAccess moduleAccess = context.getBean(AdharModuleAccess.class);
            AdharKitAutoConfiguration.AdharKitModuleRegistry registry =
                    context.getBean(AdharKitAutoConfiguration.AdharKitModuleRegistry.class);

            assertThat(moduleAccess.isEnabled("ai")).isFalse(); // default-disabled module
            assertThat(registry.isModuleEnabled("ai")).isFalse();
            assertThat(moduleAccess.isEnabled("metrics")).isTrue();
            assertThat(registry.isModuleEnabled("metrics")).isTrue();
        });
    }

    @Test
    void disabledModuleProperty_flowsToRegistryAndModuleAccess_sameSourceOfTruth() {
        runner.withPropertyValues("adhar.kit.modules.metrics=false").run(context -> {
            AdharModuleAccess moduleAccess = context.getBean(AdharModuleAccess.class);
            AdharKitAutoConfiguration.AdharKitModuleRegistry registry =
                    context.getBean(AdharKitAutoConfiguration.AdharKitModuleRegistry.class);

            assertThat(moduleAccess.isEnabled("metrics")).isFalse();
            assertThat(registry.isModuleEnabled("metrics")).isFalse();
            assertThat(registry.getDisabledModules())
                    .extracting(AdharKitAutoConfiguration.AdharKitModuleRegistry.ModuleInfo::id)
                    .contains("metrics");
        });
    }

    @Test
    void masterSwitchDisabled_backsOffCompletely() {
        runner.withPropertyValues("adhar.kit.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AdharModuleAccess.class);
            assertThat(context).doesNotHaveBean(AdharKitAutoConfiguration.AdharKitModuleRegistry.class);
        });
    }

    @Test
    void actuatorEndpoint_registeredWhenActuatorOnClasspath() {
        runner.run(context -> assertThat(context).hasSingleBean(AdharKitEndpoint.class));
    }

    @Test
    void actuatorEndpoint_absentWhenActuatorOffClasspath() {
        runner.withClassLoader(new FilteredClassLoader(Endpoint.class))
                .run(context -> assertThat(context).doesNotHaveBean(AdharKitEndpoint.class));
    }

    @Test
    void gracefulShutdown_registeredByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(AdharGracefulShutdown.class));
    }

    @Test
    void gracefulShutdown_collectsShutdownHookBeans_andRunsOnContextClose() {
        List<String> events = new ArrayList<>();
        runner.withBean("recordingHook", AdharShutdownHook.class, () -> new AdharShutdownHook() {
            @Override
            public String moduleName() {
                return "recording";
            }

            @Override
            public void stopAcceptingWork() {
                events.add("stop");
            }

            @Override
            public void drain() {
                events.add("drain");
            }

            @Override
            public void close() {
                events.add("close");
            }
        }).run(context -> {
            AdharGracefulShutdown shutdown = context.getBean(AdharGracefulShutdown.class);
            assertThat(shutdown.getHooks()).hasSize(1);
            // Closing the context stops the SmartLifecycle, driving all phases.
            context.close();
            assertThat(events).containsExactly("stop", "drain", "close");
        });
    }

    @Test
    void gracefulShutdown_backsOffWhenUserProvidesOwnBean() {
        AdharGracefulShutdown custom = new AdharGracefulShutdown(List.of(), List.of());
        runner.withBean(AdharGracefulShutdown.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(AdharGracefulShutdown.class);
                    assertThat(context.getBean(AdharGracefulShutdown.class)).isSameAs(custom);
                });
    }
}
