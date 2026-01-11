package com.adhar.adharkit.messaging.config;

import com.adhar.kit.messaging.cloudevents.CloudEventAdapter;
import com.adhar.kit.messaging.config.CloudEventAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link CloudEventAutoConfiguration} class.
 */
class CloudEventAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudEventAutoConfiguration.class));

    @Test
    void testCloudEventAdapterBeanIsCreatedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CloudEventAdapter.class);
        });
    }

    @Test
    void testCloudEventAdapterBeanIsCreatedWhenEnabled() {
        contextRunner
                .withPropertyValues("adhar.messaging.cloudevents.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CloudEventAdapter.class);
                });
    }

    @Test
    void testCloudEventAdapterBeanIsNotCreatedWhenDisabled() {
        contextRunner
                .withPropertyValues("adhar.messaging.cloudevents.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CloudEventAdapter.class);
                });
    }

    @Test
    void testCloudEventAdapterBeanIsNotCreatedWhenUserProvidesCustomBean() {
        contextRunner
                .withUserConfiguration(CustomCloudEventAdapterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CloudEventAdapter.class);
                    assertThat(context.getBean(CloudEventAdapter.class))
                            .isInstanceOf(CustomCloudEventAdapter.class);
                });
    }

    /**
     * Custom configuration that provides a custom CloudEventAdapter bean.
     */
    @Configuration
    static class CustomCloudEventAdapterConfiguration {
        @Bean
        CloudEventAdapter cloudEventAdapter() {
            return new CustomCloudEventAdapter();
        }
    }

    /**
     * Custom CloudEventAdapter implementation for testing.
     */
    static class CustomCloudEventAdapter extends CloudEventAdapter {
        // Custom implementation for testing
    }
}