package com.adhar.kit.grpc.interceptor;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * gRPC server interceptor that records Micrometer metrics for every call:
 *
 * <ul>
 *   <li>{@code grpc.server.calls} - counter tagged by {@code method} and {@code status}</li>
 *   <li>{@code grpc.server.duration} - timer tagged by {@code method} and {@code status}</li>
 *   <li>{@code grpc.server.calls.inflight} - gauge of calls currently in progress</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class MetricsServerInterceptor implements ServerInterceptor {

    private static final String CALLS_METRIC = "grpc.server.calls";
    private static final String DURATION_METRIC = "grpc.server.duration";
    private static final String INFLIGHT_METRIC = "grpc.server.calls.inflight";

    private final MeterRegistry registry;
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * Creates the interceptor and registers the in-flight gauge.
     *
     * @param registry Micrometer registry to record metrics into
     */
    public MetricsServerInterceptor(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(INFLIGHT_METRIC, inFlight, AtomicInteger::get)
                .description("Number of gRPC server calls currently in flight")
                .register(registry);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        inFlight.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);

        ServerCall<ReqT, RespT> wrappedCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        inFlight.decrementAndGet();
                        String statusCode = status.getCode().name();
                        registry.counter(CALLS_METRIC, "method", methodName, "status", statusCode).increment();
                        sample.stop(registry.timer(DURATION_METRIC, "method", methodName, "status", statusCode));
                        super.close(status, trailers);
                    }
                };

        return next.startCall(wrappedCall, headers);
    }

    /**
     * Number of calls currently in flight, exposed for testing.
     *
     * @return current in-flight call count
     */
    public int getInFlightCount() {
        return inFlight.get();
    }
}
