package com.adhar.kit.grpc.server;

import io.grpc.Metadata;

/**
 * Default {@link GrpcAuthenticator} that permits every call.
 *
 * <p>Used when authentication is disabled ({@code adhar.grpc.auth.enabled=false}),
 * which is the default, so existing deployments keep working unchanged.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class PermitAllGrpcAuthenticator implements GrpcAuthenticator {

    /**
     * Principal assigned to every call since no real authentication occurs.
     */
    public static final String ANONYMOUS_PRINCIPAL = "anonymous";

    @Override
    public AuthResult authenticate(Metadata headers) {
        return AuthResult.success(ANONYMOUS_PRINCIPAL);
    }
}
