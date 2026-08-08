package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.source.ConfigSource;
import com.adhar.kit.config.source.impl.DaprConfigSource;
import com.adhar.kit.config.source.impl.DaprSecretConfigSource;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Dapr configuration/secret source wiring in
 * {@link ConfigAutoConfiguration}: opt-in via {@code adhar.dapr.enabled},
 * per-source disable flags, and registration into the {@link ConfigManager}.
 */
class DaprConfigSourcesAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigAutoConfiguration.class));

    private DaprFacade mockFacade() {
        DaprFacade facade = mock(DaprFacade.class);
        when(facade.getConfiguration(anyString(), anyList()))
                .thenReturn(Map.of("app.name", "from-dapr"));
        when(facade.getBulkSecrets(anyString())).thenReturn(Map.of("db-password", "s3cret"));
        return facade;
    }

    @Test
    void registersBothSourcesWhenDaprEnabled() {
        runner.withPropertyValues("adhar.dapr.enabled=true")
                .withBean(DaprFacade.class, this::mockFacade)
                .run(context -> {
                    assertThat(context.getBeansOfType(ConfigSource.class).values())
                            .hasAtLeastOneElementOfType(DaprConfigSource.class)
                            .hasAtLeastOneElementOfType(DaprSecretConfigSource.class);
                    // And the manager actually resolves through them.
                    ConfigManager manager = context.getBean(ConfigManager.class);
                    assertThat(manager.getProperty("app.name")).isEqualTo("from-dapr");
                    assertThat(manager.getProperty("db-password")).isEqualTo("s3cret");
                });
    }

    @Test
    void staysOffWithoutDaprEnabledProperty() {
        runner.withBean(DaprFacade.class, this::mockFacade)
                .run(context -> assertThat(context.getBeansOfType(ConfigSource.class)).isEmpty());
    }

    @Test
    void sourcesCanBeIndividuallyDisabled() {
        runner.withPropertyValues("adhar.dapr.enabled=true",
                        "adhar.config.dapr.config-enabled=false",
                        "adhar.config.dapr.secrets-enabled=false")
                .withBean(DaprFacade.class, this::mockFacade)
                .run(context -> assertThat(context.getBeansOfType(ConfigSource.class)).isEmpty());
    }

    @Test
    void storeNamesAreConfigurable() {
        runner.withPropertyValues("adhar.dapr.enabled=true",
                        "adhar.config.dapr.config-store=my-config",
                        "adhar.config.dapr.secret-store=my-secrets",
                        "adhar.config.dapr.secrets-enabled=false")
                .withBean(DaprFacade.class, this::mockFacade)
                .run(context -> {
                    DaprFacade facade = context.getBean(DaprFacade.class);
                    context.getBean(ConfigManager.class);
                    org.mockito.Mockito.verify(facade)
                            .getConfiguration(org.mockito.ArgumentMatchers.eq("my-config"), anyList());
                });
    }
}
