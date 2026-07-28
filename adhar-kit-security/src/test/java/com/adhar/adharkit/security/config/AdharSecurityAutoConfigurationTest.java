package com.adhar.adharkit.security.config;

import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.security.api.SecurityService;
import com.adhar.kit.security.aspect.AccessControlAspect;
import com.adhar.kit.security.audit.AuditEventSink;
import com.adhar.kit.security.audit.SecurityAuditLogger;
import com.adhar.kit.security.audit.Slf4jAuditEventSink;
import com.adhar.kit.security.config.AdharSecurityAutoConfiguration;
import com.adhar.kit.security.jwks.JwksController;
import com.adhar.kit.security.jwks.JwksKeyManager;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.ratelimit.InMemoryRateLimiterStore;
import com.adhar.kit.security.ratelimit.RateLimiterStore;
import com.adhar.kit.security.ratelimit.RedisRateLimiterStore;
import com.adhar.kit.security.relay.BearerTokenRelayInterceptor;
import com.adhar.kit.security.service.ApiKeyService;
import com.adhar.kit.security.service.InMemoryRefreshTokenStore;
import com.adhar.kit.security.service.RedisRefreshTokenStore;
import com.adhar.kit.security.service.RefreshTokenStore;
import com.adhar.kit.security.service.TokenRefreshService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.adhar.kit.security.spring.SpringSecurityAdapter;
import com.adhar.kit.security.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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

    @Test
    public void testSecurityServiceAdapterRegisteredAndWiredIntoFacade() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityService.class);
                    assertThat(context.getBean(SecurityService.class))
                            .isInstanceOf(SpringSecurityAdapter.class);
                    assertThat(SecurityFacade.getInstance().hasDelegate()).isTrue();
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testAccessControlAspectEnabledByDefaultAndCanBeDisabled() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(AccessControlAspect.class));

        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.rbac.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AccessControlAspect.class));
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testTokenRefreshBeansLoadWithStore() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues(
                        "adhar.security.token-refresh.enabled=true",
                        "adhar.security.token-refresh.secret=this-is-a-very-long-test-secret-key-256bits!!"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RefreshTokenStore.class);
                    assertThat(context.getBean(RefreshTokenStore.class))
                            .isInstanceOf(InMemoryRefreshTokenStore.class);
                    assertThat(context).hasSingleBean(TokenRefreshService.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testTokenRefreshBeansAbsentByDefault() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RefreshTokenStore.class);
                    assertThat(context).doesNotHaveBean(TokenRefreshService.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testApiKeyBeansLoadWhenEnabled() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.api-key.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiKeyService.class);
                    assertThat(context.getBeansOfType(FilterRegistrationBean.class))
                            .containsKey("apiKeyAuthenticationFilter");
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testApiKeyBeansAbsentByDefault() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(ApiKeyService.class));
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testAuditBeansLoadWhenEnabled() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.audit.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventSink.class);
                    assertThat(context.getBean(AuditEventSink.class))
                            .isInstanceOf(Slf4jAuditEventSink.class);
                    assertThat(context).hasSingleBean(SecurityAuditLogger.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testDefaultRateLimiterStoreIsInMemory() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiterStore.class);
                    assertThat(context.getBean(RateLimiterStore.class))
                            .isInstanceOf(InMemoryRateLimiterStore.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testRedisRateLimiterStoreSelectedWhenConfigured() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class, RedisMockConfiguration.class)
                .withPropertyValues(
                        "adhar.security.rate-limit.enabled=true",
                        "adhar.security.rate-limit.store=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiterStore.class);
                    assertThat(context.getBean(RateLimiterStore.class))
                            .isInstanceOf(RedisRateLimiterStore.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testRedisRefreshTokenStoreSelectedWhenConfigured() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class, RedisMockConfiguration.class)
                .withPropertyValues(
                        "adhar.security.token-refresh.enabled=true",
                        "adhar.security.token-refresh.store=redis",
                        "adhar.security.token-refresh.secret=this-is-a-very-long-test-secret-key-256bits!!")
                .run(context -> {
                    assertThat(context).hasSingleBean(RefreshTokenStore.class);
                    assertThat(context.getBean(RefreshTokenStore.class))
                            .isInstanceOf(RedisRefreshTokenStore.class);
                    assertThat(context).hasSingleBean(TokenRefreshService.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testTokenRelayInterceptorLoadsWhenEnabled() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.token-relay.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(BearerTokenRelayInterceptor.class));
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testTokenRelayInterceptorAbsentByDefault() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(BearerTokenRelayInterceptor.class));
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testJwksBeansLoadWhenEnabled() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .withPropertyValues("adhar.security.jwks.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwksKeyManager.class);
                    assertThat(context).hasSingleBean(JwksController.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Test
    public void testJwksBeansAbsentByDefault() {
        contextRunner
                .withUserConfiguration(MockConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JwksKeyManager.class);
                    assertThat(context).doesNotHaveBean(JwksController.class);
                });
        SecurityFacade.getInstance().clearDelegate();
    }

    @Configuration
    static class RedisMockConfiguration {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
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