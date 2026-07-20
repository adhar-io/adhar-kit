package com.adhar.adharkit.security.filter;

import com.adhar.kit.security.filter.ApiKeyAuthenticationFilter;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.service.ApiKeyService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiKeyAuthenticationFilter}.
 */
class ApiKeyAuthenticationFilterTest {

    private static final String VALID_KEY = "valid-api-key";

    private AdharSecurityProperties.ApiKeyProperties config;
    private ApiKeyAuthenticationFilter filter;

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        config = new AdharSecurityProperties.ApiKeyProperties();
        config.setEnabled(true);

        var credential = new AdharSecurityProperties.ApiKeyProperties.ApiKeyCredential();
        credential.setKeyHash(sha256Hex(VALID_KEY));
        credential.setPrincipal("batch-service");
        credential.setRoles(List.of("ROLE_SERVICE", "batch:run"));
        config.getKeys().add(credential);

        filter = new ApiKeyAuthenticationFilter(config, new ApiKeyService(config));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validApiKeyAuthenticatesRequestDuringChainExecution() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res) {
                seenAuth.set(SecurityContextHolder.getContext().getAuthentication());
            }
        });

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        Authentication auth = seenAuth.get();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo("batch-service");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_SERVICE", "batch:run");
        // Context is cleared after the request completes.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidApiKeyIsRejectedWith401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("Invalid API key");
        assertThat(chain.getRequest()).isNull(); // chain never invoked
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeaderPassesThroughUnauthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankHeaderPassesThroughUnauthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void disabledFilterPassesThroughEvenWithInvalidKey() throws ServletException, IOException {
        config.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void customHeaderNameIsUsed() throws ServletException, IOException {
        config.setHeaderName("X-Custom-Key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res) {
                seenAuth.set(SecurityContextHolder.getContext().getAuthentication());
            }
        });

        filter.doFilter(request, response, chain);

        assertThat(seenAuth.get()).isNotNull();
        assertThat(seenAuth.get().getPrincipal()).isEqualTo("batch-service");
    }
}
