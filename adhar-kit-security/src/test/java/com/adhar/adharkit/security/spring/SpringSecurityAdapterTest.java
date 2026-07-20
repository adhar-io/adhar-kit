package com.adhar.adharkit.security.spring;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.service.TokenRefreshService;
import com.adhar.kit.security.spring.SpringSecurityAdapter;
import com.adhar.kit.security.util.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SpringSecurityAdapter}.
 */
class SpringSecurityAdapterTest {

    private static final String SECRET = "this-is-a-very-long-test-secret-key-256bits!!";

    private final SpringSecurityAdapter adapter = new SpringSecurityAdapter();

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String user, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "n/a",
                AuthorityUtils.createAuthorityList(authorities)));
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token-value")
            .header("alg", "none")
            .subject("user-42")
            .claim("preferred_username", "alice")
            .build();
    }

    private void authenticateWithJwt(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt(), AuthorityUtils.createAuthorityList(authorities)));
    }

    private TokenRefreshService tokenRefreshService() {
        AdharSecurityProperties.TokenRefreshProperties props = new AdharSecurityProperties.TokenRefreshProperties();
        props.setEnabled(true);
        props.setSecret(SECRET);
        return new TokenRefreshService(props);
    }

    // ---------------------------------------------------------------- context

    @Test
    void currentUserAccessorsReturnNullWhenUnauthenticated() {
        assertThat(adapter.getCurrentUserId()).isNull();
        assertThat(adapter.getCurrentUsername()).isNull();
        assertThat(adapter.getCurrentUserRoles()).isEmpty();
        assertThat(adapter.isAuthenticated()).isFalse();
    }

    @Test
    void anonymousAuthenticationIsTreatedAsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(adapter.isAuthenticated()).isFalse();
        assertThat(adapter.getCurrentUserId()).isNull();
        assertThat(adapter.getCurrentUserRoles()).isEmpty();
        assertThat(adapter.hasRole("ANONYMOUS")).isFalse();
    }

    @Test
    void currentUserFromStandardAuthentication() {
        authenticate("bob", "ROLE_USER", "order:read");

        assertThat(adapter.isAuthenticated()).isTrue();
        assertThat(adapter.getCurrentUserId()).isEqualTo("bob");
        assertThat(adapter.getCurrentUsername()).isEqualTo("bob");
        assertThat(adapter.getCurrentUserRoles()).containsExactlyInAnyOrder("USER", "order:read");
    }

    @Test
    void currentUserFromJwtAuthenticationToken() {
        authenticateWithJwt("ROLE_ADMIN");

        assertThat(adapter.isAuthenticated()).isTrue();
        assertThat(adapter.getCurrentUserId()).isEqualTo("user-42");
        // Without JwtUtils the subject is used as username.
        assertThat(adapter.getCurrentUsername()).isEqualTo("user-42");
        assertThat(adapter.getCurrentUserRoles()).containsExactly("ADMIN");
    }

    @Test
    void currentUsernameFromJwtUsesConfiguredClaimViaJwtUtils() {
        AdharSecurityProperties.JwtProperties jwtProps = new AdharSecurityProperties.JwtProperties();
        jwtProps.setUsernameClaimName("preferred_username");
        SpringSecurityAdapter withUtils = new SpringSecurityAdapter(null, new JwtUtils(jwtProps), null);

        authenticateWithJwt("ROLE_USER");

        assertThat(withUtils.getCurrentUsername()).isEqualTo("alice");
        assertThat(withUtils.getCurrentUserId()).isEqualTo("user-42");
    }

    @Test
    void jwtPrincipalOnNonJwtAuthenticationIsRecognized() {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(jwt(), null, "ROLE_SERVICE"));

        assertThat(adapter.getCurrentUserId()).isEqualTo("user-42");
        assertThat(adapter.getCurrentUserRoles()).containsExactly("SERVICE");
    }

    // ------------------------------------------------------------------ roles

    @Test
    void hasRoleMatchesWithAndWithoutRolePrefix() {
        authenticate("bob", "ROLE_ADMIN", "AUDITOR");

        assertThat(adapter.hasRole("ADMIN")).isTrue();
        assertThat(adapter.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(adapter.hasRole("AUDITOR")).isTrue();
        assertThat(adapter.hasRole("USER")).isFalse();
        assertThat(adapter.hasRole(null)).isFalse();
    }

    @Test
    void hasAnyRoleAndHasAllRoles() {
        authenticate("bob", "ROLE_ADMIN", "ROLE_USER");

        assertThat(adapter.hasAnyRole("MISSING", "ADMIN")).isTrue();
        assertThat(adapter.hasAnyRole("MISSING", "OTHER")).isFalse();
        assertThat(adapter.hasAnyRole((String[]) null)).isFalse();
        assertThat(adapter.hasAllRoles("ADMIN", "USER")).isTrue();
        assertThat(adapter.hasAllRoles("ADMIN", "MISSING")).isFalse();
        assertThat(adapter.hasAllRoles()).isTrue();
        assertThat(adapter.hasAllRoles((String[]) null)).isTrue();
    }

    @Test
    void roleChecksReturnFalseWhenUnauthenticated() {
        assertThat(adapter.hasRole("ADMIN")).isFalse();
        assertThat(adapter.hasAnyRole("ADMIN")).isFalse();
        assertThat(adapter.hasPermission("order:read")).isFalse();
    }

    // ------------------------------------------------------------ permissions

    @Test
    void hasPermissionMatchesExactAndScopePrefixedAuthorities() {
        authenticate("bob", "order:create", "SCOPE_order:export");

        assertThat(adapter.hasPermission("order:create")).isTrue();
        assertThat(adapter.hasPermission("order:export")).isTrue();
        assertThat(adapter.hasPermission("order:delete")).isFalse();
        assertThat(adapter.hasPermission(null)).isFalse();
    }

    // -------------------------------------------------------------- passwords

    @Test
    void encodeAndVerifyPasswordWithDefaultDelegatingEncoder() {
        String encoded = adapter.encodePassword("s3cret");

        assertThat(encoded).isNotEqualTo("s3cret");
        // Delegating encoder prefixes the algorithm id.
        assertThat(encoded).startsWith("{");
        assertThat(adapter.verifyPassword("s3cret", encoded)).isTrue();
        assertThat(adapter.verifyPassword("wrong", encoded)).isFalse();
    }

    @Test
    void verifyPasswordHandlesNulls() {
        assertThat(adapter.verifyPassword(null, "x")).isFalse();
        assertThat(adapter.verifyPassword("x", null)).isFalse();
    }

    @Test
    void customPasswordEncoderIsUsed() {
        SpringSecurityAdapter custom = new SpringSecurityAdapter(new BCryptPasswordEncoder(), null, null);

        String encoded = custom.encodePassword("pw");

        assertThat(encoded).startsWith("$2");
        assertThat(custom.verifyPassword("pw", encoded)).isTrue();
    }

    // ----------------------------------------------------------------- tokens

    @Test
    void tokenOperationsDelegateToTokenRefreshService() {
        SpringSecurityAdapter withTokens = new SpringSecurityAdapter(null, null, tokenRefreshService());

        String token = withTokens.generateToken("user-9", Set.of("ADMIN"), Map.of("email", "a@b.com"));

        assertThat(token).isNotBlank();
        assertThat(withTokens.validateToken(token)).isTrue();
        assertThat(withTokens.extractUserId(token)).isEqualTo("user-9");
        assertThat(withTokens.validateToken("garbage")).isFalse();
        assertThat(withTokens.extractUserId("garbage")).isNull();
    }

    @Test
    void generateTokenTwoArgOverloadDelegates() {
        SpringSecurityAdapter withTokens = new SpringSecurityAdapter(null, null, tokenRefreshService());

        String token = withTokens.generateToken("user-10", Set.of("USER"));

        assertThat(withTokens.extractUserId(token)).isEqualTo("user-10");
    }

    @Test
    void generateTokenWithoutTokenServiceThrows() {
        assertThatThrownBy(() -> adapter.generateToken("u", Set.of("USER")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("token-refresh");
    }

    @Test
    void validateAndExtractWithoutTokenServiceReturnDefaults() {
        assertThat(adapter.validateToken("anything")).isFalse();
        assertThat(adapter.extractUserId("anything")).isNull();
    }

    @Test
    void unauthenticatedFlagOnAuthenticationIsRespected() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "eve", "creds", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(adapter.isAuthenticated()).isFalse();
        assertThat(adapter.getCurrentUserId()).isNull();
    }
}
