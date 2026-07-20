package com.adhar.kit.security.service;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Service for handling JWT token refresh operations.
 *
 * <p>Provides secure token refresh functionality with optional refresh token rotation
 * for enhanced security.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Refresh token validation</li>
 *   <li>Access token generation</li>
 *   <li>Refresh token rotation (optional)</li>
 *   <li>Token family tracking for rotation</li>
 *   <li>Refresh token revocation</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   security:
 *     token-refresh:
 *       enabled: true
 *       access-token-validity-seconds: 900
 *       refresh-token-validity-seconds: 604800
 *       rotate-refresh-tokens: true
 *       secret: your-256-bit-secret
 * }</pre>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @RestController
 * public class AuthController {
 *
 *     @Autowired
 *     private TokenRefreshService tokenRefreshService;
 *
 *     @PostMapping("/auth/refresh")
 *     public TokenResponse refreshToken(@RequestBody RefreshRequest request) {
 *         return tokenRefreshService.refreshAccessToken(request.getRefreshToken());
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class TokenRefreshService {

    private final AdharSecurityProperties.TokenRefreshProperties config;
    private final SecretKey signingKey;

    /**
     * Pluggable storage for token families and revocations.
     */
    private final RefreshTokenStore tokenStore;

    /**
     * Token response DTO.
     */
    public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn,
        String tokenType
    ) {}

    /**
     * Creates token refresh service backed by the default in-memory store.
     *
     * @param config token refresh configuration
     */
    public TokenRefreshService(AdharSecurityProperties.TokenRefreshProperties config) {
        this(config, new InMemoryRefreshTokenStore());
    }

    /**
     * Creates token refresh service with a custom {@link RefreshTokenStore}
     * (e.g. a Redis-backed implementation for multi-node deployments).
     *
     * @param config token refresh configuration
     * @param tokenStore store for token families and revocations
     */
    public TokenRefreshService(AdharSecurityProperties.TokenRefreshProperties config, RefreshTokenStore tokenStore) {
        this.config = config;
        this.tokenStore = tokenStore;

        // Generate or use configured signing key
        String secret = config.getSecret();
        if (secret == null || secret.length() < 32) {
            // Generate a secure random key if not configured
            secret = UUID.randomUUID().toString() + UUID.randomUUID().toString();
            log.warn("Token refresh secret not configured or too short, using generated secret. " +
                "Configure adhar.security.token-refresh.secret for production.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        log.info("Token refresh service initialized - Access token validity: {}s, Refresh token validity: {}s, Rotation: {}",
            config.getAccessTokenValiditySeconds(),
            config.getRefreshTokenValiditySeconds(),
            config.isRotateRefreshTokens());
    }

    /**
     * Refreshes the access token using a valid refresh token.
     *
     * @param refreshToken the refresh token
     * @return new token response with access token (and new refresh token if rotation enabled)
     * @throws TokenRefreshException if refresh token is invalid or expired
     */
    public TokenResponse refreshAccessToken(String refreshToken) {
        if (!config.isEnabled()) {
            throw new TokenRefreshException("Token refresh is disabled");
        }

        // Check if token is revoked
        if (tokenStore.isTokenRevoked(refreshToken)) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }

        try {
            // Parse and validate refresh token
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(refreshToken)
                .getPayload();

            // Validate token type
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                throw new TokenRefreshException("Invalid token type");
            }

            // Check expiration
            Date expiration = claims.getExpiration();
            if (expiration.before(new Date())) {
                throw new TokenRefreshException("Refresh token has expired");
            }

            String subject = claims.getSubject();
            String familyId = claims.get("family", String.class);

            // Handle token rotation
            String newRefreshToken = refreshToken;
            long refreshTokenExpiresIn = config.getRefreshTokenValiditySeconds();

            if (config.isRotateRefreshTokens()) {
                // Check if token is still valid in its family
                if (familyId != null
                        && tokenStore.familyExists(familyId)
                        && !tokenStore.isTokenInFamily(familyId, refreshToken)) {
                    // Token was already rotated - possible token theft
                    log.warn("Refresh token reuse detected for family {}. Revoking entire family.", familyId);
                    revokeTokenFamily(familyId);
                    throw new TokenRefreshException("Refresh token has been invalidated");
                }

                // Generate new refresh token
                newRefreshToken = generateRefreshToken(subject, familyId != null ? familyId : UUID.randomUUID().toString());

                // Invalidate old refresh token
                if (familyId != null) {
                    tokenStore.removeTokenFromFamily(familyId, refreshToken);
                }

                // Calculate remaining validity for rotated token
                long originalExpiry = expiration.getTime();
                long now = System.currentTimeMillis();
                refreshTokenExpiresIn = Math.max(0, (originalExpiry - now) / 1000);
            }

            // Generate new access token
            String accessToken = generateAccessToken(subject, claims);

            log.debug("Access token refreshed for user: {}", subject);

            return new TokenResponse(
                accessToken,
                newRefreshToken,
                config.getAccessTokenValiditySeconds(),
                refreshTokenExpiresIn,
                "Bearer"
            );

        } catch (TokenRefreshException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new TokenRefreshException("Invalid refresh token: " + e.getMessage());
        }
    }

    /**
     * Generates a new access token.
     */
    private String generateAccessToken(String subject, Claims originalClaims) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(config.getAccessTokenValiditySeconds());

        var builder = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("type", "access");

        // Copy over relevant claims from original token
        if (originalClaims.containsKey("roles")) {
            builder.claim("roles", originalClaims.get("roles"));
        }
        if (originalClaims.containsKey("authorities")) {
            builder.claim("authorities", originalClaims.get("authorities"));
        }
        if (originalClaims.containsKey("email")) {
            builder.claim("email", originalClaims.get("email"));
        }
        if (originalClaims.containsKey("name")) {
            builder.claim("name", originalClaims.get("name"));
        }

        return builder.signWith(signingKey).compact();
    }

    /**
     * Generates a new refresh token.
     */
    private String generateRefreshToken(String subject, String familyId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(config.getRefreshTokenValiditySeconds());

        String token = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("type", "refresh")
            .claim("family", familyId)
            .id(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact();

        // Track token in family
        tokenStore.addTokenToFamily(familyId, token, config.getRefreshTokenValiditySeconds());

        return token;
    }

    /**
     * Generates a standalone access token (no refresh token / family tracking).
     *
     * @param subject user identifier
     * @param roles roles to embed in the {@code roles} claim (may be null or empty)
     * @param claims additional claims to include (may be null)
     * @return signed access token
     */
    public String generateAccessToken(String subject, Set<String> roles, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(config.getAccessTokenValiditySeconds());

        var builder = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("type", "access");

        if (roles != null && !roles.isEmpty()) {
            builder.claim("roles", new ArrayList<>(roles));
        }
        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.signWith(signingKey).compact();
    }

    /**
     * Validates a token signed by this service (signature and expiration).
     *
     * @param token the token to validate
     * @return true if the token is well-formed, correctly signed and not expired
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the subject (user id) from a token signed by this service.
     *
     * @param token the token
     * @return the subject, or null if the token cannot be parsed or verified
     */
    public String extractSubject(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (Exception e) {
            log.debug("Could not extract subject from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates initial token pair for a user.
     *
     * @param subject user identifier
     * @param claims additional claims to include
     * @return token response with access and refresh tokens
     */
    public TokenResponse createTokenPair(String subject, Map<String, Object> claims) {
        if (!config.isEnabled()) {
            throw new TokenRefreshException("Token refresh is disabled");
        }

        String familyId = UUID.randomUUID().toString();

        // Generate access token
        Instant now = Instant.now();
        Instant accessExpiry = now.plusSeconds(config.getAccessTokenValiditySeconds());

        var accessBuilder = Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(accessExpiry))
            .claim("type", "access");

        if (claims != null) {
            claims.forEach(accessBuilder::claim);
        }

        String accessToken = accessBuilder.signWith(signingKey).compact();

        // Generate refresh token
        String refreshToken = generateRefreshToken(subject, familyId);

        log.debug("Token pair created for user: {}", subject);

        return new TokenResponse(
            accessToken,
            refreshToken,
            config.getAccessTokenValiditySeconds(),
            config.getRefreshTokenValiditySeconds(),
            "Bearer"
        );
    }

    /**
     * Revokes a refresh token.
     *
     * @param refreshToken the refresh token to revoke
     */
    public void revokeRefreshToken(String refreshToken) {
        tokenStore.revokeToken(refreshToken, config.getRefreshTokenValiditySeconds());

        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(refreshToken)
                .getPayload();

            String familyId = claims.get("family", String.class);
            if (familyId != null) {
                tokenStore.removeTokenFromFamily(familyId, refreshToken);
            }

            log.debug("Refresh token revoked for user: {}", claims.getSubject());

        } catch (Exception e) {
            log.debug("Could not parse revoked token: {}", e.getMessage());
        }
    }

    /**
     * Revokes all tokens in a token family (used when token theft is detected).
     */
    private void revokeTokenFamily(String familyId) {
        Set<String> familyTokens = tokenStore.removeFamily(familyId);
        if (!familyTokens.isEmpty()) {
            familyTokens.forEach(token ->
                tokenStore.revokeToken(token, config.getRefreshTokenValiditySeconds()));
            log.warn("Revoked {} tokens in family {}", familyTokens.size(), familyId);
        }
    }

    /**
     * Revokes all refresh tokens for a user.
     *
     * @param userId user identifier
     */
    public void revokeAllUserTokens(String userId) {
        List<String> familiesToRemove = new ArrayList<>();

        for (String familyId : tokenStore.getFamilyIds()) {
            for (String token : tokenStore.getFamilyTokens(familyId)) {
                try {
                    Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                    if (userId.equals(claims.getSubject())) {
                        familiesToRemove.add(familyId);
                        break;
                    }
                } catch (Exception e) {
                    // Token might be expired, continue
                }
            }
        }

        for (String familyId : familiesToRemove) {
            revokeTokenFamily(familyId);
        }

        log.info("Revoked all refresh tokens for user: {}", userId);
    }

    /**
     * Exception thrown when token refresh fails.
     */
    public static class TokenRefreshException extends RuntimeException {
        public TokenRefreshException(String message) {
            super(message);
        }
    }
}
