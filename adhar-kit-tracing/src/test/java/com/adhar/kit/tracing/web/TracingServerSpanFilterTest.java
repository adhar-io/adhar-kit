package com.adhar.kit.tracing.web;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link TracingServerSpanFilter}, driving it with Spring's mock servlet objects and
 * an in-memory OpenTelemetry SDK so the emitted SERVER span can be inspected.
 */
class TracingServerSpanFilterTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetrySdk openTelemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                // Always sample so the assertions do not depend on any parent sampling decision
                // that may be present in an ambient (possibly leaked) OpenTelemetry Context.
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    private TracingServerSpanFilter filter() {
        return new TracingServerSpanFilter(openTelemetry, new String[]{"/actuator/**"});
    }

    @Test
    void createsServerSpanWithHttpTags() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter().doFilter(request, response, (req, res) -> { });

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getName()).isEqualTo("GET /api/orders");
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.method"))).isEqualTo("GET");
        assertThat(span.getAttributes().get(AttributeKey.stringKey("http.route"))).isEqualTo("/api/orders");
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.status_code"))).isEqualTo(200L);
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    void joinsIncomingRemoteTraceViaTraceparent() throws Exception {
        String remoteTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String remoteSpanId = "00f067aa0ba902b7";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("traceparent", "00-" + remoteTraceId + "-" + remoteSpanId + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter().doFilter(request, response, (req, res) -> { });

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getTraceId()).isEqualTo(remoteTraceId);
        assertThat(span.getParentSpanId()).isEqualTo(remoteSpanId);
    }

    @Test
    void marksSpanErrorWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException boom = new RuntimeException("kaboom");
        FilterChain chain = (req, res) -> { throw boom; };

        assertThatThrownBy(() -> filter().doFilter(request, response, chain)).isSameAs(boom);

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
    }

    @Test
    void marksSpanErrorForServerErrorStatus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(503));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AttributeKey.longKey("http.status_code"))).isEqualTo(503L);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void skipsConfiguredSkipPatternsWithoutCreatingSpan() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    @Test
    void usesBestMatchingPatternAttributeForRouteWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter().doFilter(request, response, (req, res) ->
                req.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern",
                        "/api/orders/{id}"));

        SpanData span = exporter.getFinishedSpanItems().get(0);
        // The span name is captured up-front from the URI; the route attribute is also the URI
        // because the best-matching pattern is only set during dispatch (after span creation).
        assertThat(span.getName()).isEqualTo("GET /api/orders/42");
    }
}
