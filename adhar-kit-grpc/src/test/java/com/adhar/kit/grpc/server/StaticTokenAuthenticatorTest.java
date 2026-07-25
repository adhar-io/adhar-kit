package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.util.GrpcUtils;
import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link StaticTokenAuthenticator}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class StaticTokenAuthenticatorTest {

    @Test
    void constructor_rejectsBlankSecret() {
        assertThatThrownBy(() -> new StaticTokenAuthenticator(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaticTokenAuthenticator(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authenticate_validBearerToken_succeeds() {
        StaticTokenAuthenticator authenticator = new StaticTokenAuthenticator("s3cr3t");
        Metadata headers = new Metadata();
        headers.put(GrpcUtils.AUTHORIZATION_KEY, "Bearer s3cr3t");

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(headers);

        assertThat(result.authenticated()).isTrue();
        assertThat(result.principal()).isEqualTo(StaticTokenAuthenticator.STATIC_TOKEN_PRINCIPAL);
    }

    @Test
    void authenticate_validApiKey_succeeds() {
        StaticTokenAuthenticator authenticator = new StaticTokenAuthenticator("s3cr3t");
        Metadata headers = new Metadata();
        headers.put(GrpcUtils.API_KEY_KEY, "s3cr3t");

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(headers);

        assertThat(result.authenticated()).isTrue();
    }

    @Test
    void authenticate_wrongToken_fails() {
        StaticTokenAuthenticator authenticator = new StaticTokenAuthenticator("s3cr3t");
        Metadata headers = new Metadata();
        headers.put(GrpcUtils.AUTHORIZATION_KEY, "Bearer wrong");

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(headers);

        assertThat(result.authenticated()).isFalse();
        assertThat(result.message()).isEqualTo("Invalid credentials");
    }

    @Test
    void authenticate_missingCredentials_fails() {
        StaticTokenAuthenticator authenticator = new StaticTokenAuthenticator("s3cr3t");

        GrpcAuthenticator.AuthResult result = authenticator.authenticate(new Metadata());

        assertThat(result.authenticated()).isFalse();
        assertThat(result.message()).isEqualTo("Missing Bearer token or API key");
    }
}
