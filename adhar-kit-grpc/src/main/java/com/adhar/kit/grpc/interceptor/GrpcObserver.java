package com.adhar.kit.grpc.interceptor;

import io.grpc.Status;

/**
 * Abstraction over span/observation recording for gRPC calls, deliberately
 * free of any tracing-library types so that the tracing interceptors
 * ({@link TracingServerInterceptor}, {@link TracingClientInterceptor}) and the
 * server/client wiring compile and run whether or not micrometer-observation is
 * on the classpath.
 *
 * <p>When micrometer-observation is present a {@code MicrometerGrpcObserver}
 * bridges to Micrometer's {@code ObservationRegistry}; when it is absent the
 * {@link #NOOP} instance is used and trace propagation degrades gracefully to
 * MDC-only correlation performed by the interceptors themselves.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface GrpcObserver {

    /**
     * Whether the observed span represents the server or the client side of a
     * call.
     */
    enum Kind {
        /** Inbound (server-received) call. */
        SERVER,
        /** Outbound (client-initiated) call. */
        CLIENT
    }

    /**
     * A started span/observation that must be finished exactly once, when the
     * associated gRPC call completes.
     */
    interface Span {
        /**
         * Finishes the span, tagging it with the call's terminal status.
         *
         * @param status the gRPC status the call completed with
         */
        void finish(Status status);
    }

    /**
     * A no-op span, used by {@link #NOOP}.
     */
    Span NOOP_SPAN = status -> {
        // no-op
    };

    /**
     * An observer that records nothing. The tracing interceptors still perform
     * W3C {@code traceparent} propagation and MDC correlation on their own; this
     * observer simply adds no span recording on top.
     */
    GrpcObserver NOOP = (methodName, kind, context) -> NOOP_SPAN;

    /**
     * Starts a span for a gRPC call.
     *
     * @param methodName the full gRPC method name (e.g. {@code pkg.Svc/Method})
     * @param kind       whether this is the server or client side
     * @param context    the W3C trace context associated with the call
     * @return a {@link Span} to be {@link Span#finish(Status) finished} when the
     *         call completes
     */
    Span start(String methodName, Kind kind, TraceContext context);
}
