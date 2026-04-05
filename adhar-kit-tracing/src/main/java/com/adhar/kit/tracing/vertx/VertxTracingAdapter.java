package com.adhar.kit.tracing.vertx;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Vert.x adapter for distributed tracing using OpenTelemetry.
 *
 * <p>This adapter provides tracing integration for Vert.x applications using the
 * OpenTelemetry API. Since Vert.x does not have a built-in dependency injection
 * container, the {@link Tracer} instance is provided via the constructor.</p>
 *
 * <p>Vert.x supports OpenTelemetry natively via the {@code vertx-opentelemetry}
 * module, which instruments HTTP servers, HTTP clients, and event bus messaging.
 * This adapter extends that instrumentation by allowing application-level span
 * creation and management.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>No DI Required:</b> Standalone initialization via constructor</li>
 *   <li><b>OpenTelemetry Native:</b> Uses the same OpenTelemetry API as Vert.x tracing</li>
 *   <li><b>Span Management:</b> Create, tag, and manage custom application spans</li>
 *   <li><b>Context Propagation:</b> Proper parent-child span relationships</li>
 *   <li><b>Non-Blocking:</b> All tracing operations are non-blocking</li>
 * </ul>
 *
 * <p><b>Dependencies (pom.xml):</b></p>
 * <pre>{@code
 * <dependency>
 *     <groupId>io.vertx</groupId>
 *     <artifactId>vertx-opentelemetry</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>io.opentelemetry</groupId>
 *     <artifactId>opentelemetry-sdk</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Initialize OpenTelemetry SDK
 * OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
 *     .setTracerProvider(tracerProvider)
 *     .build();
 * Tracer tracer = openTelemetry.getTracer("my-service");
 *
 * // Create the adapter
 * VertxTracingAdapter tracing = new VertxTracingAdapter(tracer);
 *
 * // Use in a Verticle
 * public class OrderVerticle extends AbstractVerticle {
 *     private final VertxTracingAdapter tracing;
 *
 *     public OrderVerticle(Tracer tracer) {
 *         this.tracing = new VertxTracingAdapter(tracer);
 *     }
 *
 *     public void start() {
 *         vertx.eventBus().consumer("orders.create", msg -> {
 *             tracing.executeInSpan("process-order", () -> {
 *                 tracing.addTag("orderId", msg.body().toString());
 *                 // process order...
 *             });
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Vert.x OpenTelemetry integration:</b></p>
 * <pre>{@code
 * OpenTelemetry otel = // ... configure OpenTelemetry SDK
 * VertxOptions options = new VertxOptions()
 *     .setTracingOptions(new OpenTelemetryOptions(otel));
 * Vertx vertx = Vertx.vertx(options);
 *
 * Tracer tracer = otel.getTracer("my-service");
 * VertxTracingAdapter tracing = new VertxTracingAdapter(tracer);
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. The OpenTelemetry {@link Tracer} is thread-safe,
 * and span operations use thread-local context for proper parent-child relationships
 * across Vert.x event loop and worker threads.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see TracingService
 * @see FrameworkAdapter
 * @see Tracer
 */
public class VertxTracingAdapter implements FrameworkAdapter<TracingService>, TracingService {

    private static final Logger log = LoggerFactory.getLogger(VertxTracingAdapter.class);

    private final Tracer tracer;

    /**
     * Constructs a new Vert.x tracing adapter with the given OpenTelemetry Tracer.
     *
     * <p>The Tracer can be obtained from the OpenTelemetry SDK or from Vert.x's
     * OpenTelemetry integration after configuring {@code OpenTelemetryOptions}.</p>
     *
     * @param tracer the OpenTelemetry Tracer instance
     * @throws NullPointerException if tracer is null
     */
    public VertxTracingAdapter(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "Tracer must not be null");
        log.info("Initialized Vert.x Tracing adapter with OpenTelemetry");
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.VERTX;
    }

    @Override
    public TracingService getService() {
        return this;
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return new VertxSpanBuilder(tracer, name);
    }

    @Override
    public <T> T executeInSpan(String name, Supplier<T> operation) {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return operation.get();
        } finally {
            span.end();
        }
    }

    @Override
    public void executeInSpan(String name, Runnable operation) {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name).startSpan();
        try (Scope scope = span.makeCurrent()) {
            operation.run();
        } finally {
            span.end();
        }
    }

    @Override
    public String getCurrentTraceId() {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        return currentSpan.getSpanContext().getTraceId();
    }

    @Override
    public String getCurrentSpanId() {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        return currentSpan.getSpanContext().getSpanId();
    }

    @Override
    public void addTag(String key, String value) {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        currentSpan.setAttribute(key, value);
    }

    @Override
    public void addEvent(String name) {
        io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
        currentSpan.addEvent(name);
    }

    /**
     * Vert.x implementation of the SpanBuilder interface.
     */
    private static class VertxSpanBuilder implements SpanBuilder {
        private final io.opentelemetry.api.trace.SpanBuilder builder;

        VertxSpanBuilder(Tracer tracer, String name) {
            this.builder = tracer.spanBuilder(name);
        }

        @Override
        public SpanBuilder tag(String key, String value) {
            builder.setAttribute(key, value);
            return this;
        }

        @Override
        public SpanBuilder event(String name) {
            // Events are added after span starts
            return this;
        }

        @Override
        public TracingService.Span start() {
            io.opentelemetry.api.trace.Span started = builder.startSpan();
            return new VertxSpan(started);
        }
    }

    /**
     * Vert.x implementation of the Span interface.
     */
    private static class VertxSpan implements TracingService.Span {
        private final io.opentelemetry.api.trace.Span span;

        VertxSpan(io.opentelemetry.api.trace.Span span) {
            this.span = span;
        }

        @Override
        public void end() {
            span.end();
        }

        @Override
        public void addEvent(String name) {
            span.addEvent(name);
        }

        @Override
        public void setTag(String key, String value) {
            span.setAttribute(key, value);
        }

        @Override
        public void recordException(Throwable throwable) {
            span.recordException(throwable);
        }
    }
}
