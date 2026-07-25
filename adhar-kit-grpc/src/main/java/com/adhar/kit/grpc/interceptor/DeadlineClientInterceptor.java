package com.adhar.kit.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client interceptor that applies a default deadline to calls which do
 * not already specify one.
 *
 * <p>Without this interceptor, the {@code default-timeout} configured on a
 * channel is purely documentation - nothing in grpc-java reads it. Wiring it
 * in as {@link CallOptions#withDeadlineAfter} ensures a runaway call cannot
 * hang indefinitely just because the caller forgot to set a deadline.</p>
 *
 * <p>If the call already has a deadline (set explicitly by the caller, e.g.
 * via {@code stub.withDeadlineAfter(...)}), that deadline is left untouched -
 * this interceptor only fills the gap.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DeadlineClientInterceptor implements ClientInterceptor {

    private final long defaultTimeoutMillis;

    /**
     * Creates a deadline interceptor.
     *
     * @param defaultTimeoutMillis default timeout in milliseconds; values less
     *                             than or equal to zero disable the default
     *                             (calls without an explicit deadline run
     *                             without one)
     */
    public DeadlineClientInterceptor(long defaultTimeoutMillis) {
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        CallOptions effectiveOptions = callOptions;
        if (defaultTimeoutMillis > 0 && callOptions.getDeadline() == null) {
            effectiveOptions = callOptions.withDeadlineAfter(defaultTimeoutMillis, TimeUnit.MILLISECONDS);
            log.debug("Applying default deadline of {}ms to call: method={}",
                    defaultTimeoutMillis, method.getFullMethodName());
        }

        return next.newCall(method, effectiveOptions);
    }
}
