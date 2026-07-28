package com.adhar.adharkit.security.jwks;

import com.adhar.kit.security.jwks.JwksKeyManager;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JwksKeyManager}.
 */
class JwksKeyManagerTest {

    private JWTClaimsSet claims(String subject) {
        return new JWTClaimsSet.Builder()
            .subject(subject)
            .expirationTime(new Date(System.currentTimeMillis() + 60_000))
            .build();
    }

    @Test
    void signsAndVerifiesToken() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);

        String token = manager.sign(claims("alice"));

        assertThat(manager.verify(token)).isTrue();
        SignedJWT parsed = manager.parseAndVerify(token);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(manager.getCurrentKid());
    }

    @Test
    void publicJwkSetContainsOnlyPublicKeys() throws Exception {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);

        JWKSet jwkSet = manager.getPublicJwkSet();

        assertThat(jwkSet.getKeys()).hasSize(1);
        assertThat(jwkSet.getKeys().get(0).isPrivate()).isFalse();
        // Serialized form must not leak private parameters.
        assertThat(jwkSet.toString()).doesNotContain("\"d\"");
    }

    @Test
    void rotationRetainsPreviousKeyWithinWindow() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);
        String oldToken = manager.sign(claims("bob"));
        String oldKid = manager.getCurrentKid();

        String newKid = manager.rotate();

        assertThat(newKid).isNotEqualTo(oldKid);
        // New signing key is current.
        String newToken = manager.sign(claims("bob"));
        assertThat(manager.verify(newToken)).isTrue();
        // Previously-issued token still verifies against the retained key.
        assertThat(manager.verify(oldToken)).isTrue();
        // JWKS now publishes both keys.
        assertThat(manager.getPublicJwkSet().getKeys()).hasSize(2);
    }

    @Test
    void rotationWithoutRetentionInvalidatesOldToken() {
        JwksKeyManager manager = new JwksKeyManager(2048, 0);
        String oldToken = manager.sign(claims("carol"));

        manager.rotate();

        assertThat(manager.verify(oldToken)).isFalse();
        assertThat(manager.getPublicJwkSet().getKeys()).hasSize(1);
    }

    @Test
    void expiredTokenFailsVerification() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);
        JWTClaimsSet expired = new JWTClaimsSet.Builder()
            .subject("dave")
            .expirationTime(new Date(System.currentTimeMillis() - 1_000))
            .build();

        String token = manager.sign(expired);

        assertThat(manager.verify(token)).isFalse();
        assertThatThrownBy(() -> manager.parseAndVerify(token))
            .isInstanceOf(JwksKeyManager.JwtVerificationException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void tokenWithUnknownKidFailsVerification() {
        JwksKeyManager signer = new JwksKeyManager(2048, 0);
        String token = signer.sign(claims("erin"));

        // A different manager has no matching kid.
        JwksKeyManager other = new JwksKeyManager(2048, 0);

        assertThat(other.verify(token)).isFalse();
        assertThatThrownBy(() -> other.parseAndVerify(token))
            .isInstanceOf(JwksKeyManager.JwtVerificationException.class)
            .hasMessageContaining("kid");
    }

    @Test
    void malformedTokenFailsVerification() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);

        assertThat(manager.verify("not-a-jwt")).isFalse();
        assertThat(manager.verify(null)).isFalse();
        assertThat(manager.verify("  ")).isFalse();
        assertThatThrownBy(() -> manager.parseAndVerify(""))
            .isInstanceOf(JwksKeyManager.JwtVerificationException.class);
    }

    @Test
    void defaultConstructorProducesUsableManager() {
        JwksKeyManager manager = new JwksKeyManager();
        String token = manager.sign(claims("frank"));

        assertThat(manager.getCurrentKid()).isNotBlank();
        assertThat(manager.getKeys()).hasSize(1);
        assertThat(manager.verify(token)).isTrue();
    }
}
