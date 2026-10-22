package com.adhar.kit.tracing.instrumentation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A record sent through Boot's {@link KafkaTemplate} carries the producer span's
 * {@code traceparent} header, and the {@code @KafkaListener} that receives it runs inside a
 * CONSUMER span of the SAME trace, child of the producer span - with nothing configured by
 * the application. Spring Boot leaves both observations off; the kit's defaults turn them on.
 * Runs against an in-JVM KRaft broker (no Docker).
 */
class KafkaTracingTest {

    private static EmbeddedKafkaKraftBroker broker;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, "orders");
        broker.afterPropertiesSet();
    }

    @AfterAll
    static void stopBroker() {
        broker.destroy();
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(TracingTestSupport::applyKitEnvironmentDefaults)
                .withConfiguration(TracingTestSupport.TRACING)
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withUserConfiguration(TracingTestSupport.InMemoryExport.class, OrdersListener.class)
                .withPropertyValues(TracingTestSupport.PROPERTIES)
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "spring.kafka.consumer.group-id=orders-" + System.nanoTime());
    }

    @Test
    void theConsumerContinuesTheProducersTrace() {
        runner().run(context -> {
            @SuppressWarnings("unchecked")
            KafkaTemplate<Object, Object> template = context.getBean(KafkaTemplate.class);
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            OrdersListener listener = context.getBean(OrdersListener.class);

            Observation.createNotStarted("place-order", registry)
                    .observe(() -> template.send("orders", "order-1", "placed").join());

            Received received = listener.received.poll(60, TimeUnit.SECONDS);
            assertThat(received).as("record received").isNotNull();

            InMemorySpanExporter exporter = context.getBean(InMemorySpanExporter.class);
            SpanData producer = awaitSpan(exporter, SpanKind.PRODUCER);
            SpanData consumer = awaitSpan(exporter, SpanKind.CONSUMER);
            SpanData placeOrder = exporter.getFinishedSpanItems().stream()
                    .filter(span -> span.getName().equals("place-order")).findFirst().orElseThrow();

            assertThat(producer.getParentSpanId()).isEqualTo(placeOrder.getSpanId());
            assertThat(received.traceparent()).startsWith(TracingTestSupport.traceparentOf(producer));
            assertThat(consumer.getTraceId()).isEqualTo(placeOrder.getTraceId());
            assertThat(consumer.getParentSpanId()).isEqualTo(producer.getSpanId());
            // The listener body ran inside the consumer span.
            assertThat(received.currentSpanId()).isEqualTo(consumer.getSpanId());
        });
    }

    private static SpanData awaitSpan(InMemorySpanExporter exporter, SpanKind kind) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            List<SpanData> spans = TracingTestSupport.spansOfKind(exporter, kind);
            if (!spans.isEmpty()) {
                return spans.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no " + kind + " span exported; spans: " + exporter.getFinishedSpanItems());
    }

    record Received(String traceparent, String currentSpanId) {
    }

    @Configuration(proxyBeanMethods = false)
    static class OrdersListener {

        final BlockingQueue<Received> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "orders")
        void onOrder(ConsumerRecord<String, String> record) {
            Header header = record.headers().lastHeader("traceparent");
            received.add(new Received(
                    header == null ? null : new String(header.value(), StandardCharsets.UTF_8),
                    Span.current().getSpanContext().getSpanId()));
        }
    }
}
