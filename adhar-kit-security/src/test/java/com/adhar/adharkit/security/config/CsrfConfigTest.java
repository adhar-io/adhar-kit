package com.adhar.adharkit.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CSRF configuration.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@SpringBootTest(classes = AdharSecurityAutoConfiguration.class)
@TestPropertySource(properties = {
    "adhar.security.enabled=true",
    "adhar.security.csrf.enabled=true"
})
class CsrfConfigTest {

    @Autowired(required = false)
    private CsrfTokenRepository csrfTokenRepository;

    @Test
    void shouldConfigureCsrfWhenEnabled() {
        assertThat(csrfTokenRepository).isNotNull();
    }

    @Test
    void shouldUseCookieCsrfTokenRepository() {
        assertThat(csrfTokenRepository).isNotNull();
        assertThat(csrfTokenRepository.getClass().getSimpleName())
            .contains("CookieCsrfTokenRepository");
    }
}

