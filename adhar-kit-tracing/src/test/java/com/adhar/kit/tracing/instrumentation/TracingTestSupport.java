package com.adhar.kit.tracing.instrumentation;

import com.adhar.kit.tracing.config.AdharTracingAutoConfiguration;
import com.adhar.kit.tracing.config.OtelEnvironmentPostProcessor;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * What every outbound-instrumentation test shares: the auto-configurations a service has
 * (Boot's observation registry, the kit's tracer, Boot's tracing observation handlers) and
 * an in-memory exporter attached to the kit's SDK in place of the OTLP one.
 */
final class TracingTestSupport {

    static final AutoConfigurations TRACING = AutoConfigurations.of(
            ObservationAutoConfiguration.class,
            AdharTracingAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class,
            NoopTracerAutoConfiguration.class);

    /** No OTLP export to localhost:4317 from a unit test. */
    static final String[] PROPERTIES = {
            "adhar.tracing.open-telemetry.enabled=false",
            "adhar.tracing.metrics.enabled=false",
            "spring.application.name=outbound-test"};

    private TracingTestSupport() {
    }

    /**
     * The kit's environment post-processor, as {@code SpringApplication} runs it in a service
     * (an {@code ApplicationContextRunner} runs none): its lowest-precedence defaults are what
     * switch the Kafka/RabbitMQ observations on and shape the JDBC spans.
     */
    static void applyKitEnvironmentDefaults(ConfigurableApplicationContext context) {
        new OtelEnvironmentPostProcessor().postProcessEnvironment(context.getEnvironment(), new SpringApplication());
    }

    @Configuration(proxyBeanMethods = false)
    static class InMemoryExport {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SpanProcessor inMemorySpanProcessor(InMemorySpanExporter exporter) {
            return SimpleSpanProcessor.create(exporter);
        }
    }

    static List<SpanData> spansOfKind(InMemorySpanExporter exporter, SpanKind kind) {
        return exporter.getFinishedSpanItems().stream().filter(span -> span.getKind() == kind).toList();
    }

    /** The W3C traceparent of a span, without the trace-flags (sampled, random-id ...). */
    static String traceparentOf(SpanData span) {
        return "00-" + span.getTraceId() + "-" + span.getSpanId() + "-";
    }
}
