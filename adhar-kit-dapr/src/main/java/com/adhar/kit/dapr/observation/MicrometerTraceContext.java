package com.adhar.kit.dapr.observation;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace context of the current Micrometer span, rendered with the application's configured
 * propagator. Inside a kit call the current span is the call's own observation, so Dapr's spans
 * hang under it.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class MicrometerTraceContext implements DaprTraceContext {

    private final Tracer tracer;
    private final Propagator propagator;

    public MicrometerTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Map<String, String> current() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return Map.of();
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
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
