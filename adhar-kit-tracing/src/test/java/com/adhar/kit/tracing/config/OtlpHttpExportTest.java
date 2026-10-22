package com.adhar.kit.tracing.config;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code use-grpc=false} the kit exports OTLP over HTTP/protobuf to {@code /v1/traces} —
 * what the platform's collector listens on. (It used to build a gRPC exporter regardless.)
 */
class OtlpHttpExportTest {

    private HttpServer collector;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startCollector() throws Exception {
        collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.getRequestBody().readAllBytes();
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
    void spansArePostedToTheTracesUrl() {
        String bareCollectorAddress = "http://127.0.0.1:" + collector.getAddress().getPort();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AdharTracingAutoConfiguration.class))
                .withPropertyValues(
                        "adhar.tracing.open-telemetry.endpoint=" + bareCollectorAddress,
                        "adhar.tracing.open-telemetry.use-grpc=false",
                        "adhar.tracing.open-telemetry.compression=none")
                .run(context -> {
                    OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
                    openTelemetry.getTracer("test").spanBuilder("GET /orders").startSpan().end();
                    ((OpenTelemetrySdk) openTelemetry).getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);
                });
        assertThat(received).containsExactly("POST /v1/traces application/x-protobuf");
    }

    @Test
    void aBareCollectorAddressGetsTheTracesPath() {
        assertThat(AdharTracingAutoConfiguration.httpTracesEndpoint("http://alloy:4318"))
                .isEqualTo("http://alloy:4318/v1/traces");
        assertThat(AdharTracingAutoConfiguration.httpTracesEndpoint("http://alloy:4318/"))
                .isEqualTo("http://alloy:4318/v1/traces");
        assertThat(AdharTracingAutoConfiguration.httpTracesEndpoint("http://alloy:4318/v1/traces"))
                .isEqualTo("http://alloy:4318/v1/traces");
    }
}
