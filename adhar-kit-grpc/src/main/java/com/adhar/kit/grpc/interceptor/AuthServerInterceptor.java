package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.server.GrpcAuthenticator;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC server interceptor that authenticates every inbound call using a
 * {@link GrpcAuthenticator}.
 *
 * <p>On success, the authenticated principal is stored in the gRPC
 * {@link Context} under {@link #PRINCIPAL_CONTEXT_KEY} for the duration of the
 * call, retrievable from service implementations via
 * {@code PRINCIPAL_CONTEXT_KEY.get()}. On failure, the call is closed with
 * {@link Status#UNAUTHENTICATED} before it reaches the service.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AuthServerInterceptor implements ServerInterceptor {

    /**
     * Context key under which the authenticated principal is stored.
     */
    public static final Context.Key<String> PRINCIPAL_CONTEXT_KEY = Context.key("adhar-grpc-principal");

    private final GrpcAuthenticator authenticator;

    /**
     * Creates the interceptor.
     *
     * @param authenticator authenticator used to validate each call
     */
    public AuthServerInterceptor(GrpcAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(headers);

        if (!result.authenticated()) {
            String description = result.message() != null ? result.message() : "Authentication failed";
            log.warn("gRPC call rejected: method={}, reason={}",
                    call.getMethodDescriptor().getFullMethodName(), description);
            call.close(Status.UNAUTHENTICATED.withDescription(description), new Metadata());
            return new ServerCall.Listener<>() {
                // No-op listener: the call has already been closed above.
            };
        }

        Context context = Context.current().withValue(PRINCIPAL_CONTEXT_KEY, result.principal());
        return Contexts.interceptCall(context, call, headers, next);
    }
}
