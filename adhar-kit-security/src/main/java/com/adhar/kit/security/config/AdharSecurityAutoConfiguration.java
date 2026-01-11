package com.adhar.kit.security.config;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Auto-configuration for Adhar Security Starter.
 * <p>
 * This class provides auto-configuration for security features including OAuth2/OpenID Connect,
 * JWT token validation, CORS, and other security-related settings for enterprise applications.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AdharSecurityProperties.class)
@ConditionalOnProperty(prefix = "adhar.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Import({
    OAuth2ResourceServerConfig.class,
    CorsConfig.class
})
public class AdharSecurityAutoConfiguration {

    private final AdharSecurityProperties properties;

    /**
     * Constructor for AdharSecurityAutoConfiguration.
     *
     * @param properties the security properties
     */
    public AdharSecurityAutoConfiguration(AdharSecurityProperties properties) {
        this.properties = properties;
        log.info("Initializing Adhar Security Auto Configuration");
    }

    /**
     * Configures the security filter chain.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring security filter chain");

        // Configure session management
        http.sessionManagement(sessionManagement -> 
            sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // Configure authorization
        if (properties.getAuthorization().isEnabled()) {
            http.authorizeHttpRequests(authorize -> {
                // Configure permitAll URLs
                if (!properties.getAuthorization().getPermitAll().isEmpty()) {
                    String[] permitAllUrls = properties.getAuthorization().getPermitAll().toArray(new String[0]);
                    authorize.requestMatchers(permitAllUrls).permitAll();
                }

                // Configure authenticated URLs
                if (!properties.getAuthorization().getAuthenticated().isEmpty()) {
                    String[] authenticatedUrls = properties.getAuthorization().getAuthenticated().toArray(new String[0]);
                    authorize.requestMatchers(authenticatedUrls).authenticated();
                }

                // Configure URLs with specific authorities
                properties.getAuthorization().getAuthorities().forEach((url, authorities) -> {
                    String[] authoritiesArray = authorities.toArray(new String[0]);
                    authorize.requestMatchers(url).hasAnyAuthority(authoritiesArray);
                });

                // Default: require authentication for all requests
                authorize.anyRequest().authenticated();
            });
        }

        // Configure CORS
        if (properties.getCors().isEnabled()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        }

        // Configure CSRF
        if (properties.getCsrf().isEnabled()) {
            http.csrf(csrf -> {
                if (!properties.getCsrf().getIgnoreAntMatchers().isEmpty()) {
                    csrf.ignoringRequestMatchers(properties.getCsrf().getIgnoreAntMatchers().toArray(new String[0]));
                }

                if (properties.getCsrf().isCookieEnabled()) {
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
                }
            });
            log.debug("CSRF protection enabled");
        } else {
            http.csrf(csrf -> csrf.disable());
            log.debug("CSRF protection disabled");
        }

        // Configure OAuth2 resource server if enabled
        if (properties.getOauth2().isEnabled() && properties.getJwt().isEnabled()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        }

        return http.build();
    }

    /**
     * Configures the CORS configuration source.
     *
     * @return the configured CorsConfigurationSource
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Configure allowed origins
        if (!properties.getCors().getAllowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        } else {
            configuration.setAllowedOrigins(Arrays.asList("*"));
        }

        // Configure allowed methods
        configuration.setAllowedMethods(properties.getCors().getAllowedMethods());

        // Configure allowed headers
        configuration.setAllowedHeaders(properties.getCors().getAllowedHeaders());

        // Configure exposed headers
        if (!properties.getCors().getExposedHeaders().isEmpty()) {
            configuration.setExposedHeaders(properties.getCors().getExposedHeaders());
        }

        // Configure allow credentials
        configuration.setAllowCredentials(properties.getCors().isAllowCredentials());

        // Configure max age
        configuration.setMaxAge(properties.getCors().getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
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
     * Configures the JWT utils.
     *
     * @return the configured JwtUtils
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils() {
        return new JwtUtils(properties.getJwt());
    }
}
