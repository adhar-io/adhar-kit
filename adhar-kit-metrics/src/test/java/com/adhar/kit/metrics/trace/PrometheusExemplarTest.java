package com.adhar.kit.metrics.trace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The kit's {@link AdharPrometheusSpanContext} is what Spring Boot hands to the
 * {@code PrometheusMeterRegistry}; with it, histogram buckets carry the current trace id as an
 * exemplar in the OpenMetrics scrape.
 */
class PrometheusExemplarTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void histogramBucketsCarryTheTraceIdAsAnExemplar() {
        TraceContext traceContext = new TraceContext() {
            @Override
            public Optional<String> currentTraceId() {
                return Optional.of(TRACE_ID);
            }

            @Override
            public Optional<String> currentSpanId() {
                return Optional.of("00f067aa0ba902b7");
            }

            @Override
            public boolean isSampled() {
                return true;
            }
        };
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT,
                new PrometheusRegistry(), Clock.SYSTEM, new AdharPrometheusSpanContext(traceContext));

        Timer.builder("http.server.requests").publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(120));

        String openMetrics = registry.scrape("application/openmetrics-text; version=1.0.0; charset=utf-8");
        assertThat(openMetrics).contains("http_server_requests_seconds_bucket").contains("trace_id=\"" + TRACE_ID + "\"");
    }
}
