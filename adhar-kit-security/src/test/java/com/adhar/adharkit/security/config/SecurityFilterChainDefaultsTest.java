package com.adhar.adharkit.security.config;

import com.adhar.kit.security.config.AdharSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default filter chain as a service gets it, with no JWT configured: requests
 * go through Spring Security's filter chain and either reach the application
 * (the mock chain records the request) or are stopped.
 */
class SecurityFilterChainDefaultsTest {

    static MockFilterChain send(Filter securityFilterChain, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockFilterChain application = new MockFilterChain();
        securityFilterChain.doFilter(request, new MockHttpServletResponse(), application);
        return application;
    }

    @Nested
    @SpringBootTest(classes = AdharSecurityAutoConfiguration.class)
    class Defaults {

        @Autowired
        @Qualifier("springSecurityFilterChain")
        Filter securityFilterChain;

        @Test
        void kubernetesProbesReachTheApplicationWithoutCredentials() throws Exception {
            assertThat(send(securityFilterChain, "GET", "/actuator/health/liveness").getRequest()).isNotNull();
            assertThat(send(securityFilterChain, "GET", "/actuator/health/readiness").getRequest()).isNotNull();
            assertThat(send(securityFilterChain, "GET", "/actuator/health").getRequest()).isNotNull();
        }

        @Test
        void applicationPathsStillRequireAuthentication() throws Exception {
            assertThat(send(securityFilterChain, "GET", "/orders").getRequest()).isNull();
        }
    }

    @Nested
    @SpringBootTest(classes = AdharSecurityAutoConfiguration.class)
    @TestPropertySource(properties = "adhar.security.authorization.permit-all=/**")
    class PermittedApi {

        @Autowired
        @Qualifier("springSecurityFilterChain")
        Filter securityFilterChain;

        @Test
        void writesAreNotRejectedForAMissingCsrfToken() throws Exception {
            // A stateless bearer-token API: CSRF used to reject every POST with 403.
            assertThat(send(securityFilterChain, "POST", "/orders").getRequest()).isNotNull();
        }
    }

    @Nested
    @SpringBootTest(classes = AdharSecurityAutoConfiguration.class)
    @TestPropertySource(properties = "adhar.security.authorization.permit-health-endpoints=false")
    class ProbesProtected {

        @Autowired
        @Qualifier("springSecurityFilterChain")
        Filter securityFilterChain;

        @Test
        void healthCanBeProtectedExplicitly() throws Exception {
            assertThat(send(securityFilterChain, "GET", "/actuator/health/liveness").getRequest()).isNull();
        }
    }
}
