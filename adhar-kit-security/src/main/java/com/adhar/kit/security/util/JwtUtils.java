package com.adhar.kit.security.util;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for JWT token handling.
 */
@Slf4j
public class JwtUtils {

    private final AdharSecurityProperties.JwtProperties jwtProperties;

    /**
     * Constructor for JwtUtils.
     *
     * @param jwtProperties the JWT properties
     */
    public JwtUtils(AdharSecurityProperties.JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Extracts the username from the JWT token.
     *
     * @param jwt the JWT token
     * @return the username
     */
    public String extractUsername(Jwt jwt) {
        return jwt.getClaimAsString(jwtProperties.getUsernameClaimName());
    }

    /**
     * Extracts the name from the JWT token.
     *
     * @param jwt the JWT token
     * @return the name
     */
    public String extractName(Jwt jwt) {
        return jwt.getClaimAsString(jwtProperties.getNameClaimName());
    }

    /**
     * Extracts the email from the JWT token.
     *
     * @param jwt the JWT token
     * @return the email
     */
    public String extractEmail(Jwt jwt) {
        return jwt.getClaimAsString(jwtProperties.getEmailClaimName());
    }

    /**
     * Extracts the authorities from the JWT token.
     *
     * @param jwt the JWT token
     * @return the authorities
     */
    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Extract authorities from the authorities claim
        Object authoritiesClaim = jwt.getClaim(jwtProperties.getAuthoritiesClaimName());
        if (authoritiesClaim instanceof Collection) {
            authorities.addAll(
                ((Collection<?>) authoritiesClaim).stream()
                    .map(Object::toString)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList())
            );
        } else if (authoritiesClaim instanceof String) {
            authorities.add(new SimpleGrantedAuthority(authoritiesClaim.toString()));
        }

        return authorities;
    }

    /**
     * Extracts additional claims from the JWT token.
     *
     * @param jwt the JWT token
     * @return the additional claims
     */
    public Map<String, Object> extractAdditionalClaims(Jwt jwt) {
        return jwtProperties.getAdditionalClaims().stream()
            .filter(jwt::hasClaim)
            .collect(Collectors.toMap(
                claimName -> claimName,
                jwt::getClaim
            ));
    }

    /**
     * Extracts the JWT token from the authentication.
     *
     * @param authentication the authentication
     * @return the JWT token
     */
    public Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) authentication).getToken();
        }
        return null;
    }

    /**
     * Validates the audience in the JWT token.
     *
     * @param jwt the JWT token
     * @return true if the audience is valid, false otherwise
     */
    public boolean validateAudience(Jwt jwt) {
        if (jwtProperties.getAudience().isEmpty()) {
            return true;
        }

        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            return false;
        }

        return audiences.stream().anyMatch(jwtProperties.getAudience()::contains);
    }
}