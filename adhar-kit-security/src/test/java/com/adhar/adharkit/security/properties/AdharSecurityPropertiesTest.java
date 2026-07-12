package com.adhar.adharkit.security.properties;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AdharSecurityProperties}.
 */
@SpringBootTest(classes = {AdharSecurityPropertiesTest.TestConfig.class})
@TestPropertySource(properties = {
    "adhar.security.enabled=true",
    "adhar.security.jwt.enabled=true",
    "adhar.security.jwt.issuer-uri=https://example.com/issuer",
    "adhar.security.jwt.jwk-set-uri=https://example.com/jwks",
    "adhar.security.jwt.audience[0]=audience1",
    "adhar.security.jwt.audience[1]=audience2",
    "adhar.security.jwt.authorities-claim-name=roles",
    "adhar.security.jwt.username-claim-name=preferred_username",
    "adhar.security.jwt.name-claim-name=full_name",
    "adhar.security.jwt.email-claim-name=email_address",
    "adhar.security.oauth2.enabled=true",
    "adhar.security.oauth2.client-id=client-id",
    "adhar.security.oauth2.client-secret=client-secret",
    "adhar.security.oauth2.authorization-uri=https://example.com/authorize",
    "adhar.security.oauth2.token-uri=https://example.com/token",
    "adhar.security.oauth2.user-info-uri=https://example.com/userinfo",
    "adhar.security.oauth2.redirect-uri=https://example.com/callback",
    "adhar.security.oauth2.scopes[0]=openid",
    "adhar.security.oauth2.scopes[1]=profile",
    "adhar.security.oauth2.scopes[2]=email",
    "adhar.security.cors.enabled=true",
    "adhar.security.cors.allowed-origins[0]=https://example.com",
    "adhar.security.cors.allowed-origins[1]=https://api.example.com",
    "adhar.security.cors.allowed-methods[0]=GET",
    "adhar.security.cors.allowed-methods[1]=POST",
    "adhar.security.cors.allowed-methods[2]=PUT",
    "adhar.security.cors.allowed-methods[3]=DELETE",
    "adhar.security.cors.allowed-headers[0]=Authorization",
    "adhar.security.cors.allowed-headers[1]=Content-Type",
    "adhar.security.cors.exposed-headers[0]=X-Custom-Header",
    "adhar.security.cors.allow-credentials=true",
    "adhar.security.cors.max-age=3600",
    "adhar.security.authorization.enabled=true",
    "adhar.security.authorization.permit-all[0]=/public/**",
    "adhar.security.authorization.permit-all[1]=/actuator/health",
    "adhar.security.authorization.authenticated[0]=/api/**",
    "adhar.security.authorization.authorities[/admin/**][0]=ROLE_ADMIN"
})
public class AdharSecurityPropertiesTest {

    @Autowired
    private AdharSecurityProperties properties;

    /**
     * Minimal context that enables binding of {@link AdharSecurityProperties}
     * from the configured {@link TestPropertySource}.
     */
    @Configuration
    @EnableConfigurationProperties(AdharSecurityProperties.class)
    static class TestConfig {
    }

    @Test
    public void testDefaultProperties() {
        assertNotNull(properties);
        assertTrue(properties.isEnabled());
    }

    @Test
    public void testJwtProperties() {
        assertTrue(properties.getJwt().isEnabled());
        assertEquals("https://example.com/issuer", properties.getJwt().getIssuerUri());
        assertEquals("https://example.com/jwks", properties.getJwt().getJwkSetUri());
        assertEquals(2, properties.getJwt().getAudience().size());
        assertTrue(properties.getJwt().getAudience().contains("audience1"));
        assertTrue(properties.getJwt().getAudience().contains("audience2"));
        assertEquals("roles", properties.getJwt().getAuthoritiesClaimName());
        assertEquals("preferred_username", properties.getJwt().getUsernameClaimName());
        assertEquals("full_name", properties.getJwt().getNameClaimName());
        assertEquals("email_address", properties.getJwt().getEmailClaimName());
    }

    @Test
    public void testOAuth2Properties() {
        assertTrue(properties.getOauth2().isEnabled());
        assertEquals("client-id", properties.getOauth2().getClientId());
        assertEquals("client-secret", properties.getOauth2().getClientSecret());
        assertEquals("https://example.com/authorize", properties.getOauth2().getAuthorizationUri());
        assertEquals("https://example.com/token", properties.getOauth2().getTokenUri());
        assertEquals("https://example.com/userinfo", properties.getOauth2().getUserInfoUri());
        assertEquals("https://example.com/callback", properties.getOauth2().getRedirectUri());
        assertEquals(3, properties.getOauth2().getScopes().size());
        assertTrue(properties.getOauth2().getScopes().contains("openid"));
        assertTrue(properties.getOauth2().getScopes().contains("profile"));
        assertTrue(properties.getOauth2().getScopes().contains("email"));
    }

    @Test
    public void testCorsProperties() {
        assertTrue(properties.getCors().isEnabled());
        assertEquals(2, properties.getCors().getAllowedOrigins().size());
        assertTrue(properties.getCors().getAllowedOrigins().contains("https://example.com"));
        assertTrue(properties.getCors().getAllowedOrigins().contains("https://api.example.com"));
        assertEquals(4, properties.getCors().getAllowedMethods().size());
        assertTrue(properties.getCors().getAllowedMethods().contains("GET"));
        assertTrue(properties.getCors().getAllowedMethods().contains("POST"));
        assertTrue(properties.getCors().getAllowedMethods().contains("PUT"));
        assertTrue(properties.getCors().getAllowedMethods().contains("DELETE"));
        assertEquals(2, properties.getCors().getAllowedHeaders().size());
        assertTrue(properties.getCors().getAllowedHeaders().contains("Authorization"));
        assertTrue(properties.getCors().getAllowedHeaders().contains("Content-Type"));
        assertEquals(1, properties.getCors().getExposedHeaders().size());
        assertTrue(properties.getCors().getExposedHeaders().contains("X-Custom-Header"));
        assertTrue(properties.getCors().isAllowCredentials());
        assertEquals(3600, properties.getCors().getMaxAge());
    }

    @Test
    public void testAuthorizationProperties() {
        assertTrue(properties.getAuthorization().isEnabled());
        assertEquals(2, properties.getAuthorization().getPermitAll().size());
        assertTrue(properties.getAuthorization().getPermitAll().contains("/public/**"));
        assertTrue(properties.getAuthorization().getPermitAll().contains("/actuator/health"));
        assertEquals(1, properties.getAuthorization().getAuthenticated().size());
        assertTrue(properties.getAuthorization().getAuthenticated().contains("/api/**"));
        assertEquals(1, properties.getAuthorization().getAuthorities().size());
        assertTrue(properties.getAuthorization().getAuthorities().containsKey("/admin/**"));
        assertEquals(1, properties.getAuthorization().getAuthorities().get("/admin/**").size());
        assertTrue(properties.getAuthorization().getAuthorities().get("/admin/**").contains("ROLE_ADMIN"));
    }
}