package com.adhar.kit.dapr.observation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Trace context of the current Micrometer span, rendered with the application's configured
 * propagator. Inside a kit call the current span is the call's own observation, so Dapr's spans
 * hang under it.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class MicrometerTraceContext implements DaprTraceContext {

    private final Supplier<Tracer> tracer;
    private final Supplier<Propagator> propagator;

    public MicrometerTraceContext(Tracer tracer, Propagator propagator) {
        this(() -> tracer, () -> propagator);
    }

    /**
     * Resolves the tracer and propagator on each call, so they may appear after this object.
     *
     * @param tracer     supplies the tracer, or {@code null} when there is none
     * @param propagator supplies the propagator, or {@code null} when there is none
     */
    public MicrometerTraceContext(Supplier<Tracer> tracer, Supplier<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Map<String, String> current() {
        Tracer currentTracer = tracer.get();
        Propagator currentPropagator = propagator.get();
        if (currentTracer == null || currentPropagator == null) {
            return Map.of();
        }
        Span span = currentTracer.currentSpan();
        if (span == null) {
            return Map.of();
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        currentPropagator.inject(span.context(), carrier, Map::put);
        Map<String, String> headers = new LinkedHashMap<>();
        carrier.forEach((key, value) -> {
            if (TRACEPARENT.equalsIgnoreCase(key)) {
                headers.put(TRACEPARENT, value);
            } else if (TRACESTATE.equalsIgnoreCase(key)) {
                headers.put(TRACESTATE, value);
            }
        });
        return headers;
    }
}
