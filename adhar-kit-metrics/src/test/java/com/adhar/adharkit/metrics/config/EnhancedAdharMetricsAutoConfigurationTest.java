package com.adhar.adharkit.metrics.config;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhanced tests for {@link AdharMetricsAutoConfiguration} with additional scenarios.
 */
class EnhancedAdharMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharMetricsAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void testCompleteConfigurationScenario() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.prometheus.enabled=true",
                        "adhar.metrics.open-telemetry.enabled=false",
                        "adhar.metrics.jvm.enabled=true",
                        "adhar.metrics.system.enabled=true",
                        "adhar.metrics.kubernetes.enabled=false",
                        "adhar.metrics.common-tags.service=test-service",
                        "adhar.metrics.common-tags.version=1.0.0",
                        "spring.application.name=enhanced-metrics-test"
                )
                .run(context -> {
                    // Verify all expected beans are present
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AdharMetricsProperties.class);

                    // Verify properties are loaded correctly
                    AdharMetricsProperties properties = context.getBean(AdharMetricsProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getPrometheus().isEnabled()).isTrue();
                    assertThat(properties.getOpenTelemetry().isEnabled()).isFalse();
                    assertThat(properties.getCommonTags()).containsEntry("service", "test-service");
                    assertThat(properties.getCommonTags()).containsEntry("version", "1.0.0");

                    // Verify meter registry is configured
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry).isNotNull();
                });
    }

    @Test
    void testMinimalConfiguration() {
        contextRunner
                .withPropertyValues("adhar.metrics.enabled=true")
                .run(context -> {
                    // Even with minimal config, basic functionality should work
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AdharMetricsProperties.class);

                    AdharMetricsProperties properties = context.getBean(AdharMetricsProperties.class);
                    // Verify defaults
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getPrometheus().isEnabled()).isTrue();
                    assertThat(properties.getJvm().isEnabled()).isTrue();
                    assertThat(properties.getSystem().isEnabled()).isTrue();
                });
    }

    @Test
    void testOpenTelemetryConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.open-telemetry.enabled=true",
                        "adhar.metrics.open-telemetry.endpoint=http://otel-collector:4318/v1/metrics",
                        "adhar.metrics.open-telemetry.timeout=PT15S"
                )
                .run(context -> {
                    AdharMetricsProperties properties = context.getBean(AdharMetricsProperties.class);
                    assertThat(properties.getOpenTelemetry().isEnabled()).isTrue();
                    assertThat(properties.getOpenTelemetry().getEndpoint())
                            .isEqualTo("http://otel-collector:4318/v1/metrics");
                    assertThat(properties.getOpenTelemetry().getTimeout()).isEqualTo("PT15S");
                });
    }

    @Test
    void testWebMetricsConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.web.enabled=true",
                        "adhar.metrics.web.record-request-size=false",
                        "adhar.metrics.web.max-uri-tags=50"
                )
                .run(context -> {
                    AdharMetricsProperties properties = context.getBean(AdharMetricsProperties.class);
                    assertThat(properties.getWeb().isEnabled()).isTrue();
                    assertThat(properties.getWeb().isRecordRequestSize()).isFalse();
                    assertThat(properties.getWeb().getMaxUriTags()).isEqualTo(50);
                });
    }

    @Test
    void testApplicationMetricsConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.application.enabled=true",
                        "adhar.metrics.application.method-timing=false",
                        "adhar.metrics.application.cache=true"
                )
                .run(context -> {
                    AdharMetricsProperties properties = context.getBean(AdharMetricsProperties.class);
                    assertThat(properties.getApplication().isEnabled()).isTrue();
                    assertThat(properties.getApplication().isMethodTiming()).isFalse();
                    assertThat(properties.getApplication().isCache()).isTrue();
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
