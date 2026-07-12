package com.adhar.adharkit.security.filter;

import com.adhar.kit.security.filter.RateLimitingFilter;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RateLimitingFilter}.
 */
class RateLimitingFilterTest {

    private AdharSecurityProperties.RateLimitProperties config(boolean enabled, int max, int windowSeconds) {
        AdharSecurityProperties.RateLimitProperties props = new AdharSecurityProperties.RateLimitProperties();
        props.setEnabled(enabled);
        props.setMaxRequests(max);
        props.setWindowSeconds(windowSeconds);
        return props;
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void whenDisabledPassesThroughWithoutHeaders() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(false, 100, 60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("1.2.3.4"), response, chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(filter.getCacheSize()).isZero();
    }

    @Test
    void allowsRequestUnderLimitAndAddsHeaders() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 5, 60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("10.0.0.1"), response, chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
        assertThat(filter.getCacheSize()).isEqualTo(1);
    }

    @Test
    void blocksRequestOverLimitWith429() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 1, 60));
        FilterChain chain = mock(FilterChain.class);

        // First request consumes the only slot.
        filter.doFilter(request("10.0.0.2"), new MockHttpServletResponse(), chain);

        // Second request from the same client should be rejected.
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("10.0.0.2"), response, chain);

        // Chain only invoked for the first (allowed) request.
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Too Many Requests");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void resetsWindowWhenExpired() throws Exception {
        // A zero-second window means every request starts a fresh window.
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 1, 0));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("10.0.0.3"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("10.0.0.3"), new MockHttpServletResponse(), chain);

        // Both requests allowed because the window resets each time.
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesXForwardedForHeaderAsClientId() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 1, 60));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = request("10.0.0.9");
        req1.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");
        filter.doFilter(req1, new MockHttpServletResponse(), chain);

        MockHttpServletRequest req2 = request("10.0.0.9");
        req2.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req2, response, chain);

        // Same forwarded client -> second blocked despite a single distinct cache entry.
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(filter.getCacheSize()).isEqualTo(1);
    }

    @Test
    void usesXRealIpHeaderWhenForwardedForAbsent() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 5, 60));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = request("10.0.0.10");
        req.addHeader("X-Real-IP", "198.51.100.7");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(filter.getCacheSize()).isEqualTo(1);
    }

    @Test
    void distinctClientsTrackedSeparately() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 1, 60));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("10.0.0.20"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("10.0.0.21"), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(filter.getCacheSize()).isEqualTo(2);
    }

    @Test
    void emptyForwardedForFallsBackToRemoteAddr() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(config(true, 5, 60));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req = request("10.0.0.30");
        req.addHeader("X-Forwarded-For", "");
        req.addHeader("X-Real-IP", "");
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        // Falls back to the remote address as the client identifier.
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(filter.getCacheSize()).isEqualTo(1);
    }
}
