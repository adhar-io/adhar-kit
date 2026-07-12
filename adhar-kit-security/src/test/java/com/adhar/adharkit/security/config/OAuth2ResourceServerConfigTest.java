package com.adhar.adharkit.security.config;

import com.adhar.kit.security.config.OAuth2ResourceServerConfig;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link OAuth2ResourceServerConfig}.
 */
class OAuth2ResourceServerConfigTest {

    private OAuth2ResourceServerConfig config(AdharSecurityProperties props) {
        return new OAuth2ResourceServerConfig(props, new JwtUtils(props.getJwt()));
    }

    @Test
    void jwtDecoderBuiltFromJwkSetUri() {
        AdharSecurityProperties props = new AdharSecurityProperties();
        props.getJwt().setJwkSetUri("https://example.com/.well-known/jwks.json");

        JwtDecoder decoder = config(props).jwtDecoder();

        // Built lazily from the JWK set URI without performing any network call.
        assertThat(decoder).isNotNull();
    }

    @Test
    void jwtDecoderThrowsWhenNeitherUriConfigured() {
        AdharSecurityProperties props = new AdharSecurityProperties();
        // No issuer-uri and no jwk-set-uri configured.

        OAuth2ResourceServerConfig config = config(props);

        assertThatThrownBy(config::jwtDecoder)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("jwkSetUri or issuerUri");
    }

    @Test
    void jwtAuthenticationConverterIsConfigured() {
        AdharSecurityProperties props = new AdharSecurityProperties();
        props.getJwt().setAuthoritiesClaimName("roles");

        Converter<Jwt, AbstractAuthenticationToken> converter = config(props).jwtAuthenticationConverter();

        assertThat(converter).isNotNull();
    }

    @Test
    void oauth2SecurityFilterChainBuilds() throws Exception {
        AdharSecurityProperties props = new AdharSecurityProperties();
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);

        SecurityFilterChain chain = config(props).oauth2SecurityFilterChain(http);

        assertThat(chain).isNotNull();
    }
}
