package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.util.GrpcUtils;
import io.grpc.Metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@link GrpcAuthenticator} that validates a shared-secret token supplied
 * either as {@code Authorization: Bearer <token>} or {@code x-api-key: <token>}.
 *
 * <p>Comparison uses {@link MessageDigest#isEqual(byte[], byte[])} which runs
 * in constant time, avoiding a timing side-channel on the secret.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class StaticTokenAuthenticator implements GrpcAuthenticator {

    /**
     * Principal assigned to successfully authenticated calls.
     */
    public static final String STATIC_TOKEN_PRINCIPAL = "static-token-client";

    private final String sharedSecret;

    /**
     * Creates the authenticator with the given shared secret.
     *
     * @param sharedSecret the expected token value; must not be blank
     */
    public StaticTokenAuthenticator(String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalArgumentException("sharedSecret must not be blank");
        }
        this.sharedSecret = sharedSecret;
    }

    @Override
    public AuthResult authenticate(Metadata headers) {
        String token = GrpcUtils.extractCredentialToken(headers);
        if (token == null || token.isBlank()) {
            return AuthResult.failure("Missing Bearer token or API key");
        }

        byte[] provided = token.getBytes(StandardCharsets.UTF_8);
        byte[] expected = sharedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(provided, expected)) {
            return AuthResult.failure("Invalid credentials");
        }

        return AuthResult.success(STATIC_TOKEN_PRINCIPAL);
    }
}
