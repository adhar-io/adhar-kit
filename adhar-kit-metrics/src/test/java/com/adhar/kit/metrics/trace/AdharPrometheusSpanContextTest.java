package com.adhar.kit.metrics.trace;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link AdharPrometheusSpanContext}, the Prometheus exemplar bridge.
 */
class AdharPrometheusSpanContextTest {

    private static TraceContext fixedContext(String traceId, String spanId, boolean sampled) {
        return new TraceContext() {
            @Override
            public Optional<String> currentTraceId() {
                return Optional.ofNullable(traceId);
            }

            @Override
            public Optional<String> currentSpanId() {
                return Optional.ofNullable(spanId);
            }

            @Override
            public boolean isSampled() {
                return sampled;
            }
        };
    }

    @Test
    void delegatesTraceAndSpanIds() {
        AdharPrometheusSpanContext ctx = new AdharPrometheusSpanContext(
                fixedContext("trace-abc", "span-123", true));

        assertThat(ctx.getCurrentTraceId()).isEqualTo("trace-abc");
        assertThat(ctx.getCurrentSpanId()).isEqualTo("span-123");
        assertThat(ctx.isCurrentSpanSampled()).isTrue();
    }

    @Test
    void returnsNullWhenNoActiveSpan() {
        AdharPrometheusSpanContext ctx = new AdharPrometheusSpanContext(
                fixedContext(null, null, false));

        assertThat(ctx.getCurrentTraceId()).isNull();
        assertThat(ctx.getCurrentSpanId()).isNull();
        assertThat(ctx.isCurrentSpanSampled()).isFalse();
    }

    @Test
    void markCurrentSpanAsExemplarIsNoOp() {
        AdharPrometheusSpanContext ctx = new AdharPrometheusSpanContext(
                fixedContext("t", "s", true));

        assertDoesNotThrow(ctx::markCurrentSpanAsExemplar);
    }
}
