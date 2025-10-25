package com.adhar.adharkit.metrics.config;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import com.adhar.adharkit.metrics.util.MetricsUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharMetricsAutoConfiguration}.
 */
class AdharMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharMetricsAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void testAutoConfigurationEnabled() {
        contextRunner
                .withPropertyValues("adhar.metrics.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the properties bean is created
                    assertThat(context).hasSingleBean(AdharMetricsProperties.class);
                    
                    // Verify that the metrics utils bean is created
                    assertThat(context).hasSingleBean(MetricsUtils.class);
                    
                    // Verify that the meter registry customizer is created
                    assertThat(context).hasSingleBean(MeterRegistryCustomizer.class);
                });
    }

    @Test
    void testAutoConfigurationDisabled() {
        contextRunner
                .withPropertyValues("adhar.metrics.enabled=false")
                .run(context -> {
                    // Verify that the auto-configuration is not loaded
                    assertThat(context).doesNotHaveBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the properties bean is still created (by Spring Boot)
                    assertThat(context).hasSingleBean(AdharMetricsProperties.class);
                    
                    // Verify that the metrics utils bean is not created
                    assertThat(context).doesNotHaveBean(MetricsUtils.class);
                    
                    // Verify that the meter registry customizer is not created
                    assertThat(context).doesNotHaveBean(MeterRegistryCustomizer.class);
                });
    }

    @Test
    void testPrometheusConfigEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.prometheus.enabled=true")
                .run(context -> {
                    // Verify that the Prometheus config is created
                    assertThat(context).hasSingleBean(MeterRegistryCustomizer.class);
                    
                    // Verify that the Prometheus meter registry is available
                    assertThat(context).hasSingleBean(PrometheusMeterRegistry.class);
                });
    }

    @Test
    void testPrometheusConfigDisabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.prometheus.enabled=false")
                .run(context -> {
                    // Verify that the Prometheus config is not created
                    assertThat(context).doesNotHaveBean(MeterRegistryCustomizer.class);
                });
    }

    @Test
    void testJvmMetricsEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.jvm.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the JVM metrics are registered
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.find("jvm.memory.used").meters()).isNotEmpty();
                    assertThat(registry.find("jvm.threads.live").meters()).isNotEmpty();
                    assertThat(registry.find("jvm.classes.loaded").meters()).isNotEmpty();
                });
    }

    @Test
    void testJvmMetricsDisabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.jvm.enabled=false")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the JVM metrics are not registered
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.find("jvm.memory.used").meters()).isEmpty();
                    assertThat(registry.find("jvm.threads.live").meters()).isEmpty();
                    assertThat(registry.find("jvm.classes.loaded").meters()).isEmpty();
                });
    }

    @Test
    void testSystemMetricsEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.system.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the system metrics are registered
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.find("system.cpu.count").meters()).isNotEmpty();
                    assertThat(registry.find("process.uptime").meters()).isNotEmpty();
                });
    }

    @Test
    void testSystemMetricsDisabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.system.enabled=false")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the system metrics are not registered
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.find("system.cpu.count").meters()).isEmpty();
                    assertThat(registry.find("process.uptime").meters()).isEmpty();
                });
    }

    @Test
    void testOpenTelemetryConfigurationNotLoadedWhenClassNotPresent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("io.opentelemetry.api"))
                .withPropertyValues("adhar.metrics.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    
                    // Verify that the OpenTelemetry configuration is not loaded
                    assertThat(context).doesNotHaveBean(AdharMetricsAutoConfiguration.OpenTelemetryConfiguration.class);
                });
    }

    @Test
    void testCommonTagsConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.common-tags.application=test-app",
                        "adhar.metrics.common-tags.version=1.0.0",
                        "spring.application.name=metrics-test")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);

                    // Verify that common tags are configured
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    assertThat(registry.config().commonTags()).isNotEmpty();

                    // Check that application tag is set from spring.application.name
                    assertThat(registry.config().commonTags().stream()
                            .anyMatch(tag -> "application".equals(tag.getKey()) && "metrics-test".equals(tag.getValue())))
                            .isTrue();
                });
    }

    @Test
    void testWithoutPrometheusDependency() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(PrometheusMeterRegistry.class))
                .withPropertyValues("adhar.metrics.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration still works without Prometheus
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(PrometheusMeterRegistry.class);
                });
    }

    @Test
    void testKubernetesMetricsEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.kubernetes.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);

                    // Verify that the Kubernetes utils bean is created
                    assertThat(context).hasBean("kubernetesMetricsUtils");
                });
    }

    @Test
    void testKubernetesMetricsDisabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.metrics.enabled=true",
                        "adhar.metrics.kubernetes.enabled=false")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharMetricsAutoConfiguration.class);

                    // Verify that the Kubernetes utils bean is not created
                    assertThat(context).doesNotHaveBean("kubernetesMetricsUtils");
                });
    }

    /**
     * Test configuration providing a meter registry.
     */
    @Configuration
    static class TestConfiguration {
        
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}