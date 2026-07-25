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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * gRPC client interceptor that records Micrometer metrics for every outbound
 * call:
 *
 * <ul>
 *   <li>{@code grpc.client.calls} - counter tagged by {@code method} and {@code status}</li>
 *   <li>{@code grpc.client.duration} - timer tagged by {@code method} and {@code status}</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class MetricsClientInterceptor implements ClientInterceptor {

    private static final String CALLS_METRIC = "grpc.client.calls";
    private static final String DURATION_METRIC = "grpc.client.duration";

    private final MeterRegistry registry;

    /**
     * Creates the interceptor.
     *
     * @param registry Micrometer registry to record metrics into
     */
    public MetricsClientInterceptor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        String methodName = method.getFullMethodName();
        Timer.Sample sample = Timer.start(registry);
        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        String statusCode = status.getCode().name();
                        registry.counter(CALLS_METRIC, "method", methodName, "status", statusCode).increment();
                        sample.stop(registry.timer(DURATION_METRIC, "method", methodName, "status", statusCode));
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
