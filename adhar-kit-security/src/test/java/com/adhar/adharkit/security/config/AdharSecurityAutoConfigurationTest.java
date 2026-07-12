package com.adhar.adharkit.security.config;

import com.adhar.kit.security.config.AdharSecurityAutoConfiguration;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AdharSecurityAutoConfiguration}.
 */
@SpringBootTest(classes = {AdharSecurityAutoConfiguration.class})
public class AdharSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharSecurityAutoConfiguration.class));

    @Autowired
    private AdharSecurityProperties properties;

    @Test
    public void testAutoConfigurationLoads() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AdharSecurityAutoConfiguration.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                });
    }

    @Test
    public void testCorsConfigurationLoads() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CorsConfigurationSource.class);
                    assertThat(context).hasSingleBean(CorsFilter.class);
                });
    }

    @Test
    public void testJwtDecoderLoads() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues(
                        "adhar.security.jwt.enabled=true",
                        // Use jwk-set-uri: the decoder is built lazily (no network call),
                        // unlike issuer-uri which eagerly resolves OIDC metadata.
                        "adhar.security.jwt.jwk-set-uri=https://example.com/jwks"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    @Test
    public void testDisabledSecurity() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AdharSecurityAutoConfiguration.class);
                });
    }

    @Configuration
    static class MockConfiguration {
        // AdharSecurityProperties is contributed by the auto-configuration via
        // @EnableConfigurationProperties; defining it again here would create a
        // duplicate bean and break constructor injection.

        // @EnableWebSecurity also contributes a (real) HttpSecurity prototype bean,
        // so mark this lightweight mock as primary to drive the filter-chain beans
        // without standing up full servlet security infrastructure.
        @Bean
        @Primary
        public HttpSecurity httpSecurity() throws Exception {
            return mock(HttpSecurity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        }
    }
}