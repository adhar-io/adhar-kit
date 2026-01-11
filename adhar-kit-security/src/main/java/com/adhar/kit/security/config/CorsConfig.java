package com.adhar.kit.security.config;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * Configuration for Cross-Origin Resource Sharing (CORS).
 * <p>
 * This class provides configuration for CORS, allowing controlled access to resources from different origins.
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "adhar.security.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CorsConfig {

    private final AdharSecurityProperties properties;

    /**
     * Constructor for CorsConfig.
     *
     * @param properties the security properties
     */
    public CorsConfig(AdharSecurityProperties properties) {
        this.properties = properties;
        log.info("Initializing CORS Configuration");
    }

    /**
     * Configures the CORS configuration source.
     *
     * @return the configured CorsConfigurationSource
     */
    @Bean
    @ConditionalOnMissingBean
    public CorsConfigurationSource corsConfigurationSource() {
        log.debug("Configuring CORS configuration source");
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Configure allowed origins
        if (!properties.getCors().getAllowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
            log.debug("CORS allowed origins: {}", properties.getCors().getAllowedOrigins());
        } else {
            configuration.setAllowedOrigins(Arrays.asList("*"));
            log.debug("CORS allowed origins: [*]");
        }
        
        // Configure allowed methods
        configuration.setAllowedMethods(properties.getCors().getAllowedMethods());
        log.debug("CORS allowed methods: {}", properties.getCors().getAllowedMethods());
        
        // Configure allowed headers
        configuration.setAllowedHeaders(properties.getCors().getAllowedHeaders());
        log.debug("CORS allowed headers: {}", properties.getCors().getAllowedHeaders());
        
        // Configure exposed headers
        if (!properties.getCors().getExposedHeaders().isEmpty()) {
            configuration.setExposedHeaders(properties.getCors().getExposedHeaders());
            log.debug("CORS exposed headers: {}", properties.getCors().getExposedHeaders());
        }
        
        // Configure allow credentials
        configuration.setAllowCredentials(properties.getCors().isAllowCredentials());
        log.debug("CORS allow credentials: {}", properties.getCors().isAllowCredentials());
        
        // Configure max age
        configuration.setMaxAge(properties.getCors().getMaxAge());
        log.debug("CORS max age: {}", properties.getCors().getMaxAge());
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    /**
     * Configures the CORS filter.
     *
     * @param corsConfigurationSource the CORS configuration source
     * @return the configured CorsFilter
     */
    @Bean
    @ConditionalOnMissingBean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        log.debug("Configuring CORS filter");
        return new CorsFilter(corsConfigurationSource);
    }
}