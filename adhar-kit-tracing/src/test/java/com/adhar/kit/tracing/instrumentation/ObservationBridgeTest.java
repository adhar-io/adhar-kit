package com.adhar.kit.tracing.instrumentation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The kit's tracer is what Boot's observation handlers record into: an {@link Observation}
 * - the instrument every Spring client uses - becomes an exported span, and a sender
 * observation writes the W3C {@code traceparent} of its span into the carrier.
 */
class ObservationBridgeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(TracingTestSupport.TRACING)
            .withUserConfiguration(TracingTestSupport.InMemoryExport.class)
            .withPropertyValues(TracingTestSupport.PROPERTIES);

    @Test
    void theKitsTracerAndPropagatorBackBootsObservationHandlers() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Tracer.class);
            assertThat(context.getBean(Tracer.class)).isInstanceOf(OtelTracer.class);
            assertThat(context).hasSingleBean(Propagator.class);
            assertThat(context.getBean(Propagator.class).fields()).contains("traceparent");
        });
    }

    @Test
    void anObservationIsExportedAsASpan() {
        runner.run(context -> {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            Observation.createNotStarted("orders.reconcile", registry).observe(() -> { });

            List<SpanData> spans = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
            assertThat(spans).extracting(SpanData::getName).contains("orders.reconcile");
        });
    }

    @Test
    void aSenderObservationInjectsTraceparentIntoTheCarrier() {
        runner.run(context -> {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            Map<String, String> carrier = new HashMap<>();
            SenderContext<Map<String, String>> sender = new SenderContext<>(Map::put);
            sender.setCarrier(carrier);
            sender.setRemoteServiceName("billing");
            Observation.createNotStarted("send", () -> sender, registry).observe(() -> { });

            SpanData producer = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems().get(0);
            assertThat(producer.getKind()).isEqualTo(SpanKind.PRODUCER);
            assertThat(carrier.get("traceparent")).startsWith(TracingTestSupport.traceparentOf(producer));
        });
    }
}
