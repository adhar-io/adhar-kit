package com.adhar.kit.tracing.metrics;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SpanMetricsProcessor}, using a {@link SimpleMeterRegistry} and mock spans.
 */
class SpanMetricsProcessorTest {

    private ReadableSpan span(String name, SpanKind kind, long latencyNanos, boolean error) {
        ReadableSpan span = mock(ReadableSpan.class);
        when(span.getName()).thenReturn(name);
        when(span.getKind()).thenReturn(kind);
        when(span.getLatencyNanos()).thenReturn(latencyNanos);
        SpanData data = mock(SpanData.class);
        when(data.getStatus()).thenReturn(error ? StatusData.error() : StatusData.ok());
        when(span.toSpanData()).thenReturn(data);
        return span;
    }

    private AdharTracingProperties.MetricsBridgeProperties props() {
        return new AdharTracingProperties.MetricsBridgeProperties();
    }

    @Test
    void recordsDurationTimerTaggedByNameKindAndError() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpanMetricsProcessor processor = new SpanMetricsProcessor(registry, props());

        processor.onEnd(span("GET /orders", SpanKind.SERVER, TimeUnit.MILLISECONDS.toNanos(25), false));

        Timer timer = registry.find("span.duration")
                .tag("span.name", "GET /orders")
                .tag("span.kind", "SERVER")
                .tag("error", "false")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(24.0);
    }

    @Test
    void incrementsErrorCounterAndRatesUpTimerCountForRepeatedSpans() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SpanMetricsProcessor processor = new SpanMetricsProcessor(registry, props());

        processor.onEnd(span("GET /boom", SpanKind.SERVER, 1000, true));
        processor.onEnd(span("GET /boom", SpanKind.SERVER, 2000, true));

        assertThat(registry.find("span.errors")
                .tag("span.name", "GET /boom")
                .tag("span.kind", "SERVER")
                .counter().count()).isEqualTo(2.0);

        // Rate is captured via the timer's count.
        assertThat(registry.find("span.duration")
                .tag("span.name", "GET /boom")
                .tag("error", "true")
                .timer().count()).isEqualTo(2);
    }

    @Test
    void honorsCustomPrefixAndOmitsKindWhenDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdharTracingProperties.MetricsBridgeProperties p = props();
        p.setMeterPrefix("trace.red");
        p.setIncludeSpanKind(false);
        SpanMetricsProcessor processor = new SpanMetricsProcessor(registry, p);

        processor.onEnd(span("job", SpanKind.INTERNAL, 5000, false));

        Timer timer = registry.find("trace.red.duration").tag("span.name", "job").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("span.kind")).isNull();
    }

    @Test
    void doesNotRecordErrorsWhenRecordErrorsDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdharTracingProperties.MetricsBridgeProperties p = props();
        p.setRecordErrors(false);
        SpanMetricsProcessor processor = new SpanMetricsProcessor(registry, p);

        processor.onEnd(span("GET /x", SpanKind.SERVER, 1000, true));

        assertThat(registry.find("span.errors").counter()).isNull();
        // No error tag on the timer either.
        Timer timer = registry.find("span.duration").tag("span.name", "GET /x").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("error")).isNull();
    }

    @Test
    void processorContractFlags() {
        SpanMetricsProcessor processor = new SpanMetricsProcessor(new SimpleMeterRegistry(), props());
        assertThat(processor.isEndRequired()).isTrue();
        assertThat(processor.isStartRequired()).isFalse();
        // onStart is a no-op and must not throw.
        processor.onStart(io.opentelemetry.context.Context.root(), null);
    }
}
