package com.adhar.adharkit.security.util;

import com.adhar.adharkit.security.properties.AdharSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JwtUtils}.
 */
public class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private AdharSecurityProperties.JwtProperties jwtProperties;
    private Jwt jwt;

    @BeforeEach
    public void setUp() {
        // Setup JWT properties
        jwtProperties = new AdharSecurityProperties().getJwt();
        jwtProperties.setAuthoritiesClaimName("roles");
        jwtProperties.setUsernameClaimName("preferred_username");
        jwtProperties.setNameClaimName("name");
        jwtProperties.setEmailClaimName("email");
        jwtProperties.setAudience(Arrays.asList("audience1", "audience2"));
        jwtProperties.setAdditionalClaims(Arrays.asList("custom_claim1", "custom_claim2"));

        // Create JwtUtils
        jwtUtils = new JwtUtils(jwtProperties);

        // Mock JWT
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("preferred_username", "testuser");
        claims.put("name", "Test User");
        claims.put("email", "testuser@example.com");
        claims.put("roles", Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        claims.put("aud", Arrays.asList("audience1", "audience3"));
        claims.put("custom_claim1", "custom_value1");
        claims.put("custom_claim2", "custom_value2");

        jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);
    }

    @Test
    public void testExtractUsername() {
        assertEquals("testuser", jwtUtils.extractUsername(jwt));
    }

    @Test
    public void testExtractName() {
        assertEquals("Test User", jwtUtils.extractName(jwt));
    }

    @Test
    public void testExtractEmail() {
        assertEquals("testuser@example.com", jwtUtils.extractEmail(jwt));
    }

    @Test
    public void testExtractAuthorities() {
        Collection<GrantedAuthority> authorities = jwtUtils.extractAuthorities(jwt);
        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    public void testExtractAuthoritiesWithStringClaim() {
        // Mock JWT with string claim
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", "ROLE_USER");

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);

        Collection<GrantedAuthority> authorities = jwtUtils.extractAuthorities(jwt);
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    public void testExtractAdditionalClaims() {
        Map<String, Object> additionalClaims = jwtUtils.extractAdditionalClaims(jwt);
        assertEquals(2, additionalClaims.size());
        assertEquals("custom_value1", additionalClaims.get("custom_claim1"));
        assertEquals("custom_value2", additionalClaims.get("custom_claim2"));
    }

    @Test
    public void testExtractJwt() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, Collections.emptyList());
        assertEquals(jwt, jwtUtils.extractJwt(authentication));
    }

    @Test
    public void testExtractJwtWithNonJwtAuthentication() {
        Authentication authentication = mock(Authentication.class);
        assertNull(jwtUtils.extractJwt(authentication));
    }

    @Test
    public void testValidateAudience() {
        assertTrue(jwtUtils.validateAudience(jwt));
    }

    @Test
    public void testValidateAudienceWithNoMatch() {
        // Mock JWT with non-matching audience
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("aud", Arrays.asList("audience3", "audience4"));

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);

        assertFalse(jwtUtils.validateAudience(jwt));
    }

    @Test
    public void testValidateAudienceWithNoAudience() {
        // Mock JWT with no audience
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);

        assertFalse(jwtUtils.validateAudience(jwt));
    }

    @Test
    public void testValidateAudienceWithNoRequiredAudience() {
        // Setup JWT properties with no required audience
        jwtProperties.setAudience(Collections.emptyList());

        // Mock JWT with audience
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("aud", Arrays.asList("audience1", "audience2"));

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims);

        assertTrue(jwtUtils.validateAudience(jwt));
    }
}