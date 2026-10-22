package com.adhar.kit.tracing.instrumentation;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.reactive.ReactiveHttpClientAutoConfiguration;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientObservationAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientObservationAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A call made through a client built from Spring Boot's auto-configured builder produces a
 * CLIENT span that is a child of the caller's span, and the request carries that span's
 * W3C {@code traceparent} - so the called service continues the same trace.
 */
class HttpClientTracingTest {

    private HttpServer server;
    private final List<String> traceparents = new CopyOnWriteArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(TracingTestSupport.TRACING)
            .withConfiguration(AutoConfigurations.of(
                    HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class,
                    ReactiveHttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    RestClientObservationAutoConfiguration.class,
                    RestTemplateAutoConfiguration.class,
                    RestTemplateObservationAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    WebClientObservationAutoConfiguration.class))
            .withUserConfiguration(TracingTestSupport.InMemoryExport.class)
            .withPropertyValues(TracingTestSupport.PROPERTIES);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            traceparents.add(String.valueOf(exchange.getRequestHeaders().getFirst("traceparent")));
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/orders/42";
    }

    @Test
    void restClient() {
        assertCallIsTraced(context -> context.getBean(RestClient.Builder.class).build()
                .get().uri(url()).retrieve().body(String.class));
    }

    @Test
    void restTemplate() {
        assertCallIsTraced(context -> context.getBean(RestTemplateBuilder.class).build()
                .getForObject(url(), String.class));
    }

    @Test
    void webClient() {
        assertCallIsTraced(context -> context.getBean(WebClient.Builder.class).build()
                .get().uri(url()).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10)));
    }

    private void assertCallIsTraced(Consumer<ApplicationContext> call) {
        runner.run(context -> {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            Observation.createNotStarted("handle-order", registry).observe(() -> call.accept(context));

            InMemorySpanExporter exporter = context.getBean(InMemorySpanExporter.class);
            List<SpanData> clientSpans = TracingTestSupport.spansOfKind(exporter, SpanKind.CLIENT);
            assertThat(clientSpans).hasSize(1);
            SpanData client = clientSpans.get(0);
            SpanData parent = exporter.getFinishedSpanItems().stream()
                    .filter(span -> span.getName().equals("handle-order")).findFirst().orElseThrow();

            assertThat(client.getTraceId()).isEqualTo(parent.getTraceId());
            assertThat(client.getParentSpanId()).isEqualTo(parent.getSpanId());
            assertThat(client.getName()).startsWith("http get");
            assertThat(traceparents).hasSize(1);
            assertThat(traceparents.get(0)).startsWith(TracingTestSupport.traceparentOf(client));
        });
    }
}
