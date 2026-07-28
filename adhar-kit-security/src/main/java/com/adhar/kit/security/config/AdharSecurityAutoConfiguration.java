package com.adhar.kit.security.config;

import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.security.api.SecurityService;
import com.adhar.kit.security.aspect.AccessControlAspect;
import com.adhar.kit.security.audit.AuditEventSink;
import com.adhar.kit.security.audit.SecurityAuditLogger;
import com.adhar.kit.security.audit.Slf4jAuditEventSink;
import com.adhar.kit.security.filter.ApiKeyAuthenticationFilter;
import com.adhar.kit.security.filter.RateLimitingFilter;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.ratelimit.InMemoryRateLimiterStore;
import com.adhar.kit.security.ratelimit.RateLimiterStore;
import com.adhar.kit.security.relay.BearerTokenRelayInterceptor;
import com.adhar.kit.security.service.ApiKeyService;
import com.adhar.kit.security.service.InMemoryRefreshTokenStore;
import com.adhar.kit.security.service.RefreshTokenStore;
import com.adhar.kit.security.service.TokenRefreshService;
import com.adhar.kit.security.spring.SpringSecurityAdapter;
import com.adhar.kit.security.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
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
    CorsConfig.class,
    RedisSecurityConfiguration.class,
    JwksConfiguration.class
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

                csrf.csrfTokenRepository(csrfTokenRepository());
            });
            log.debug("CSRF protection enabled");
        } else {
            http.csrf(csrf -> csrf.disable());
            log.debug("CSRF protection disabled");
        }

        // Configure OAuth2 resource server only when a JWT endpoint is configured.
        // Without an issuer/JWK set URI there is no JwtDecoder bean, so wiring the
        // resource server here would fail the filter chain build.
        if (properties.getOauth2().isEnabled() && properties.getJwt().isEnabled() && isJwtConfigured()) {
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
     * Configures the CSRF token repository.
     * <p>
     * A cookie-based repository (readable by JavaScript) is exposed so that SPA
     * clients can echo the token back, which is the common pattern for stateless
     * APIs. Only created when CSRF protection is enabled.
     * </p>
     *
     * @return the configured CsrfTokenRepository
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.csrf", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /**
     * Whether a JWT validation endpoint (issuer or JWK set URI) has been configured.
     *
     * @return {@code true} if JWT decoding can be performed
     */
    private boolean isJwtConfigured() {
        return (properties.getJwt().getIssuerUri() != null && !properties.getJwt().getIssuerUri().isEmpty())
            || (properties.getJwt().getJwkSetUri() != null && !properties.getJwt().getJwkSetUri().isEmpty());
    }

    /**
     * Configures the JWT decoder.
     *
     * @return the configured JwtDecoder
     */
    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnJwtConfiguredCondition.OnBeanMethod.class)
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

    /**
     * Configures the rate limiting filter.
     *
     * @return the rate limiting filter registration
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.rate-limit", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilter(RateLimiterStore rateLimiterStore) {
        log.info("Configuring rate limiting filter: {} requests per {} seconds (store: {})",
            properties.getRateLimit().getMaxRequests(),
            properties.getRateLimit().getWindowSeconds(),
            rateLimiterStore.getClass().getSimpleName());

        RateLimitingFilter filter = new RateLimitingFilter(properties.getRateLimit(), rateLimiterStore);
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Configures the default in-memory rate-limiter counter store. Replaced by a
     * distributed store when {@code adhar.security.rate-limit.store=redis}.
     *
     * @return the in-memory rate limiter store
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.rate-limit", name = "store", havingValue = "memory", matchIfMissing = true)
    public RateLimiterStore rateLimiterStore() {
        return new InMemoryRateLimiterStore();
    }

    /**
     * Configures the downstream bearer-token relay interceptor. Register it with a
     * {@code RestClient}/{@code RestTemplate} builder to forward the caller's token.
     *
     * @return the relay interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.token-relay", name = "enabled", havingValue = "true")
    public BearerTokenRelayInterceptor bearerTokenRelayInterceptor() {
        log.info("Configuring bearer token relay interceptor (header: {})",
            properties.getTokenRelay().getHeaderName());
        return new BearerTokenRelayInterceptor(properties.getTokenRelay().getHeaderName());
    }

    /**
     * Configures the audit event sink used by the security audit logger.
     *
     * @return the audit event sink (SLF4J/Jackson by default)
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.audit", name = "enabled", havingValue = "true")
    public AuditEventSink auditEventSink() {
        return new Slf4jAuditEventSink();
    }

    /**
     * Configures the security audit logger.
     *
     * @param auditEventSink destination for audit events
     * @return the security audit logger
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.audit", name = "enabled", havingValue = "true")
    public SecurityAuditLogger securityAuditLogger(AuditEventSink auditEventSink) {
        log.info("Configuring security audit logger");
        return new SecurityAuditLogger(properties.getAudit(), auditEventSink);
    }

    /**
     * Configures the token refresh service.
     *
     * @param refreshTokenStore store for token families and revocations
     * @return the token refresh service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.token-refresh", name = "enabled", havingValue = "true")
    public TokenRefreshService tokenRefreshService(RefreshTokenStore refreshTokenStore) {
        log.info("Configuring token refresh service");
        return new TokenRefreshService(properties.getTokenRefresh(), refreshTokenStore);
    }

    /**
     * Configures the Spring-backed {@link SecurityService} and registers it as the
     * delegate of the universal {@link SecurityFacade}.
     *
     * @param passwordEncoder optional user-provided password encoder
     * @param jwtUtils optional JWT utils
     * @param tokenRefreshService optional token refresh service (enables token operations)
     * @return the security service adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityService securityService(ObjectProvider<PasswordEncoder> passwordEncoder,
                                           ObjectProvider<JwtUtils> jwtUtils,
                                           ObjectProvider<TokenRefreshService> tokenRefreshService) {
        SpringSecurityAdapter adapter = new SpringSecurityAdapter(
            passwordEncoder.getIfAvailable(),
            jwtUtils.getIfAvailable(),
            tokenRefreshService.getIfAvailable());
        SecurityFacade.getInstance().setDelegate(adapter);
        log.info("Registered SpringSecurityAdapter as SecurityFacade delegate");
        return adapter;
    }

    /**
     * Configures the aspect enforcing @RequiresRole / @RequiresPermission annotations.
     *
     * @param securityService the security service used for evaluation
     * @return the access-control aspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.rbac", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AccessControlAspect accessControlAspect(SecurityService securityService) {
        log.info("Configuring RBAC access-control aspect");
        return new AccessControlAspect(securityService);
    }

    /**
     * Configures the API-key service validating configured SHA-256 key hashes.
     *
     * @return the API key service
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.api-key", name = "enabled", havingValue = "true")
    public ApiKeyService apiKeyService() {
        log.info("Configuring API key service");
        return new ApiKeyService(properties.getApiKey());
    }

    /**
     * Configures the API-key authentication filter.
     *
     * @param apiKeyService the API key service
     * @return the API-key filter registration
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.security.api-key", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        log.info("Configuring API key authentication filter (header: {})", properties.getApiKey().getHeaderName());
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(properties.getApiKey(), apiKeyService);
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Provides the default in-memory {@link RefreshTokenStore}. Only active when token
     * refresh is enabled and the store is not explicitly set to {@code redis}, so the
     * distributed store (see {@code RedisSecurityConfiguration}) can take over.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "adhar.security.token-refresh", name = "enabled", havingValue = "true")
    static class RefreshTokenStoreConfiguration {

        /**
         * Configures the in-memory refresh-token store (default).
         *
         * @return the refresh token store
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.security.token-refresh", name = "store", havingValue = "memory", matchIfMissing = true)
        public RefreshTokenStore refreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }
    }
}
