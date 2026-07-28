package com.adhar.kit.security.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for Adhar Security Starter.
 * <p>
 * This class provides configuration options for security features including OAuth2/OpenID Connect,
 * JWT token validation, CORS, and other security-related settings for enterprise applications.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "adhar.security")
public class AdharSecurityProperties {

    /**
     * Whether security features are enabled.
     */
    private boolean enabled = true;

    /**
     * Configuration for JWT token validation.
     */
    private JwtProperties jwt = new JwtProperties();

    /**
     * Configuration for OAuth2/OpenID Connect.
     */
    private OAuth2Properties oauth2 = new OAuth2Properties();

    /**
     * Configuration for CORS.
     */
    private CorsProperties cors = new CorsProperties();

    /**
     * Configuration for Content Security Policy.
     */
    private CspProperties csp = new CspProperties();

    /**
     * Configuration for security headers.
     */
    private SecurityHeadersProperties headers = new SecurityHeadersProperties();

    /**
     * Configuration for authentication.
     */
    private AuthenticationProperties authentication = new AuthenticationProperties();

    /**
     * Configuration for authorization.
     */
    private AuthorizationProperties authorization = new AuthorizationProperties();

    /**
     * Configuration for CSRF protection.
     */
    private CsrfProperties csrf = new CsrfProperties();

    /**
     * Configuration for rate limiting.
     */
    private RateLimitProperties rateLimit = new RateLimitProperties();

    /**
     * Configuration for security audit logging.
     */
    private AuditProperties audit = new AuditProperties();

    /**
     * Configuration for token refresh.
     */
    private TokenRefreshProperties tokenRefresh = new TokenRefreshProperties();

    /**
     * Configuration for API-key authentication.
     */
    private ApiKeyProperties apiKey = new ApiKeyProperties();

    /**
     * Configuration for RBAC annotations (@RequiresRole/@RequiresPermission).
     */
    private RbacProperties rbac = new RbacProperties();

    /**
     * Configuration for JWKS key management and publishing.
     */
    private JwksProperties jwks = new JwksProperties();

    /**
     * Configuration for bearer token relay to downstream services.
     */
    private TokenRelayProperties tokenRelay = new TokenRelayProperties();

    /**
     * Configuration properties for JWT token validation.
     */
    @Data
    public static class JwtProperties {
        /**
         * Whether JWT validation is enabled.
         */
        private boolean enabled = true;

        /**
         * The issuer URI for JWT tokens.
         */
        private String issuerUri;

        /**
         * The JWK set URI for JWT tokens.
         */
        private String jwkSetUri;

        /**
         * The audience for JWT tokens.
         */
        private List<String> audience = new ArrayList<>();

        /**
         * The claim name for authorities.
         */
        private String authoritiesClaimName = "authorities";

        /**
         * The claim name for username.
         */
        private String usernameClaimName = "sub";

        /**
         * The claim name for name.
         */
        private String nameClaimName = "name";

        /**
         * The claim name for email.
         */
        private String emailClaimName = "email";

        /**
         * Additional claims to extract from JWT tokens.
         */
        private List<String> additionalClaims = new ArrayList<>();
    }

    /**
     * Configuration properties for OAuth2/OpenID Connect.
     */
    @Data
    public static class OAuth2Properties {
        /**
         * Whether OAuth2/OpenID Connect is enabled.
         */
        private boolean enabled = true;

        /**
         * The client ID for OAuth2/OpenID Connect.
         */
        private String clientId;

        /**
         * The client secret for OAuth2/OpenID Connect.
         */
        private String clientSecret;

        /**
         * The authorization server URI for OAuth2/OpenID Connect.
         */
        private String authorizationUri;

        /**
         * The token URI for OAuth2/OpenID Connect.
         */
        private String tokenUri;

        /**
         * The user info URI for OAuth2/OpenID Connect.
         */
        private String userInfoUri;

        /**
         * The redirect URI for OAuth2/OpenID Connect.
         */
        private String redirectUri;

        /**
         * The scopes for OAuth2/OpenID Connect.
         */
        private List<String> scopes = new ArrayList<>();

        /**
         * Additional parameters for OAuth2/OpenID Connect.
         */
        private Map<String, String> additionalParameters = new HashMap<>();
    }

    /**
     * Configuration properties for CORS.
     */
    @Data
    public static class CorsProperties {
        /**
         * Whether CORS is enabled.
         */
        private boolean enabled = true;

        /**
         * The allowed origins for CORS.
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * The allowed methods for CORS.
         */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

        /**
         * The allowed headers for CORS.
         */
        private List<String> allowedHeaders = List.of("*");

        /**
         * The exposed headers for CORS.
         */
        private List<String> exposedHeaders = new ArrayList<>();

        /**
         * Whether credentials are allowed for CORS.
         */
        private boolean allowCredentials = true;

        /**
         * The max age for CORS preflight requests in seconds.
         */
        private long maxAge = 3600;
    }

    /**
     * Configuration properties for Content Security Policy.
     */
    @Data
    public static class CspProperties {
        /**
         * Whether Content Security Policy is enabled.
         */
        private boolean enabled = true;

        /**
         * The Content Security Policy directives.
         */
        private Map<String, String> directives = new HashMap<>();

        /**
         * Whether to report only Content Security Policy violations.
         */
        private boolean reportOnly = false;

        /**
         * The report URI for Content Security Policy violations.
         */
        private String reportUri;
    }

    /**
     * Configuration properties for security headers.
     */
    @Data
    public static class SecurityHeadersProperties {
        /**
         * Whether security headers are enabled.
         */
        private boolean enabled = true;

        /**
         * Whether X-XSS-Protection header is enabled.
         */
        private boolean xssProtection = true;

        /**
         * Whether X-Content-Type-Options header is enabled.
         */
        private boolean contentTypeOptions = true;

        /**
         * Whether X-Frame-Options header is enabled.
         */
        private boolean frameOptions = true;

        /**
         * The X-Frame-Options header value.
         */
        private String frameOptionsValue = "DENY";

        /**
         * Whether Strict-Transport-Security header is enabled.
         */
        private boolean hsts = true;

        /**
         * The Strict-Transport-Security header max age in seconds.
         */
        private long hstsMaxAgeSeconds = 31536000;

        /**
         * Whether Strict-Transport-Security header includes subdomains.
         */
        private boolean hstsIncludeSubdomains = true;

        /**
         * Whether Strict-Transport-Security header includes preload.
         */
        private boolean hstsPreload = false;

        /**
         * Whether Cache-Control header is enabled.
         */
        private boolean cacheControl = true;

        /**
         * The Cache-Control header value.
         */
        private String cacheControlValue = "no-cache, no-store, max-age=0, must-revalidate";

        /**
         * Whether Pragma header is enabled.
         */
        private boolean pragma = true;

        /**
         * The Pragma header value.
         */
        private String pragmaValue = "no-cache";

        /**
         * Whether Expires header is enabled.
         */
        private boolean expires = true;

        /**
         * The Expires header value.
         */
        private String expiresValue = "0";

        /**
         * Whether Referrer-Policy header is enabled.
         */
        private boolean referrerPolicy = true;

        /**
         * The Referrer-Policy header value.
         */
        private String referrerPolicyValue = "strict-origin-when-cross-origin";

        /**
         * Whether Feature-Policy header is enabled.
         */
        private boolean featurePolicy = false;

        /**
         * The Feature-Policy header value.
         */
        private String featurePolicyValue;

        /**
         * Whether Permissions-Policy header is enabled.
         */
        private boolean permissionsPolicy = false;

        /**
         * The Permissions-Policy header value.
         */
        private String permissionsPolicyValue;
    }

    /**
     * Configuration properties for authentication.
     */
    @Data
    public static class AuthenticationProperties {
        /**
         * Whether authentication is enabled.
         */
        private boolean enabled = true;

        /**
         * The authentication entry point.
         */
        private String entryPoint = "http";

        /**
         * The authentication failure handler.
         */
        private String failureHandler = "http";

        /**
         * The authentication success handler.
         */
        private String successHandler = "http";

        /**
         * The authentication logout handler.
         */
        private String logoutHandler = "http";

        /**
         * The authentication logout success handler.
         */
        private String logoutSuccessHandler = "http";

        /**
         * The authentication remember-me key.
         */
        private String rememberMeKey;

        /**
         * The authentication remember-me validity in seconds.
         */
        private int rememberMeValiditySeconds = 2592000;
    }

    /**
     * Configuration properties for authorization.
     */
    @Data
    public static class AuthorizationProperties {
        /**
         * Whether authorization is enabled.
         */
        private boolean enabled = true;

        /**
         * The authorization decision manager.
         */
        private String decisionManager = "affirmative";

        /**
         * The authorization access denied handler.
         */
        private String accessDeniedHandler = "http";

        /**
         * The authorization URL patterns to permit all.
         */
        private List<String> permitAll = new ArrayList<>();

        /**
         * The authorization URL patterns to authenticate.
         */
        private List<String> authenticated = new ArrayList<>();

        /**
         * The authorization URL patterns with required authorities.
         */
        private Map<String, List<String>> authorities = new HashMap<>();
    }

    /**
     * Configuration properties for CSRF protection.
     */
    @Data
    public static class CsrfProperties {
        /**
         * Whether CSRF protection is enabled.
         */
        private boolean enabled = true;

        /**
         * URL patterns to ignore for CSRF protection.
         */
        private List<String> ignoreAntMatchers = new ArrayList<>();

        /**
         * The CSRF token header name.
         */
        private String headerName = "X-CSRF-TOKEN";

        /**
         * The CSRF token parameter name.
         */
        private String parameterName = "_csrf";

        /**
         * The CSRF cookie name.
         */
        private String cookieName = "XSRF-TOKEN";

        /**
         * Whether to use CSRF cookie.
         */
        private boolean cookieEnabled = false;

        /**
         * The CSRF cookie path.
         */
        private String cookiePath = "/";

        /**
         * The CSRF cookie domain.
         */
        private String cookieDomain;

        /**
         * Whether the CSRF cookie is secure.
         */
        private boolean cookieSecure = true;

        /**
         * Whether the CSRF cookie is HTTP only.
         */
        private boolean cookieHttpOnly = true;
    }

    /**
     * Configuration properties for rate limiting.
     */
    @Data
    public static class RateLimitProperties {
        /**
         * Whether rate limiting is enabled.
         */
        private boolean enabled = false;

        /**
         * Maximum number of requests allowed within the time window.
         */
        private int maxRequests = 100;

        /**
         * Time window in seconds for rate limiting.
         */
        private int windowSeconds = 60;

        /**
         * Counter store backing the rate limiter: {@code memory} (default, single node)
         * or {@code redis} (distributed, requires spring-data-redis and a
         * StringRedisTemplate bean).
         */
        private String store = "memory";

        /**
         * Redis key namespace prefix (only used when {@code store=redis}).
         */
        private String redisKeyPrefix = "adhar:security:ratelimit:";
    }

    /**
     * Configuration properties for security audit logging.
     */
    @Data
    public static class AuditProperties {
        /**
         * Whether security audit logging is enabled.
         */
        private boolean enabled = false;

        /**
         * Whether to log successful authentication events.
         */
        private boolean logSuccessfulAuth = true;

        /**
         * Whether to log failed authentication events.
         */
        private boolean logFailedAuth = true;

        /**
         * Whether to log logout events.
         */
        private boolean logLogout = true;
    }

    /**
     * Configuration properties for token refresh.
     */
    @Data
    public static class TokenRefreshProperties {
        /**
         * Whether token refresh is enabled.
         */
        private boolean enabled = false;

        /**
         * Access token validity in seconds.
         */
        private long accessTokenValiditySeconds = 900; // 15 minutes

        /**
         * Refresh token validity in seconds.
         */
        private long refreshTokenValiditySeconds = 604800; // 7 days

        /**
         * Whether to rotate refresh tokens on each use.
         * When enabled, each refresh generates a new refresh token.
         */
        private boolean rotateRefreshTokens = true;

        /**
         * Secret key for signing tokens.
         * Must be at least 256 bits (32 characters) for HS256.
         */
        private String secret;

        /**
         * Store backing refresh-token families and revocations: {@code memory}
         * (default, single node) or {@code redis} (distributed, requires
         * spring-data-redis and a StringRedisTemplate bean).
         */
        private String store = "memory";

        /**
         * Redis key namespace prefix (only used when {@code store=redis}).
         */
        private String redisKeyPrefix = "adhar:security:refresh:";
    }

    /**
     * Configuration properties for JWKS key management and publishing.
     */
    @Data
    public static class JwksProperties {
        /**
         * Whether the JWKS key manager and publishing endpoint are enabled.
         */
        private boolean enabled = false;

        /**
         * The path at which the public JWKS is published.
         */
        private String path = "/.well-known/jwks.json";

        /**
         * RSA signing key size in bits.
         */
        private int keySize = 2048;

        /**
         * Number of previous keys to retain (in addition to the current key) so that
         * recently-issued tokens still verify after a rotation.
         */
        private int retainKeys = 1;
    }

    /**
     * Configuration properties for bearer token relay to downstream services.
     */
    @Data
    public static class TokenRelayProperties {
        /**
         * Whether the downstream bearer-token relay interceptor is enabled.
         */
        private boolean enabled = false;

        /**
         * The inbound request header carrying the bearer token to relay.
         */
        private String headerName = "Authorization";
    }

    /**
     * Configuration properties for API-key authentication.
     */
    @Data
    public static class ApiKeyProperties {
        /**
         * Whether API-key authentication is enabled.
         */
        private boolean enabled = false;

        /**
         * The request header carrying the API key.
         */
        private String headerName = "X-API-Key";

        /**
         * The accepted API keys, declared as SHA-256 hashes with associated identity.
         */
        private List<ApiKeyCredential> keys = new ArrayList<>();

        /**
         * A single API-key credential (hash + identity).
         */
        @Data
        public static class ApiKeyCredential {
            /**
             * Lowercase hex SHA-256 hash of the API key value.
             */
            private String keyHash;

            /**
             * Principal name associated with the key.
             */
            private String principal;

            /**
             * Roles granted to the key.
             */
            private List<String> roles = new ArrayList<>();
        }
    }

    /**
     * Configuration properties for RBAC annotation enforcement.
     */
    @Data
    public static class RbacProperties {
        /**
         * Whether the access-control aspect enforcing @RequiresRole and
         * @RequiresPermission is enabled.
         */
        private boolean enabled = true;
    }
}
