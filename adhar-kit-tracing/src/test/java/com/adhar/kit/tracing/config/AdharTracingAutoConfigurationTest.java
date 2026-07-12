package com.adhar.kit.tracing.config;

import com.adhar.kit.tracing.aspect.TracingAspect;
import com.adhar.kit.tracing.properties.AdharTracingProperties;
import com.adhar.kit.tracing.util.AdharTracing;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharTracingAutoConfiguration}.
 */
class AdharTracingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharTracingAutoConfiguration.class));

    @Test
    void testAutoConfigurationEnabled() {
        contextRunner
                .withPropertyValues("adhar.tracing.enabled=true")
                .run(context -> {
                    // Verify that the auto-configuration is loaded
                    assertThat(context).hasSingleBean(AdharTracingAutoConfiguration.class);

                    // Verify that the properties bean is created
                    assertThat(context).hasSingleBean(AdharTracingProperties.class);

                    // Verify that OpenTelemetry SDK is created
                    assertThat(context).hasSingleBean(OpenTelemetry.class);

                    // Verify that the tracer is created
                    assertThat(context).hasSingleBean(Tracer.class);

                    // Verify that the tracing aspect is created
                    assertThat(context).hasSingleBean(TracingAspect.class);

                    // Verify that consolidated tracing utility is created
                    assertThat(context).hasSingleBean(AdharTracing.class);
                });
    }

    @Test
    void testAutoConfigurationDisabled() {
        contextRunner
                .withPropertyValues("adhar.tracing.enabled=false")
                .run(context -> {
                    // Verify that the auto-configuration is not loaded
                    assertThat(context).doesNotHaveBean(AdharTracingAutoConfiguration.class);

                    // Verify that the beans are not created
                    assertThat(context).doesNotHaveBean(OpenTelemetry.class);
                    assertThat(context).doesNotHaveBean(Tracer.class);
                    assertThat(context).doesNotHaveBean(TracingAspect.class);
                    assertThat(context).doesNotHaveBean(AdharTracing.class);
                });
    }

    @Test
    void testOpenTelemetryConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.open-telemetry.enabled=true",
                        "adhar.tracing.open-telemetry.endpoint=http://test-otel:4317",
                        "adhar.tracing.sampling.probability=0.5"
                )
                .run(context -> {
                    // Verify OpenTelemetry configuration
                    assertThat(context).hasSingleBean(OpenTelemetry.class);

                    // Verify properties are loaded correctly
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getOpenTelemetry().isEnabled()).isTrue();
                    assertThat(properties.getOpenTelemetry().getEndpoint()).isEqualTo("http://test-otel:4317");
                    assertThat(properties.getSampling().getProbability()).isEqualTo(0.5);
                });
    }

    @Test
    void testZipkinConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.zipkin.enabled=true",
                        "adhar.tracing.zipkin.base-url=http://test-zipkin:9411"
                )
                .run(context -> {
                    // Verify configuration is loaded
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getZipkin().isEnabled()).isTrue();
                    assertThat(properties.getZipkin().getBaseUrl()).isEqualTo("http://test-zipkin:9411");
                });
    }

    @Test
    void testJaegerConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.jaeger.enabled=true",
                        "adhar.tracing.jaeger.grpc-endpoint=http://test-jaeger:14250"
                )
                .run(context -> {
                    // Verify configuration is loaded
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getJaeger().isEnabled()).isTrue();
                    assertThat(properties.getJaeger().getGrpcEndpoint()).isEqualTo("http://test-jaeger:14250");
                });
    }

    @Test
    void testSamplingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.sampling.probability=1.0",
                        "adhar.tracing.sampling.rate-limit=500"
                )
                .run(context -> {
                    // Verify sampling configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getSampling().getProbability()).isEqualTo(1.0);
                    assertThat(properties.getSampling().getRateLimit()).isEqualTo(500);
                });
    }

    @Test
    void testPropagationConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.propagation.type=b3",
                        "adhar.tracing.propagation.b3-single-header=true"
                )
                .run(context -> {
                    // Verify propagation configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getPropagation().getType()).isEqualTo("b3");
                    assertThat(properties.getPropagation().isB3SingleHeader()).isTrue();
                });
    }

    @Test
    void testResourceConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.resource.service-name=test-service",
                        "adhar.tracing.resource.service-version=1.2.3",
                        "adhar.tracing.resource.service-namespace=production",
                        "spring.application.name=tracing-test-app"
                )
                .run(context -> {
                    // Verify resource configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getResource().getServiceName()).isEqualTo("test-service");
                    assertThat(properties.getResource().getServiceVersion()).isEqualTo("1.2.3");
                    assertThat(properties.getResource().getServiceNamespace()).isEqualTo("production");
                });
    }

    @Test
    void testWebTracingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.web.enabled=true",
                        "adhar.tracing.web.trace-http-clients=false",
                        "adhar.tracing.web.include-request-headers=true"
                )
                .run(context -> {
                    // Verify controller tracing configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getWeb().isEnabled()).isTrue();
                    assertThat(properties.getWeb().isTraceHttpClients()).isFalse();
                    assertThat(properties.getWeb().isIncludeRequestHeaders()).isTrue();
                });
    }

    @Test
    void testDatabaseTracingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.database.enabled=true",
                        "adhar.tracing.database.include-sql-statements=false",
                        "adhar.tracing.database.max-sql-length=500"
                )
                .run(context -> {
                    // Verify database tracing configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getDatabase().isEnabled()).isTrue();
                    assertThat(properties.getDatabase().isIncludeSqlStatements()).isFalse();
                    assertThat(properties.getDatabase().getMaxSqlLength()).isEqualTo(500);
                });
    }

    @Test
    void testMessagingTracingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.messaging.enabled=true",
                        "adhar.tracing.messaging.trace-kafka=false",
                        "adhar.tracing.messaging.include-payloads=true"
                )
                .run(context -> {
                    // Verify messaging tracing configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getMessaging().isEnabled()).isTrue();
                    assertThat(properties.getMessaging().isTraceKafka()).isFalse();
                    assertThat(properties.getMessaging().isIncludePayloads()).isTrue();
                });
    }

    @Test
    void testBaggageConfiguration() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.baggage.enabled=true",
                        "adhar.tracing.baggage.max-entries=50",
                        "adhar.tracing.baggage.max-value-length=500"
                )
                .run(context -> {
                    // Verify baggage configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getBaggage().isEnabled()).isTrue();
                    assertThat(properties.getBaggage().getMaxEntries()).isEqualTo(50);
                    assertThat(properties.getBaggage().getMaxValueLength()).isEqualTo(500);
                });
    }

    @Test
    void testWithoutTracerClass() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Tracer.class))
                .withPropertyValues("adhar.tracing.enabled=true")
                .run(context -> {
                    // Verify that auto-configuration doesn't load without Tracer class
                    assertThat(context).doesNotHaveBean(AdharTracingAutoConfiguration.class);
                });
    }

    @Test
    void testMinimalConfiguration() {
        contextRunner
                .withPropertyValues("adhar.tracing.enabled=true")
                .run(context -> {
                    // Even with minimal config, basic functionality should work
                    assertThat(context).hasSingleBean(AdharTracingAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AdharTracingProperties.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(Tracer.class);

                    // Verify defaults
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getOpenTelemetry().isEnabled()).isTrue();
                    assertThat(properties.getZipkin().isEnabled()).isFalse();
                    assertThat(properties.getJaeger().isEnabled()).isFalse();
                    assertThat(properties.getSampling().getProbability()).isEqualTo(0.1);
                });
    }

    @Test
    void testAllExportersEnabled() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.open-telemetry.enabled=true",
                        "adhar.tracing.zipkin.enabled=true",
                        "adhar.tracing.jaeger.enabled=true"
                )
                .run(context -> {
                    // Verify all exporters can be enabled simultaneously
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getOpenTelemetry().isEnabled()).isTrue();
                    assertThat(properties.getZipkin().isEnabled()).isTrue();
                    assertThat(properties.getJaeger().isEnabled()).isTrue();

                    // Verify beans are created
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(Tracer.class);
                });
    }

    @Test
    void testCustomTags() {
        contextRunner
                .withPropertyValues(
                        "adhar.tracing.enabled=true",
                        "adhar.tracing.tags.environment=test",
                        "adhar.tracing.tags.version=1.0.0",
                        "adhar.tracing.tags.team=platform"
                )
                .run(context -> {
                    // Verify custom tags configuration
                    AdharTracingProperties properties = context.getBean(AdharTracingProperties.class);
                    assertThat(properties.getTags()).hasSize(3);
                    assertThat(properties.getTags()).containsEntry("environment", "test");
                    assertThat(properties.getTags()).containsEntry("version", "1.0.0");
                    assertThat(properties.getTags()).containsEntry("team", "platform");
                });
    }
}
