package com.adhar.kit.grpc.interceptor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TraceContext} W3C {@code traceparent} parsing/rendering.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class TraceContextTest {

    private static final String VALID =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void parse_validSampledHeader_extractsFields() {
        TraceContext ctx = TraceContext.parse(VALID);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(ctx.getSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(ctx.isSampled()).isTrue();
    }

    @Test
    void parse_notSampledFlag_isReflected() {
        TraceContext ctx = TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");

        assertThat(ctx).isNotNull();
        assertThat(ctx.isSampled()).isFalse();
    }

    @Test
    void parse_trimsWhitespace() {
        assertThat(TraceContext.parse("  " + VALID + "  ")).isNotNull();
    }

    @Test
    void parse_nullOrBlank_returnsNull() {
        assertThat(TraceContext.parse(null)).isNull();
        assertThat(TraceContext.parse("")).isNull();
        assertThat(TraceContext.parse("   ")).isNull();
    }

    @Test
    void parse_wrongNumberOfParts_returnsNull() {
        assertThat(TraceContext.parse("00-abc-def")).isNull();
        assertThat(TraceContext.parse("00-a-b-c-d")).isNull();
    }

    @Test
    void parse_unknownVersion_returnsNull() {
        assertThat(TraceContext.parse(
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isNull();
    }

    @Test
    void parse_wrongLengths_returnsNull() {
        assertThat(TraceContext.parse("00-abcd-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-abcd-01")).isNull();
        assertThat(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-001")).isNull();
    }

    @Test
    void parse_nonHex_returnsNull() {
        assertThat(TraceContext.parse(
                "00-ZZZ92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-ZZf067aa0ba902b7-01")).isNull();
        assertThat(TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-ZZ")).isNull();
    }

    @Test
    void parse_allZeroTraceOrSpanId_returnsNull() {
        assertThat(TraceContext.parse(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01")).isNull();
    }

    @Test
    void toTraceparent_roundTripsThroughParse() {
        TraceContext ctx = TraceContext.parse(VALID);

        assertThat(ctx.toTraceparent()).isEqualTo(VALID);
        assertThat(TraceContext.parse(ctx.toTraceparent()).getSpanId()).isEqualTo(ctx.getSpanId());
    }

    @Test
    void toTraceparent_notSampled_rendersZeroFlags() {
        TraceContext ctx = new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false);

        assertThat(ctx.toTraceparent()).endsWith("-00");
    }

    @Test
    void newRoot_producesValidSampledContext() {
        TraceContext root = TraceContext.newRoot();

        assertThat(root.getTraceId()).hasSize(32);
        assertThat(root.getSpanId()).hasSize(16);
        assertThat(root.isSampled()).isTrue();
        // Must be a re-parseable, spec-valid traceparent.
        assertThat(TraceContext.parse(root.toTraceparent())).isNotNull();
    }

    @Test
    void newRoot_generatesDistinctIds() {
        assertThat(TraceContext.newRoot().getTraceId())
                .isNotEqualTo(TraceContext.newRoot().getTraceId());
    }

    @Test
    void withNewSpan_keepsTraceIdAndSampled_changesSpanId() {
        TraceContext parent = TraceContext.parse(VALID);
        TraceContext child = parent.withNewSpan();

        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.isSampled()).isEqualTo(parent.isSampled());
        assertThat(child.getSpanId()).isNotEqualTo(parent.getSpanId());
        assertThat(child.getSpanId()).hasSize(16);
    }

    @Test
    void toString_equalsTraceparent() {
        TraceContext ctx = TraceContext.parse(VALID);
        assertThat(ctx.toString()).isEqualTo(ctx.toTraceparent());
    }
}
