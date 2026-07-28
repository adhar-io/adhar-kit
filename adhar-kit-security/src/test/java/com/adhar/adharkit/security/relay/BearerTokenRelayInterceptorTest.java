package com.adhar.adharkit.security.relay;

import com.adhar.kit.security.relay.BearerTokenRelayInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BearerTokenRelayInterceptor}.
 */
class BearerTokenRelayInterceptorTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindInboundRequest(String headerName, String headerValue) {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        if (headerValue != null) {
            inbound.addHeader(headerName, headerValue);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
    }

    private HttpRequest outboundRequest(HttpHeaders headers) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("https://downstream/api"));
        return request;
    }

    @Test
    void relaysBearerTokenToDownstream() throws IOException {
        bindInboundRequest(HttpHeaders.AUTHORIZATION, "Bearer abc123");
        HttpHeaders outbound = new HttpHeaders();
        HttpRequest request = outboundRequest(outbound);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(response);

        BearerTokenRelayInterceptor interceptor = new BearerTokenRelayInterceptor();
        byte[] body = new byte[0];

        assertThat(interceptor.intercept(request, body, execution)).isSameAs(response);
        assertThat(outbound.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc123");
        verify(execution).execute(request, body);
    }

    @Test
    void doesNotOverwriteExistingAuthorization() throws IOException {
        bindInboundRequest(HttpHeaders.AUTHORIZATION, "Bearer inbound");
        HttpHeaders outbound = new HttpHeaders();
        outbound.set(HttpHeaders.AUTHORIZATION, "Bearer preset");
        HttpRequest request = outboundRequest(outbound);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        new BearerTokenRelayInterceptor().intercept(request, new byte[0], execution);

        assertThat(outbound.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer preset");
    }

    @Test
    void noRelayWhenNoCurrentRequest() throws IOException {
        // No RequestContextHolder bound.
        HttpHeaders outbound = new HttpHeaders();
        HttpRequest request = outboundRequest(outbound);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        new BearerTokenRelayInterceptor().intercept(request, new byte[0], execution);

        assertThat(outbound.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void noRelayWhenInboundHeaderNotBearer() throws IOException {
        bindInboundRequest(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        HttpHeaders outbound = new HttpHeaders();
        HttpRequest request = outboundRequest(outbound);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        new BearerTokenRelayInterceptor().intercept(request, new byte[0], execution);

        assertThat(outbound.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void relaysFromCustomHeaderName() throws IOException {
        bindInboundRequest("X-Auth", "Bearer custom-token");
        HttpHeaders outbound = new HttpHeaders();
        HttpRequest request = outboundRequest(outbound);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        new BearerTokenRelayInterceptor("X-Auth").intercept(request, new byte[0], execution);

        assertThat(outbound.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer custom-token");
    }
}
