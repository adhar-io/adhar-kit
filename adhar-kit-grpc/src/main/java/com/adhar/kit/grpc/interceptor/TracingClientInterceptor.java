package com.adhar.kit.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.MDC;

/**
 * gRPC client interceptor that propagates the active trace context onto
 * outbound calls as a W3C {@code traceparent} header and records a client span.
 *
 * <p>The parent trace context is resolved, in order of preference, from:</p>
 * <ol>
 *   <li>the gRPC {@link io.grpc.Context} value set by
 *       {@link TracingServerInterceptor} (so an outbound call made while
 *       handling an inbound one continues the same trace, starting a child
 *       span);</li>
 *   <li>SLF4J {@link MDC} {@code traceId} (so a call made from a non-gRPC
 *       thread that nonetheless carries a trace id still propagates it);</li>
 *   <li>a freshly generated root context when neither is available.</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TracingClientInterceptor implements ClientInterceptor {

    private final GrpcObserver observer;

    /**
     * Creates the interceptor.
     *
     * @param observer observer used to record spans; if {@code null},
     *                 {@link GrpcObserver#NOOP} is used (propagation only)
     */
    public TracingClientInterceptor(GrpcObserver observer) {
        this.observer = observer != null ? observer : GrpcObserver.NOOP;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        TraceContext context = resolveContext();
        String methodName = method.getFullMethodName();

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.removeAll(TracingServerInterceptor.TRACEPARENT_KEY);
                headers.put(TracingServerInterceptor.TRACEPARENT_KEY, context.toTraceparent());

                GrpcObserver.Span span = observer.start(methodName, GrpcObserver.Kind.CLIENT, context);

                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        span.finish(status);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }

    /**
     * Resolves the parent trace context to propagate: the gRPC context value
     * (as a child span) if present, otherwise an MDC-derived context, otherwise
     * a new root.
     *
     * @return the trace context to attach to the outbound call
     */
    private TraceContext resolveContext() {
        TraceContext current = TracingServerInterceptor.TRACE_CONTEXT_KEY.get();
        if (current != null) {
            return current.withNewSpan();
        }
        String mdcTraceId = MDC.get(TracingServerInterceptor.MDC_TRACE_ID);
        if (mdcTraceId != null && mdcTraceId.length() == 32) {
            TraceContext fromMdc = TraceContext.parse("00-" + mdcTraceId + "-0000000000000001-01");
            if (fromMdc != null) {
                return fromMdc.withNewSpan();
            }
        }
        return TraceContext.newRoot();
    }
}
