package com.adhar.kit.grpc.server;

import io.grpc.Metadata;

/**
 * Service Provider Interface for authenticating inbound gRPC calls.
 *
 * <p>Implementations inspect the call's {@link Metadata} (headers) and decide
 * whether the call is authenticated, optionally returning a principal
 * identifier that is stored in the gRPC {@link io.grpc.Context} for the
 * duration of the call.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface GrpcAuthenticator {

    /**
     * Authenticates a call based on its metadata.
     *
     * @param headers request metadata
     * @return the authentication result
     */
    AuthResult authenticate(Metadata headers);

    /**
     * Result of an authentication attempt.
     *
     * @param authenticated whether the call is authenticated
     * @param principal     the authenticated principal identifier, or {@code null} on failure
     * @param message       a failure message, or {@code null} on success
     */
    record AuthResult(boolean authenticated, String principal, String message) {

        /**
         * Creates a successful result.
         *
         * @param principal authenticated principal identifier
         * @return a successful {@link AuthResult}
         */
        public static AuthResult success(String principal) {
            return new AuthResult(true, principal, null);
        }

        /**
         * Creates a failed result.
         *
         * @param message reason the authentication failed
         * @return a failed {@link AuthResult}
         */
        public static AuthResult failure(String message) {
            return new AuthResult(false, null, message);
        }
    }
}
