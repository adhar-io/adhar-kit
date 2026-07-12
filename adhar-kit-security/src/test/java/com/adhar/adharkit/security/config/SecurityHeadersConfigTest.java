package com.adhar.adharkit.security.config;

import com.adhar.kit.security.config.SecurityHeadersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SecurityHeadersConfig}.
 */
class SecurityHeadersConfigTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void configureHeadersWiresCspFrameXssAndContentTypeOptions() throws Exception {
        SecurityHeadersConfig config = new SecurityHeadersConfig();

        HttpSecurity http = mock(HttpSecurity.class);
        HeadersConfigurer headers = mock(HeadersConfigurer.class);
        HeadersConfigurer.ContentSecurityPolicyConfig csp = mock(HeadersConfigurer.ContentSecurityPolicyConfig.class);
        HeadersConfigurer.FrameOptionsConfig frame = mock(HeadersConfigurer.FrameOptionsConfig.class);
        HeadersConfigurer.XXssConfig xss = mock(HeadersConfigurer.XXssConfig.class);
        HeadersConfigurer.ContentTypeOptionsConfig contentType = mock(HeadersConfigurer.ContentTypeOptionsConfig.class);

        // http.headers(customizer) -> drive the customizer with our HeadersConfigurer mock.
        when(http.headers(any())).thenAnswer(inv -> {
            ((Customizer<HeadersConfigurer>) inv.getArgument(0)).customize(headers);
            return http;
        });
        // Each nested customizer-accepting method drives its own config and returns the
        // HeadersConfigurer so the fluent chain continues.
        when(headers.contentSecurityPolicy(any())).thenAnswer(inv -> {
            ((Customizer<HeadersConfigurer.ContentSecurityPolicyConfig>) inv.getArgument(0)).customize(csp);
            return headers;
        });
        when(headers.frameOptions(any())).thenAnswer(inv -> {
            ((Customizer<HeadersConfigurer.FrameOptionsConfig>) inv.getArgument(0)).customize(frame);
            return headers;
        });
        when(headers.xssProtection(any())).thenAnswer(inv -> {
            ((Customizer<HeadersConfigurer.XXssConfig>) inv.getArgument(0)).customize(xss);
            return headers;
        });
        when(headers.contentTypeOptions(any())).thenAnswer(inv -> {
            ((Customizer<HeadersConfigurer.ContentTypeOptionsConfig>) inv.getArgument(0)).customize(contentType);
            return headers;
        });
        when(csp.policyDirectives(anyString())).thenReturn(csp);

        config.configureHeaders(http);

        verify(http, times(1)).headers(any());
        verify(csp, times(1)).policyDirectives(
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
        verify(frame, times(1)).deny();
        verify(xss, times(1)).disable();
        verify(contentType, times(1)).disable();
    }
}
