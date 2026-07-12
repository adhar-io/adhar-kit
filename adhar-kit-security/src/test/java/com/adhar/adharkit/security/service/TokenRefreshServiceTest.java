package com.adhar.adharkit.security.service;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.service.TokenRefreshService;
import com.adhar.kit.security.service.TokenRefreshService.TokenRefreshException;
import com.adhar.kit.security.service.TokenRefreshService.TokenResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TokenRefreshService}.
 */
class TokenRefreshServiceTest {

    private static final String SECRET = "this-is-a-very-long-test-secret-key-256bits!!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private AdharSecurityProperties.TokenRefreshProperties config(boolean enabled, boolean rotate) {
        AdharSecurityProperties.TokenRefreshProperties props = new AdharSecurityProperties.TokenRefreshProperties();
        props.setEnabled(enabled);
        props.setRotateRefreshTokens(rotate);
        props.setSecret(SECRET);
        props.setAccessTokenValiditySeconds(900);
        props.setRefreshTokenValiditySeconds(604800);
        return props;
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private String manualToken(String subject, String type, String family, long expiresInSeconds, Map<String, Object> extra) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
            .claim("type", type);
        if (family != null) {
            builder.claim("family", family);
        }
        if (extra != null) {
            extra.forEach(builder::claim);
        }
        return builder.signWith(key).compact();
    }

    @Test
    void createTokenPairProducesValidTokens() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));

        TokenResponse pair = service.createTokenPair("user-1", Map.of("roles", List.of("ADMIN")));

        assertThat(pair.tokenType()).isEqualTo("Bearer");
        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.accessTokenExpiresIn()).isEqualTo(900);
        assertThat(pair.refreshTokenExpiresIn()).isEqualTo(604800);

        Claims access = parse(pair.accessToken());
        assertThat(access.getSubject()).isEqualTo("user-1");
        assertThat(access.get("type", String.class)).isEqualTo("access");
        assertThat(access.get("roles", List.class)).containsExactly("ADMIN");

        Claims refresh = parse(pair.refreshToken());
        assertThat(refresh.get("type", String.class)).isEqualTo("refresh");
        assertThat(refresh.get("family", String.class)).isNotNull();
    }

    @Test
    void createTokenPairWithNullClaimsWorks() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));

        TokenResponse pair = service.createTokenPair("user-null", null);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(parse(pair.accessToken()).getSubject()).isEqualTo("user-null");
    }

    @Test
    void createTokenPairThrowsWhenDisabled() {
        TokenRefreshService service = new TokenRefreshService(config(false, true));

        assertThatThrownBy(() -> service.createTokenPair("u", Map.of()))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("disabled");
    }

    @Test
    void refreshThrowsWhenDisabled() {
        TokenRefreshService service = new TokenRefreshService(config(false, true));

        assertThatThrownBy(() -> service.refreshAccessToken("anything"))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("disabled");
    }

    @Test
    void refreshWithRotationIssuesNewRefreshToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        TokenResponse pair = service.createTokenPair("user-2", Map.of());

        TokenResponse refreshed = service.refreshAccessToken(pair.refreshToken());

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(pair.refreshToken());
        assertThat(parse(refreshed.accessToken()).getSubject()).isEqualTo("user-2");
        assertThat(refreshed.refreshTokenExpiresIn()).isPositive();
    }

    @Test
    void refreshWithoutRotationKeepsSameRefreshToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, false));
        TokenResponse pair = service.createTokenPair("user-3", Map.of());

        TokenResponse refreshed = service.refreshAccessToken(pair.refreshToken());

        assertThat(refreshed.refreshToken()).isEqualTo(pair.refreshToken());
        assertThat(refreshed.refreshTokenExpiresIn()).isEqualTo(604800);
    }

    @Test
    void refreshCopiesRelevantClaimsIntoAccessToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        // A refresh token carrying user claims so the copy branches execute.
        Map<String, Object> extra = new HashMap<>();
        extra.put("roles", List.of("USER"));
        extra.put("authorities", List.of("READ"));
        extra.put("email", "a@b.com");
        extra.put("name", "Alice");
        String refreshToken = manualToken("user-4", "refresh", "fam-x", 3600, extra);

        TokenResponse refreshed = service.refreshAccessToken(refreshToken);

        Claims access = parse(refreshed.accessToken());
        assertThat(access.get("roles", List.class)).containsExactly("USER");
        assertThat(access.get("authorities", List.class)).containsExactly("READ");
        assertThat(access.get("email", String.class)).isEqualTo("a@b.com");
        assertThat(access.get("name", String.class)).isEqualTo("Alice");
    }

    @Test
    void refreshWithFamilylessTokenRotatesIntoNewFamily() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        String refreshToken = manualToken("user-5", "refresh", null, 3600, null);

        TokenResponse refreshed = service.refreshAccessToken(refreshToken);

        assertThat(refreshed.refreshToken()).isNotEqualTo(refreshToken);
        assertThat(parse(refreshed.refreshToken()).get("family", String.class)).isNotNull();
    }

    @Test
    void refreshRejectsWrongTokenType() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        String accessTyped = manualToken("user-6", "access", "fam", 3600, null);

        assertThatThrownBy(() -> service.refreshAccessToken(accessTyped))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("Invalid token type");
    }

    @Test
    void refreshRejectsMalformedToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));

        assertThatThrownBy(() -> service.refreshAccessToken("not-a-valid-jwt"))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void refreshRejectsRevokedToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        TokenResponse pair = service.createTokenPair("user-7", Map.of());

        service.revokeRefreshToken(pair.refreshToken());

        assertThatThrownBy(() -> service.refreshAccessToken(pair.refreshToken()))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("revoked");
    }

    @Test
    void detectsRefreshTokenReuseAndRevokesFamily() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        TokenResponse pair = service.createTokenPair("user-8", Map.of());
        String original = pair.refreshToken();

        // First use rotates the token successfully.
        TokenResponse rotated = service.refreshAccessToken(original);

        // Reusing the now-rotated original token signals theft -> whole family revoked.
        assertThatThrownBy(() -> service.refreshAccessToken(original))
            .isInstanceOf(TokenRefreshException.class)
            .hasMessageContaining("invalidated");

        // The legitimately rotated token is now revoked too.
        assertThatThrownBy(() -> service.refreshAccessToken(rotated.refreshToken()))
            .isInstanceOf(TokenRefreshException.class);
    }

    @Test
    void revokeRefreshTokenToleratesUnparseableToken() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));

        // Should not throw even though the token cannot be parsed.
        service.revokeRefreshToken("garbage");

        assertThatThrownBy(() -> service.refreshAccessToken("garbage"))
            .isInstanceOf(TokenRefreshException.class);
    }

    @Test
    void revokeAllUserTokensInvalidatesRefreshTokens() {
        TokenRefreshService service = new TokenRefreshService(config(true, true));
        TokenResponse pair = service.createTokenPair("user-9", Map.of());

        service.revokeAllUserTokens("user-9");

        assertThatThrownBy(() -> service.refreshAccessToken(pair.refreshToken()))
            .isInstanceOf(TokenRefreshException.class);
    }

    @Test
    void constructorGeneratesKeyWhenSecretMissing() {
        AdharSecurityProperties.TokenRefreshProperties props = new AdharSecurityProperties.TokenRefreshProperties();
        props.setEnabled(true);
        props.setSecret(null); // triggers generated-secret branch

        TokenRefreshService service = new TokenRefreshService(props);

        // Service still functions with the generated key.
        TokenResponse pair = service.createTokenPair("user-10", Map.of());
        assertThat(pair.accessToken()).isNotBlank();
        TokenResponse refreshed = service.refreshAccessToken(pair.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
    }

    @Test
    void tokenResponseRecordExposesAllComponents() {
        TokenResponse response = new TokenResponse("a", "r", 1L, 2L, "Bearer");
        assertThat(response.accessToken()).isEqualTo("a");
        assertThat(response.refreshToken()).isEqualTo("r");
        assertThat(response.accessTokenExpiresIn()).isEqualTo(1L);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(2L);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
