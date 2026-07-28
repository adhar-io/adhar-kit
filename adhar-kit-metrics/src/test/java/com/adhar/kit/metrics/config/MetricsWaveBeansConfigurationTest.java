package com.adhar.kit.metrics.config;

import com.adhar.kit.metrics.config.AdharMetricsAutoConfiguration.OpenTelemetryTraceConfiguration;
import com.adhar.kit.metrics.config.AdharMetricsAutoConfiguration.PrometheusExemplarConfiguration;
import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.trace.TraceContext;
import com.adhar.kit.metrics.util.CgroupMetricsPoller;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the newly-added trace-correlation and cgroup-polling beans (OpenTelemetry and the
 * Prometheus client are on the test classpath). The nested {@code @Configuration} classes are
 * exercised in isolation to avoid the outer auto-configuration's self-injecting
 * {@code configureMeterRegistry} method, which is not designed to run in a full context.
 */
class MetricsWaveBeansConfigurationTest {

    @Test
    void openTelemetryTraceContextBeanIsCreated() {
        new ApplicationContextRunner()
                .withUserConfiguration(OpenTelemetryTraceConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(TraceContext.class));
    }

    @Test
    void prometheusSpanContextBridgeIsCreatedWhenTraceContextPresent() {
        new ApplicationContextRunner()
                .withUserConfiguration(TraceContextConfig.class, PrometheusExemplarConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(io.prometheus.metrics.tracer.common.SpanContext.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cgroupMetricsPollerBeanMethodCreatesAndStartsPoller() {
        AdharMetricsProperties properties = new AdharMetricsProperties();
        properties.getKubernetes().getResourcePolling().setEnabled(true);
        AdharMetricsAutoConfiguration config =
                new AdharMetricsAutoConfiguration(properties, Mockito.mock(Environment.class));

        MeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<com.adhar.kit.metrics.util.KubernetesMetricsUtils> provider =
                Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable(Mockito.any())).thenAnswer(inv ->
                ((java.util.function.Supplier<?>) inv.getArgument(0)).get());

        CgroupMetricsPoller poller = config.cgroupMetricsPoller(registry, provider);
        try {
            assertThat(poller).isNotNull();
        } finally {
            poller.shutdown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TraceContextConfig {
        @Bean
        TraceContext traceContext() {
            return new TraceContext() {
                @Override
                public Optional<String> currentTraceId() {
                    return Optional.of("t");
                }

                @Override
                public Optional<String> currentSpanId() {
                    return Optional.of("s");
                }

                @Override
                public boolean isSampled() {
                    return true;
                }
            };
        }
    }
}
