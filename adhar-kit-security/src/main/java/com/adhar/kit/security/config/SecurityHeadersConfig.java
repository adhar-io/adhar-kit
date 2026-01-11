package com.adhar.kit.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Configuration for security headers.
 * Configures security-related HTTP headers (CSP, XSS Protection, etc.).
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "adhar.security.headers", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SecurityHeadersConfig {

    /**
     * Configure security headers.
     */
    public void configureHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
            )
            .frameOptions(frame -> frame.deny())
            .xssProtection(xss -> xss.disable()) // Modern browsers don't need this
            .contentTypeOptions(contentType -> contentType.disable())
        );
    }
}

