package com.adhar.kit.security.config;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration for OAuth2 Resource Server.
 * <p>
 * This class provides configuration for OAuth2 Resource Server, allowing secure access to protected resources
 * using JWT tokens for authentication and authorization.
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "adhar.security.oauth2", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(OnJwtConfiguredCondition.class)
@Slf4j
public class OAuth2ResourceServerConfig {

    private final AdharSecurityProperties properties;
    private final JwtUtils jwtUtils;

    /**
     * Constructor for OAuth2ResourceServerConfig.
     *
     * @param properties the security properties
     * @param jwtUtils   the JWT utilities
     */
    public OAuth2ResourceServerConfig(AdharSecurityProperties properties, JwtUtils jwtUtils) {
        this.properties = properties;
        this.jwtUtils = jwtUtils;
        log.info("Initializing OAuth2 Resource Server Configuration");
    }

    /**
     * Configures the JWT decoder.
     *
     * @return the configured JwtDecoder
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        if (properties.getJwt().getJwkSetUri() != null && !properties.getJwt().getJwkSetUri().isEmpty()) {
            log.debug("Configuring JWT decoder with JWK set URI: {}", properties.getJwt().getJwkSetUri());
            return NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwkSetUri()).build();
        } else if (properties.getJwt().getIssuerUri() != null && !properties.getJwt().getIssuerUri().isEmpty()) {
            log.debug("Configuring JWT decoder with issuer URI: {}", properties.getJwt().getIssuerUri());
            return NimbusJwtDecoder.withIssuerLocation(properties.getJwt().getIssuerUri()).build();
        } else {
            log.warn("No JWK set URI or issuer URI configured for JWT decoder");
            throw new IllegalStateException("Either jwkSetUri or issuerUri must be configured for JWT validation");
        }
    }

    /**
     * Configures the JWT authentication converter.
     *
     * @return the configured JWT authentication converter
     */
    @Bean
    @ConditionalOnMissingBean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        log.debug("Configuring JWT authentication converter");
        
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName(properties.getJwt().getAuthoritiesClaimName());
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        
        return jwtAuthenticationConverter;
    }

    /**
     * Configures the security filter chain for OAuth2 resource server.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    @ConditionalOnMissingBean(name = "oauth2SecurityFilterChain")
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring OAuth2 resource server security filter chain");
        
        // Configure session management
        http.sessionManagement(sessionManagement -> 
            sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        
        // Configure OAuth2 resource server
        http.oauth2ResourceServer(oauth2 -> 
            oauth2.jwt(jwt -> 
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        );
        
        return http.build();
    }
}