package com.adhar.kit.security.filter;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests carrying an API key in a configurable header
 * (default {@code X-API-Key}).
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>Feature disabled or header absent: the request continues unchanged.</li>
 *   <li>Header present and valid: the {@link SecurityContextHolder} is populated with an
 *       authenticated principal carrying the roles configured for the key, and the
 *       request continues.</li>
 *   <li>Header present but invalid: the request is rejected with {@code 401} and a JSON
 *       error body.</li>
 * </ul>
 *
 * <p>Off by default; enable via {@code adhar.security.api-key.enabled=true}.
 * Keys are validated by {@link ApiKeyService} against configured SHA-256 hashes.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private final AdharSecurityProperties.ApiKeyProperties config;
    private final ApiKeyService apiKeyService;

    /**
     * Creates the filter.
     *
     * @param config API-key configuration (header name, enabled flag)
     * @param apiKeyService service used to validate presented keys
     */
    public ApiKeyAuthenticationFilter(AdharSecurityProperties.ApiKeyProperties config,
                                      ApiKeyService apiKeyService) {
        this.config = config;
        this.apiKeyService = apiKeyService;
        log.info("API key authentication filter initialized (header: {})", config.getHeaderName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(config.getHeaderName());
        if (apiKey == null || apiKey.isBlank()) {
            // No API key presented - let other authentication mechanisms handle the request.
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyService.ApiKeyPrincipal> principal = apiKeyService.authenticate(apiKey);
        if (principal.isEmpty()) {
            log.warn("Rejected request with invalid API key from {}", request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid API key\"}");
            return;
        }

        List<SimpleGrantedAuthority> authorities = principal.get().roles().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();

        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(principal.get().principal(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        log.debug("Authenticated API key principal: {}", principal.get().principal());

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
