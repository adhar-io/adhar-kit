package com.adhar.kit.grpc.server;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PermitAllGrpcAuthenticator}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class PermitAllGrpcAuthenticatorTest {

    @Test
    void authenticate_alwaysSucceeds() {
        PermitAllGrpcAuthenticator authenticator = new PermitAllGrpcAuthenticator();

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(new Metadata());

        assertThat(result.authenticated()).isTrue();
        assertThat(result.principal()).isEqualTo(PermitAllGrpcAuthenticator.ANONYMOUS_PRINCIPAL);
        assertThat(result.message()).isNull();
    }
}
