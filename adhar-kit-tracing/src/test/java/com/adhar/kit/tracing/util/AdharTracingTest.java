package com.adhar.kit.tracing.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AdharTracing} - consolidated tracing utility.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdharTracingTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    private AdharTracing adharTracing;

    @BeforeEach
    void setUp() {
        // Setup mock behavior - use span builder pattern correctly
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.currentSpan()).thenReturn(span);

        adharTracing = new AdharTracing(tracer);
    }

    // ========== SPAN MANAGEMENT TESTS ==========

    @Test
    void testWithinSpanSupplier() {
        // Execute operation within span using explicit Supplier
        java.util.function.Supplier<String> supplier = () -> "test-result";
        String result = adharTracing.withinSpan("test.operation", supplier);

        // Verify result
        assertThat(result).isEqualTo("test-result");

        // Verify span interactions
        verify(tracer).nextSpan();
        verify(span).name("test.operation");
        verify(span).start();
        verify(span).tag("success", "true");
        verify(span).end();
    }

    @Test
    void testWithinSpanSupplierWithTags() {
        Map<String, String> tags = Map.of("service", "user-service", "version", "1.0");

        // Execute operation within span with tags using explicit Supplier
        java.util.function.Supplier<String> supplier = () -> "test-result";
        String result = adharTracing.withinSpan("test.operation", tags, supplier);

        // Verify result
        assertThat(result).isEqualTo("test-result");

        // Verify span interactions
        verify(span).tag("service", "user-service");
        verify(span).tag("version", "1.0");
        verify(span).tag("success", "true");
    }

    @Test
    void testWithinSpanSupplierWithException() {
        RuntimeException testException = new RuntimeException("Test exception");

        // Execute operation that throws exception using explicit Supplier
        java.util.function.Supplier<String> supplier = () -> {
            throw testException;
        };

        assertThatThrownBy(() -> adharTracing.withinSpan("test.operation", supplier))
            .isSameAs(testException);

        // Verify span recorded the error
        verify(span).tag("success", "false");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).tag("error.message", "Test exception");
    }

    @Test
    void testWithinSpanRunnable() {
        final String[] result = {null};

        // Execute runnable within span using explicit Runnable
        Runnable runnable = () -> result[0] = "executed";
        adharTracing.withinSpan("test.operation", runnable);

        // Verify execution
        assertThat(result[0]).isEqualTo("executed");

        // Verify span was created
        verify(tracer).nextSpan();
        verify(span).name("test.operation");
        verify(span).tag("success", "true");
    }

    @Test
    void testWithinSpanAsync() throws Exception {
        // Execute async operation within span using explicit Supplier
        java.util.function.Supplier<CompletableFuture<String>> asyncSupplier =
            () -> CompletableFuture.completedFuture("async-result");

        CompletableFuture<String> result = adharTracing.withinSpanAsync("test.async", asyncSupplier);

        // Verify result
        assertThat(result.get()).isEqualTo("async-result");

        // Verify span was created with async tag
        verify(span).tag("async", "true");
        verify(span).tag("success", "true");
    }

    @Test
    void testGetCurrentSpan() {
        // Test getting current span
        Span currentSpan = adharTracing.getCurrentSpan();

        assertThat(currentSpan).isEqualTo(span);
        verify(tracer).currentSpan();
    }

    @Test
    void testAddTagsToCurrentSpan() {
        // Test adding tags to current span
        adharTracing.addTag("custom.tag", "custom-value");
        adharTracing.addTags(Map.of("tag1", "value1", "tag2", "value2"));

        verify(span).tag("custom.tag", "custom-value");
        verify(span).tag("tag1", "value1");
        verify(span).tag("tag2", "value2");
    }

    @Test
    void testAddTagsWithoutCurrentSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        // Should not throw exception when no current span
        adharTracing.addTag("test.tag", "test-value");
        adharTracing.addTags(Map.of("tag1", "value1"));

        // No interactions with span since it's null
        verify(span, never()).tag(anyString(), anyString());
    }

    @Test
    void testAddEventToCurrentSpan() {
        // Test adding events to current span
        adharTracing.addEvent("test.event");
        adharTracing.addEvent("test.event.with.attributes",
            Map.of("attr1", "value1", "attr2", "value2"));

        verify(span, times(2)).event(anyString());
        verify(span).tag("event.attr1", "value1");
        verify(span).tag("event.attr2", "value2");
    }

    @Test
    void testRecordException() {
        RuntimeException testException = new RuntimeException("Test error");

        adharTracing.recordException(testException);

        verify(span).tag("error", "true");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).tag("error.message", "Test error");
    }

    @Test
    void testCreateSpan() {
        Span createdSpan = adharTracing.createSpan("custom.span");

        assertThat(createdSpan).isNotNull();
        verify(tracer).nextSpan();
        verify(span).name("custom.span");
    }

    @Test
    void testCreateSpanWithTags() {
        Map<String, String> tags = Map.of("tag1", "value1");
        Span createdSpan = adharTracing.createSpan("custom.span.with.tags", tags);

        assertThat(createdSpan).isNotNull();
        verify(span).tag("tag1", "value1");
    }

    @Test
    void testIsTracingActive() {
        // With current span, tracing is active
        boolean isActive = adharTracing.isTracingActive();
        assertThat(isActive).isTrue();

        // Without current span, tracing is not active
        when(tracer.currentSpan()).thenReturn(null);
        isActive = adharTracing.isTracingActive();
        assertThat(isActive).isFalse();
    }

    @Test
    void testCreateSemanticSpans() {
        // Test database span
        Span dbSpan = adharTracing.createDatabaseSpan("SELECT", "users", "SELECT * FROM users");
        assertThat(dbSpan).isNotNull();
        verify(span).name("SELECT users");
        verify(span).tag("span.kind", "client");
        verify(span).tag("db.system", "sql");
        verify(span).tag("db.operation", "SELECT");

        // Reset mock for next test
        reset(tracer, span);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);

        // Test HTTP client span
        Span httpSpan = adharTracing.createHttpClientSpan("GET", "https://api.example.com/users");
        assertThat(httpSpan).isNotNull();
        verify(span).name("HTTP GET");
        verify(span).tag("span.kind", "client");
        verify(span).tag("http.method", "GET");
        verify(span).tag("http.url", "https://api.example.com/users");

        // Reset mock for next test
        reset(tracer, span);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);

        // Test messaging span
        Span msgSpan = adharTracing.createMessagingSpan("send", "user.events", "kafka");
        assertThat(msgSpan).isNotNull();
        verify(span).name("user.events send");
        verify(span).tag("span.kind", "producer");
        verify(span).tag("messaging.operation", "send");
        verify(span).tag("messaging.destination", "user.events");
        verify(span).tag("messaging.system", "kafka");
    }

    // Baggage management is backed by the real Tracer baggage API (createBaggageInScope /
    // getAllBaggage), which requires an active span/trace context and real (non-mock)
    // semantics to exercise meaningfully. See AdharTracingBaggageTest (uses SimpleTracer)
    // for set/get/remove/clear/propagation coverage, and AdharTracingMoreTest for the
    // mock-based error/edge-path coverage (null args, exceptions swallowed, disabled config).
}
