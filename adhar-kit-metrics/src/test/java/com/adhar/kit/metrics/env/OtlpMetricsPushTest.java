package com.adhar.kit.metrics.env;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1.5's kit half: with only the OTEL_* variables a run pod gets, the mapped properties are Spring
 * Boot's own OTLP export properties, and a registry that lives far less than one export step still
 * delivers its meters, because it pushes when it closes (Boot closes it with the context). The
 * collector is a local HTTP endpoint standing in for Alloy's {@code :4318}.
 *
 * <p>The registry is built from the mapped values directly rather than through Boot's
 * auto-configuration: this module imports the Quarkus BOM first, which pins Micrometer 1.16 on its
 * own test classpath, while Boot 4.1's OTLP auto-configuration needs 1.17. Applications resolve
 * Boot's version.</p>
 */
class OtlpMetricsPushTest {

    private static final String EXPORT = OtlpMetricsEnvironmentPostProcessor.EXPORT;

    private HttpServer collector;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startCollector() throws Exception {
        collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/v1/metrics", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + exchange.getRequestHeaders().getFirst("X-Scope-OrgID") + " "
                    + new String(body, StandardCharsets.ISO_8859_1));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        collector.start();
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void everyMappedPropertyIsOneSpringBootDefines() throws Exception {
        Map<String, Object> mapped = new OtlpMetricsEnvironmentPostProcessor(Map.of(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318", "OTEL_EXPORTER_OTLP_HEADERS", "a=b",
                "OTEL_METRIC_EXPORT_INTERVAL", "1000")::get).map();
        StringBuilder all = new StringBuilder();
        var resources = OtlpMetricsPushTest.class.getClassLoader().getResources("META-INF/spring-configuration-metadata.json");
        while (resources.hasMoreElements()) {
            try (InputStream in = resources.nextElement().openStream()) {
                all.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        String metadata = all.toString();

        assertThat(mapped.keySet()).isNotEmpty().allSatisfy(key ->
                assertThat(metadata).contains("\"name\": \"" + key.replaceAll("\\[.*]$", "") + "\""));
    }

    @Test
    void aRegistryShorterThanOneStepPushesWhenItCloses() {
        Map<String, Object> mapped = new OtlpMetricsEnvironmentPostProcessor(Map.of(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:" + collector.getAddress().getPort(),
                "OTEL_EXPORTER_OTLP_HEADERS", "X-Scope-OrgID=team-a")::get).map();
        assertThat(mapped).containsEntry(EXPORT + "enabled", "true");
        OtlpConfig config = key -> switch (key) {
            case "otlp.url" -> (String) mapped.get(EXPORT + "url");
            case "otlp.headers" -> "X-Scope-OrgID=" + mapped.get(EXPORT + "headers[X-Scope-OrgID]");
            default -> null;
        };

        OtlpMeterRegistry registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
        registry.counter("adhar.batch.test.items").increment(42);
        assertThat(received).as("nothing is pushed before the one-minute step").isEmpty();
        registry.close();

        assertThat(received).isNotEmpty();
        assertThat(received.getFirst()).startsWith("application/x-protobuf team-a").contains("adhar.batch.test.items");
    }
}
