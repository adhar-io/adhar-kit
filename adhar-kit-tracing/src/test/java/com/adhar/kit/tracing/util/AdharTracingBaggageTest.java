package com.adhar.kit.tracing.util;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AdharTracing}'s baggage management, exercised against a real
 * {@link SimpleTracer} (from {@code micrometer-tracing-test}) instead of a mock, so that
 * set/get/propagation is verified against Micrometer's actual, context-scoped baggage API
 * ({@link Tracer#createBaggageInScope(String, String)} / {@link Tracer#getAllBaggage()})
 * rather than the previous fake local map.
 * <p>
 * {@link SimpleTracer}'s baggage implementation is keyed by the current
 * {@link io.micrometer.tracing.TraceContext}, so every test keeps a span active for its
 * duration, mirroring how baggage is actually used within a request/trace.
 * </p>
 */
class AdharTracingBaggageTest {

    private SimpleTracer tracer;
    private Span span;
    private Tracer.SpanInScope spanInScope;

    @BeforeEach
    void setUp() {
        tracer = new SimpleTracer();
        span = tracer.nextSpan().name("test-span").start();
        spanInScope = tracer.withSpan(span);
    }

    @AfterEach
    void tearDown() {
        spanInScope.close();
        span.end();
    }

    @Test
    void setAndGetBaggageRoundTrips() {
        AdharTracing adharTracing = new AdharTracing(tracer);

        adharTracing.setBaggage("user.id", "12345");
        adharTracing.setBaggage("tenant.id", "tenant-abc");

        assertThat(adharTracing.getBaggage("user.id")).isEqualTo("12345");
        assertThat(adharTracing.getBaggage("tenant.id")).isEqualTo("tenant-abc");
        assertThat(adharTracing.getBaggage("nonexistent")).isNull();
    }

    @Test
    void overwritingBaggageKeyReplacesValue() {
        AdharTracing adharTracing = new AdharTracing(tracer);

        adharTracing.setBaggage("k", "v1");
        assertThat(adharTracing.getBaggage("k")).isEqualTo("v1");

        adharTracing.setBaggage("k", "v2");
        assertThat(adharTracing.getBaggage("k")).isEqualTo("v2");
        assertThat(adharTracing.getAllBaggage()).containsEntry("k", "v2").hasSize(1);
    }

    @Test
    void removeBaggageDeletesEntry() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("k", "v");

        adharTracing.removeBaggage("k");

        assertThat(adharTracing.getBaggage("k")).isNull();
        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void getAllBaggageReflectsAllEntries() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("a", "1");
        adharTracing.setBaggage("b", "2");

        assertThat(adharTracing.getAllBaggage()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "2"));
        assertThat(adharTracing.getBaggageCount()).isEqualTo(2);
        assertThat(adharTracing.containsBaggageKey("a")).isTrue();
        assertThat(adharTracing.containsBaggageKey("z")).isFalse();
    }

    @Test
    void clearBaggageRemovesEverything() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("a", "1");
        adharTracing.setBaggage("b", "2");

        adharTracing.clearBaggage();

        assertThat(adharTracing.isBaggageEmpty()).isTrue();
        assertThat(adharTracing.getAllBaggage()).isEmpty();
    }

    @Test
    void setBaggageItemsSetsAllEntries() {
        AdharTracing adharTracing = new AdharTracing(tracer);

        adharTracing.setBaggageItems(Map.of("x", "1", "y", "2"));

        assertThat(adharTracing.getBaggage("x")).isEqualTo("1");
        assertThat(adharTracing.getBaggage("y")).isEqualTo("2");
    }

    @Test
    void setBaggageItemsWithNullMapIsNoOp() {
        AdharTracing adharTracing = new AdharTracing(tracer);

        adharTracing.setBaggageItems(null);

        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void correlationFieldIsTaggedOnCurrentSpanButOthersAreNot() {
        AdharTracingProperties.BaggageProperties props = new AdharTracingProperties.BaggageProperties();
        props.setCorrelationFields(new String[]{"tenant.id"});
        AdharTracing adharTracing = new AdharTracing(tracer, props);

        adharTracing.setBaggage("tenant.id", "tenant-abc");
        adharTracing.setBaggage("other.key", "should-not-be-tagged");

        SimpleSpan simpleSpan = (SimpleSpan) tracer.currentSpan();
        assertThat(simpleSpan.getTags()).containsEntry("baggage.tenant.id", "tenant-abc");
        assertThat(simpleSpan.getTags()).doesNotContainKey("baggage.other.key");
    }

    @Test
    void baggageDisabledIsNoOp() {
        AdharTracingProperties.BaggageProperties props = new AdharTracingProperties.BaggageProperties();
        props.setEnabled(false);
        AdharTracing adharTracing = new AdharTracing(tracer, props);

        adharTracing.setBaggage("k", "v");

        assertThat(adharTracing.getBaggage("k")).isNull();
        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void copyBaggageToSpanTagsEachEntry() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("k1", "v1");
        adharTracing.setBaggage("k2", "v2");

        SimpleSpan targetSpan = (SimpleSpan) tracer.nextSpan().name("child");
        adharTracing.copyBaggageToSpan(targetSpan);

        assertThat(targetSpan.getTags()).containsEntry("baggage.k1", "v1");
        assertThat(targetSpan.getTags()).containsEntry("baggage.k2", "v2");
    }

    // ---- W3C `baggage` header propagation ----

    @Test
    void injectBaggageIntoHeadersProducesW3CFormat() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("user-id", "12345");

        Map<String, String> headers = new HashMap<>();
        adharTracing.injectBaggageIntoHeaders(headers);

        assertThat(headers).containsKey("baggage");
        assertThat(headers.get("baggage")).isEqualTo("user-id=12345");
    }

    @Test
    void injectBaggageOnlyIncludesRemoteFieldsWhenConfigured() {
        AdharTracingProperties.BaggageProperties props = new AdharTracingProperties.BaggageProperties();
        props.setRemoteFields(new String[]{"user-id"});
        AdharTracing adharTracing = new AdharTracing(tracer, props);

        adharTracing.setBaggage("user-id", "12345");
        adharTracing.setBaggage("internal-only", "secret");

        Map<String, String> headers = new HashMap<>();
        adharTracing.injectBaggageIntoHeaders(headers);

        assertThat(headers.get("baggage")).isEqualTo("user-id=12345");
    }

    @Test
    void injectBaggageWhenEmptyDoesNotAddHeader() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        Map<String, String> headers = new HashMap<>();

        adharTracing.injectBaggageIntoHeaders(headers);

        assertThat(headers).isEmpty();
    }

    @Test
    void injectBaggageIntoNullHeadersIsNoOp() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        adharTracing.setBaggage("k", "v");

        // Must not throw.
        adharTracing.injectBaggageIntoHeaders(null);
    }

    @Test
    void extractBaggageFromHeadersParsesW3CFormat() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        Map<String, String> headers = Map.of(
                "Baggage", "user-id=12345,tenant-id=tenant-abc",
                "content-type", "application/json"
        );

        adharTracing.extractBaggageFromHeaders(headers);

        assertThat(adharTracing.getBaggage("user-id")).isEqualTo("12345");
        assertThat(adharTracing.getBaggage("tenant-id")).isEqualTo("tenant-abc");
        assertThat(adharTracing.getBaggageCount()).isEqualTo(2);
    }

    @Test
    void extractBaggageFromHeadersHandlesPercentEncodingAndMemberProperties() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        Map<String, String> headers = Map.of("baggage", "key1=value%20with%20spaces;prop1=x,key2=value2");

        adharTracing.extractBaggageFromHeaders(headers);

        assertThat(adharTracing.getBaggage("key1")).isEqualTo("value with spaces");
        assertThat(adharTracing.getBaggage("key2")).isEqualTo("value2");
    }

    @Test
    void extractBaggageFromHeadersRespectsMaxEntries() {
        AdharTracingProperties.BaggageProperties props = new AdharTracingProperties.BaggageProperties();
        props.setMaxEntries(1);
        AdharTracing adharTracing = new AdharTracing(tracer, props);

        Map<String, String> headers = Map.of("baggage", "a=1,b=2,c=3");
        adharTracing.extractBaggageFromHeaders(headers);

        assertThat(adharTracing.getBaggageCount()).isEqualTo(1);
        assertThat(adharTracing.getBaggage("a")).isEqualTo("1");
    }

    @Test
    void extractBaggageFromHeadersTruncatesLongValues() {
        AdharTracingProperties.BaggageProperties props = new AdharTracingProperties.BaggageProperties();
        props.setMaxValueLength(5);
        AdharTracing adharTracing = new AdharTracing(tracer, props);

        Map<String, String> headers = Map.of("baggage", "k=abcdefghij");
        adharTracing.extractBaggageFromHeaders(headers);

        assertThat(adharTracing.getBaggage("k")).isEqualTo("abcde");
    }

    @Test
    void extractBaggageFromHeadersWithNoBaggageHeaderIsNoOp() {
        AdharTracing adharTracing = new AdharTracing(tracer);
        Map<String, String> headers = Map.of("content-type", "application/json");

        adharTracing.extractBaggageFromHeaders(headers);

        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void extractBaggageFromNullHeadersIsNoOp() {
        AdharTracing adharTracing = new AdharTracing(tracer);

        // Must not throw.
        adharTracing.extractBaggageFromHeaders(null);

        assertThat(adharTracing.isBaggageEmpty()).isTrue();
    }

    @Test
    void roundTripInjectThenExtractPreservesBaggage() {
        AdharTracing sender = new AdharTracing(tracer);
        sender.setBaggage("user-id", "42");
        sender.setBaggage("tenant-id", "acme");

        Map<String, String> headers = new HashMap<>();
        sender.injectBaggageIntoHeaders(headers);

        sender.clearBaggage();
        assertThat(sender.isBaggageEmpty()).isTrue();

        sender.extractBaggageFromHeaders(headers);
        assertThat(sender.getBaggage("user-id")).isEqualTo("42");
        assertThat(sender.getBaggage("tenant-id")).isEqualTo("acme");
    }
}
