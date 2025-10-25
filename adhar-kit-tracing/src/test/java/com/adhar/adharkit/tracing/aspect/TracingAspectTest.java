package com.adhar.adharkit.tracing.aspect;

import com.adhar.adharkit.tracing.annotation.AsyncSpan;
import com.adhar.adharkit.tracing.annotation.ContinueSpan;
import com.adhar.adharkit.tracing.annotation.DatabaseSpan;
import com.adhar.adharkit.tracing.annotation.HttpClientSpan;
import com.adhar.adharkit.tracing.annotation.MessagingSpan;
import com.adhar.adharkit.tracing.annotation.NewSpan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TracingAspect}.
 */
@ExtendWith(MockitoExtension.class)
class TracingAspectTest {

    private SimpleTracer tracer;
    private TracingAspect tracingAspect;
    private TestService testService;

    @BeforeEach
    void setUp() {
        tracer = new SimpleTracer();
        tracingAspect = new TracingAspect(tracer);

        // Create proxy with aspect
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestService());
        factory.addAspect(tracingAspect);
        testService = factory.getProxy();
    }

    @Test
    void testNewSpanAnnotation() {
        // Execute method with @NewSpan
        String result = testService.methodWithNewSpan("test-input");

        // Verify result
        assertThat(result).isEqualTo("processed: test-input");

        // Verify span was created
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("test.span");
        assertThat(span.getTags()).containsEntry("component", "adhar-tracing");
        assertThat(span.getTags()).containsEntry("span.kind", "internal");
        assertThat(span.getTags()).containsEntry("success", "true");
    }

    @Test
    void testNewSpanWithTags() {
        // Execute method with @NewSpan with tags
        testService.methodWithNewSpanAndTags("test-input", "test-type");

        // Verify span was created with tags
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("test.span.with.tags");
        assertThat(span.getTags()).containsEntry("input", "test-input");
        assertThat(span.getTags()).containsEntry("type", "test-type");
    }

    @Test
    void testNewSpanWithException() {
        // Execute method that throws exception
        assertThatThrownBy(() -> testService.methodWithNewSpanThatThrows())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");

        // Verify span was created with error tags
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getTags()).containsEntry("success", "false");
        assertThat(span.getTags()).containsEntry("error.class", "RuntimeException");
        assertThat(span.getTags()).containsEntry("error.message", "Test exception");
    }

    @Test
    void testContinueSpanAnnotation() {
        // Start a parent span
        Span parentSpan = tracer.nextSpan().name("parent").start();

        try (Tracer.SpanInScope spanInScope = tracer.withSpanInScope(parentSpan)) {
            // Execute method with @ContinueSpan
            testService.methodWithContinueSpan("test-input");
        } finally {
            parentSpan.end();
        }

        // Verify parent span was modified
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("parent");
        assertThat(span.getTags()).containsEntry("operation", "continue");
        assertThat(span.getTags()).containsEntry("input", "test-input");
    }

    @Test
    void testContinueSpanWithoutCurrentSpan() {
        // Execute method with @ContinueSpan without current span
        String result = testService.methodWithContinueSpan("test-input");

        // Verify method still executes normally
        assertThat(result).isEqualTo("continued: test-input");

        // Verify no spans were created
        assertThat(tracer.getSpans()).isEmpty();
    }

    @Test
    void testDatabaseSpanAnnotation() {
        // Execute method with @DatabaseSpan
        testService.methodWithDatabaseSpan("123");

        // Verify database span was created
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("SELECT users");
        assertThat(span.getTags()).containsEntry("component", "adhar-tracing");
        assertThat(span.getTags()).containsEntry("span.kind", "client");
        assertThat(span.getTags()).containsEntry("db.system", "sql");
        assertThat(span.getTags()).containsEntry("db.operation", "SELECT");
        assertThat(span.getTags()).containsEntry("db.sql.table", "users");
        assertThat(span.getTags()).containsEntry("db.statement", "SELECT * FROM users WHERE id = 123");
    }

    @Test
    void testHttpClientSpanAnnotation() {
        // Execute method with @HttpClientSpan
        testService.methodWithHttpClientSpan("http://example.com", "/api/users");

        // Verify HTTP client span was created
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("HTTP GET");
        assertThat(span.getTags()).containsEntry("component", "adhar-tracing");
        assertThat(span.getTags()).containsEntry("span.kind", "client");
        assertThat(span.getTags()).containsEntry("http.method", "GET");
        assertThat(span.getTags()).containsEntry("http.url", "http://example.com/api/users");
    }

    @Test
    void testMessagingSpanAnnotation() {
        // Execute method with @MessagingSpan
        testService.methodWithMessagingSpan("test-message");

        // Verify messaging span was created
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("user.events send");
        assertThat(span.getTags()).containsEntry("component", "adhar-tracing");
        assertThat(span.getTags()).containsEntry("span.kind", "producer");
        assertThat(span.getTags()).containsEntry("messaging.operation", "send");
        assertThat(span.getTags()).containsEntry("messaging.destination", "user.events");
        assertThat(span.getTags()).containsEntry("messaging.destination_kind", "topic");
        assertThat(span.getTags()).containsEntry("messaging.system", "kafka");
    }

    @Test
    void testAsyncSpanAnnotation() {
        // Execute method with @AsyncSpan
        CompletableFuture<String> result = testService.methodWithAsyncSpan("test-input");

        // Wait for completion
        String value = result.join();
        assertThat(value).isEqualTo("async: test-input");

        // Verify async span was created
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("async.operation");
        assertThat(span.getTags()).containsEntry("component", "adhar-tracing");
        assertThat(span.getTags()).containsEntry("span.kind", "internal");
        assertThat(span.getTags()).containsEntry("async", "true");
        assertThat(span.getTags()).containsEntry("success", "true");
    }

    @Test
    void testAsyncSpanWithException() {
        // Execute async method that throws exception
        CompletableFuture<String> result = testService.methodWithAsyncSpanThatThrows();

        // Verify exception is propagated
        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(RuntimeException.class);

        // Verify span was created with error tags
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getTags()).containsEntry("success", "false");
        assertThat(span.getTags()).containsEntry("error.class", "RuntimeException");
    }

    @Test
    void testSpanNameGeneration() {
        // Execute method without explicit span name
        testService.methodWithoutExplicitSpanName();

        // Verify span name is generated from method signature
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getName()).isEqualTo("TestService.methodWithoutExplicitSpanName");
    }

    @Test
    void testSpelExpressionEvaluation() {
        // Execute method with SpEL expressions in tags
        testService.methodWithSpelExpressions("user123", "admin");

        // Verify SpEL expressions were evaluated
        assertThat(tracer.getSpans()).hasSize(1);
        Span span = tracer.getSpans().get(0);
        assertThat(span.getTags()).containsEntry("userId", "user123");
        assertThat(span.getTags()).containsEntry("role", "admin");
    }

    @Test
    void testNestedSpans() {
        // Execute method that calls another traced method
        testService.methodThatCallsAnotherTracedMethod("test");

        // Verify both spans were created
        assertThat(tracer.getSpans()).hasSize(2);

        // Find parent and child spans
        Span parentSpan = tracer.getSpans().stream()
                .filter(span -> "parent.operation".equals(span.getName()))
                .findFirst()
                .orElseThrow();

        Span childSpan = tracer.getSpans().stream()
                .filter(span -> "child.operation".equals(span.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(parentSpan).isNotNull();
        assertThat(childSpan).isNotNull();
    }

    /**
     * Test service with various tracing annotations.
     */
    static class TestService {

        @NewSpan("test.span")
        public String methodWithNewSpan(String input) {
            return "processed: " + input;
        }

        @NewSpan(value = "test.span.with.tags", tags = {"input", "#{#input}", "type", "#{#type}"})
        public void methodWithNewSpanAndTags(String input, String type) {
            // Method implementation
        }

        @NewSpan("test.span.exception")
        public void methodWithNewSpanThatThrows() {
            throw new RuntimeException("Test exception");
        }

        @ContinueSpan(tags = {"operation", "continue", "input", "#{#input}"})
        public String methodWithContinueSpan(String input) {
            return "continued: " + input;
        }

        @DatabaseSpan(operation = "SELECT", table = "users", statement = "SELECT * FROM users WHERE id = #{#id}")
        public void methodWithDatabaseSpan(String id) {
            // Database operation simulation
        }

        @HttpClientSpan(method = "GET", url = "#{#baseUrl}#{#path}")
        public void methodWithHttpClientSpan(String baseUrl, String path) {
            // HTTP client operation simulation
        }

        @MessagingSpan(operation = "send", destination = "user.events", destinationType = "topic", system = "kafka")
        public void methodWithMessagingSpan(String message) {
            // Messaging operation simulation
        }

        @AsyncSpan("async.operation")
        public CompletableFuture<String> methodWithAsyncSpan(String input) {
            return CompletableFuture.completedFuture("async: " + input);
        }

        @AsyncSpan("async.operation.exception")
        public CompletableFuture<String> methodWithAsyncSpanThatThrows() {
            return CompletableFuture.failedFuture(new RuntimeException("Async exception"));
        }

        @NewSpan
        public void methodWithoutExplicitSpanName() {
            // Method without explicit span name
        }

        @NewSpan(value = "spel.test", tags = {"userId", "#{#userId}", "role", "#{#role}"})
        public void methodWithSpelExpressions(String userId, String role) {
            // Method with SpEL expressions in tags
        }

        @NewSpan("parent.operation")
        public void methodThatCallsAnotherTracedMethod(String input) {
            childMethod(input);
        }

        @NewSpan("child.operation")
        private void childMethod(String input) {
            // Child method
        }
    }
}
