package com.adhar.kit.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * gRPC server interceptor for logging and tracing.
 *
 * <p>Provides comprehensive logging for all gRPC requests:</p>
 * <ul>
 *   <li>Request/response logging</li>
 *   <li>Correlation ID propagation</li>
 *   <li>MDC context setup</li>
 *   <li>Performance metrics</li>
 *   <li>Error logging</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic correlation ID generation</li>
 *   <li>Request ID tracking</li>
 *   <li>Method name logging</li>
 *   <li>Duration tracking</li>
 *   <li>Status code logging</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * ServerBuilder.forPort(9090)
 *     .intercept(new LoggingInterceptor())
 *     .addService(orderService)
 *     .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class LoggingInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> CORRELATION_ID_KEY =
        Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> REQUEST_ID_KEY =
        Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Extract or generate correlation ID
        String correlationIdValue = headers.get(CORRELATION_ID_KEY);
        if (correlationIdValue == null || correlationIdValue.isEmpty()) {
            correlationIdValue = UUID.randomUUID().toString();
        }
        final String correlationId = correlationIdValue;

        // Extract or generate request ID
        String requestIdValue = headers.get(REQUEST_ID_KEY);
        if (requestIdValue == null || requestIdValue.isEmpty()) {
            requestIdValue = UUID.randomUUID().toString();
        }
        final String requestId = requestIdValue;

        // Setup MDC context
        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);
        MDC.put("grpcMethod", call.getMethodDescriptor().getFullMethodName());

        final long startTime = System.currentTimeMillis();

        log.info("gRPC request started: method={}, correlationId={}, requestId={}",
                call.getMethodDescriptor().getFullMethodName(), correlationId, requestId);

        // Add response headers
        Metadata responseHeaders = new Metadata();
        responseHeaders.put(CORRELATION_ID_KEY, correlationId);
        responseHeaders.put(REQUEST_ID_KEY, requestId);
        call.sendHeaders(responseHeaders);

        // Wrap the call to log completion
        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long duration = System.currentTimeMillis() - startTime;

                if (status.isOk()) {
                    log.info("gRPC request completed: method={}, status={}, duration={}ms, correlationId={}",
                            call.getMethodDescriptor().getFullMethodName(),
                            status.getCode(),
                            duration,
                            correlationId);
                } else {
                    log.error("gRPC request failed: method={}, status={}, description={}, duration={}ms, correlationId={}",
                            call.getMethodDescriptor().getFullMethodName(),
                            status.getCode(),
                            status.getDescription(),
                            duration,
                            correlationId);
                }

                // Clear MDC context
                MDC.clear();

                super.close(status, trailers);
            }
        };

        return next.startCall(wrappedCall, headers);
    }
}

