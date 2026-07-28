package com.adhar.kit.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.MDC;

/**
 * gRPC server interceptor that establishes a trace context for every inbound
 * call.
 *
 * <p>It extracts the W3C {@code traceparent} header (see {@link TraceContext})
 * from the incoming {@link Metadata}; if present and well-formed it continues
 * that trace by starting a child span, otherwise it begins a new root trace.
 * The resulting {@link TraceContext} is:</p>
 * <ul>
 *   <li>placed on the gRPC {@link Context} under {@link #TRACE_CONTEXT_KEY} so
 *       downstream code (and {@link TracingClientInterceptor} on outbound calls)
 *       can continue the trace;</li>
 *   <li>mirrored into SLF4J {@link MDC} as {@code traceId}/{@code spanId} for log
 *       correlation - this is the fallback behaviour that works even when no
 *       tracing library is present;</li>
 *   <li>recorded as a span via the supplied {@link GrpcObserver}, which is a
 *       real Micrometer observation when micrometer-observation is on the
 *       classpath and a no-op otherwise.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TracingServerInterceptor implements ServerInterceptor {

    /**
     * Metadata key for the W3C {@code traceparent} header.
     */
    public static final Metadata.Key<String> TRACEPARENT_KEY =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * gRPC context key under which the active {@link TraceContext} is stored.
     */
    public static final Context.Key<TraceContext> TRACE_CONTEXT_KEY =
            Context.key("adhar-grpc-trace-context");

    /**
     * MDC key for the trace id.
     */
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * MDC key for the span id.
     */
    public static final String MDC_SPAN_ID = "spanId";

    private final GrpcObserver observer;

    /**
     * Creates the interceptor.
     *
     * @param observer observer used to record spans; if {@code null},
     *                 {@link GrpcObserver#NOOP} is used (MDC-only correlation)
     */
    public TracingServerInterceptor(GrpcObserver observer) {
        this.observer = observer != null ? observer : GrpcObserver.NOOP;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        TraceContext incoming = TraceContext.parse(headers.get(TRACEPARENT_KEY));
        TraceContext context = incoming != null ? incoming.withNewSpan() : TraceContext.newRoot();
        String methodName = call.getMethodDescriptor().getFullMethodName();

        MDC.put(MDC_TRACE_ID, context.getTraceId());
        MDC.put(MDC_SPAN_ID, context.getSpanId());

        GrpcObserver.Span span = observer.start(methodName, GrpcObserver.Kind.SERVER, context);

        ServerCall<ReqT, RespT> tracedCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        try {
                            span.finish(status);
                        } finally {
                            MDC.remove(MDC_TRACE_ID);
                            MDC.remove(MDC_SPAN_ID);
                        }
                        super.close(status, trailers);
                    }
                };

        Context grpcContext = Context.current().withValue(TRACE_CONTEXT_KEY, context);
        return Contexts.interceptCall(grpcContext, tracedCall, headers, next);
    }
}
