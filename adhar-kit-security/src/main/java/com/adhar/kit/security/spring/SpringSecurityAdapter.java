package com.adhar.kit.security.spring;

import com.adhar.kit.security.api.SecurityService;
import com.adhar.kit.security.service.TokenRefreshService;
import com.adhar.kit.security.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security-backed implementation of the framework-agnostic {@link SecurityService}.
 *
 * <p>Reads the current user from {@link SecurityContextHolder}, supporting both standard
 * {@link Authentication} principals and JWT principals ({@link JwtAuthenticationToken} /
 * {@link Jwt}). Password operations use a {@link PasswordEncoder} (by default the
 * delegating encoder from
 * {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}). Token operations
 * delegate to {@link TokenRefreshService} when available.</p>
 *
 * <p><b>Role vs. permission semantics:</b> {@link #hasRole(String)} matches a granted
 * authority equal to the role name or to {@code ROLE_&lt;name&gt;};
 * {@link #hasPermission(String)} matches the authority exactly (e.g. {@code order:create})
 * or with a {@code SCOPE_} prefix as produced by the JWT resource server.</p>
 *
 * <p><b>Token support:</b> {@link #generateToken}, {@link #validateToken} and
 * {@link #extractUserId} require a {@link TokenRefreshService} (enable
 * {@code adhar.security.token-refresh}). Without it, generation throws
 * {@link IllegalStateException} and validation/extraction return {@code false}/{@code null}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class SpringSecurityAdapter implements SecurityService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenRefreshService tokenRefreshService;

    /**
     * Creates an adapter with the default delegating password encoder and no
     * token support.
     */
    public SpringSecurityAdapter() {
        this(null, null, null);
    }

    /**
     * Creates an adapter.
     *
     * @param passwordEncoder encoder for password operations; when null the default
     *        {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()} is used
     * @param jwtUtils optional JWT utility for claim extraction (may be null)
     * @param tokenRefreshService optional token service for generate/validate/extract
     *        operations (may be null)
     */
    public SpringSecurityAdapter(PasswordEncoder passwordEncoder,
                                 JwtUtils jwtUtils,
                                 TokenRefreshService tokenRefreshService) {
        this.passwordEncoder = passwordEncoder != null
            ? passwordEncoder
            : PasswordEncoderFactories.createDelegatingPasswordEncoder();
        this.jwtUtils = jwtUtils;
        this.tokenRefreshService = tokenRefreshService;
        log.info("Initialized SpringSecurityAdapter (jwtUtils={}, tokenRefreshService={})",
            jwtUtils != null, tokenRefreshService != null);
    }

    @Override
    public String getCurrentUserId() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return null;
        }
        Jwt jwt = extractJwt(auth);
        if (jwt != null) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    @Override
    public String getCurrentUsername() {
        Authentication auth = currentAuthentication();
        if (auth == null) {
            return null;
        }
        Jwt jwt = extractJwt(auth);
        if (jwt != null) {
            String username = jwtUtils != null ? jwtUtils.extractUsername(jwt) : jwt.getSubject();
            return username != null ? username : jwt.getSubject();
        }
        return auth.getName();
    }

    @Override
    public Set<String> getCurrentUserRoles() {
        Authentication auth = currentAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
            .collect(Collectors.toSet());
    }

    @Override
    public boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        Authentication auth = currentAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals(role) || a.equals(ROLE_PREFIX + role));
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllRoles(String... roles) {
        if (roles == null) {
            return true;
        }
        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }
        Authentication auth = currentAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals(permission) || a.equals(SCOPE_PREFIX + permission));
    }

    @Override
    public String generateToken(String userId, Set<String> roles) {
        return generateToken(userId, roles, Map.of());
    }

    @Override
    public String generateToken(String userId, Set<String> roles, Map<String, Object> claims) {
        if (tokenRefreshService == null) {
            throw new IllegalStateException(
                "Token generation requires a TokenRefreshService. "
                    + "Enable it via adhar.security.token-refresh.enabled=true");
        }
        Map<String, Object> effectiveClaims = claims != null ? new HashMap<>(claims) : new HashMap<>();
        return tokenRefreshService.generateAccessToken(userId, roles, effectiveClaims);
    }

    @Override
    public boolean validateToken(String token) {
        if (tokenRefreshService == null) {
            log.warn("Token validation requested but no TokenRefreshService is configured");
            return false;
        }
        return tokenRefreshService.validateToken(token);
    }

    @Override
    public String extractUserId(String token) {
        if (tokenRefreshService == null) {
            log.warn("Token subject extraction requested but no TokenRefreshService is configured");
            return null;
        }
        return tokenRefreshService.extractSubject(token);
    }

    @Override
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean isAuthenticated() {
        return currentAuthentication() != null;
    }

    /**
     * Returns the current non-anonymous, authenticated {@link Authentication} or null.
     */
    private Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }

    /**
     * Extracts a {@link Jwt} from the authentication if it is JWT-based.
     */
    private Jwt extractJwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (auth.getPrincipal() instanceof Jwt jwtPrincipal) {
            return jwtPrincipal;
        }
        return null;
    }
}
