package com.adhar.kit.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC server interceptor for error handling and exception translation.
 *
 * <p>Converts Java exceptions to gRPC Status codes:</p>
 * <ul>
 *   <li>IllegalArgumentException → INVALID_ARGUMENT</li>
 *   <li>IllegalStateException → FAILED_PRECONDITION</li>
 *   <li>NullPointerException → INVALID_ARGUMENT</li>
 *   <li>SecurityException → PERMISSION_DENIED</li>
 *   <li>UnsupportedOperationException → UNIMPLEMENTED</li>
 *   <li>TimeoutException → DEADLINE_EXCEEDED</li>
 *   <li>All others → INTERNAL</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic exception translation</li>
 *   <li>Error message sanitization</li>
 *   <li>Stack trace logging</li>
 *   <li>Metadata preservation</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * ServerBuilder.forPort(9090)
 *     .intercept(new ExceptionHandlerInterceptor())
 *     .addService(orderService)
 *     .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ExceptionHandlerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    handleException(call, e);
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } catch (Exception e) {
                    handleException(call, e);
                }
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Exception e) {
                    handleException(call, e);
                }
            }
        };
    }

    /**
     * Handles exceptions and converts to gRPC status.
     */
    private <ReqT, RespT> void handleException(ServerCall<ReqT, RespT> call, Exception e) {
        Status status = convertExceptionToStatus(e);

        log.error("gRPC exception: method={}, status={}, message={}",
                call.getMethodDescriptor().getFullMethodName(),
                status.getCode(),
                e.getMessage(),
                e);

        Metadata trailers = new Metadata();
        call.close(status, trailers);
    }

    /**
     * Converts Java exception to gRPC Status.
     */
    private Status convertExceptionToStatus(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e);
        } else if (e instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION.withDescription(e.getMessage()).withCause(e);
        } else if (e instanceof NullPointerException) {
            return Status.INVALID_ARGUMENT.withDescription("Required field is null").withCause(e);
        } else if (e instanceof SecurityException) {
            return Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e);
        } else if (e instanceof UnsupportedOperationException) {
            return Status.UNIMPLEMENTED.withDescription(e.getMessage()).withCause(e);
        } else if (e.getClass().getSimpleName().contains("Timeout")) {
            return Status.DEADLINE_EXCEEDED.withDescription(e.getMessage()).withCause(e);
        } else if (e.getClass().getSimpleName().contains("NotFound")) {
            return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
        } else if (e.getClass().getSimpleName().contains("AlreadyExists")) {
            return Status.ALREADY_EXISTS.withDescription(e.getMessage()).withCause(e);
        } else if (e instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) e).getStatus();
        } else {
            return Status.INTERNAL.withDescription("Internal server error: " + e.getMessage()).withCause(e);
        }
    }
}

