package com.adhar.kit.graphql.ratelimit;

import com.adhar.kit.graphql.config.GraphQlProperties;
import graphql.ExecutionInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link QueryCostRateLimitInterceptor}.
 */
class QueryCostRateLimitInterceptorTest {

    private final WebGraphQlResponse mockResponse = mock(WebGraphQlResponse.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private GraphQlProperties properties() {
        return new GraphQlProperties();
    }

    private WebGraphQlRequest request(String document, HttpHeaders headers, InetSocketAddress remote) {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        when(request.getDocument()).thenReturn(document);
        when(request.getOperationName()).thenReturn(null);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(remote);
        when(request.toExecutionInput()).thenReturn(ExecutionInput.newExecutionInput(document).build());
        return request;
    }

    private WebGraphQlInterceptor.Chain capturingChain(AtomicReference<WebGraphQlRequest> captured) {
        return req -> {
            captured.set(req);
            return Mono.just(mockResponse);
        };
    }

    @Test
    @DisplayName("allows a request that is within the client's budget")
    void allowsWithinBudget() {
        ClientRateLimiter limiter = new ClientRateLimiter(100, 10.0, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Client-Id", "client-1");
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { a b c }", headers, null), capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @DisplayName("rejects an over-budget request with a RateLimited error and does not call the chain")
    void rejectsOverBudget() {
        // capacity of 2 tokens, query costs 3 fields -> rejected
        ClientRateLimiter limiter = new ClientRateLimiter(2, 0.001, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Client-Id", "client-1");
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { a b c }", headers, null), capturingChain(captured));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(QueryCostRateLimitInterceptor.RATE_LIMITED);
        assertThat(captured.get()).as("chain must not run when rate limited").isNull();
    }

    @Test
    @DisplayName("resolves client id from the configured header")
    void resolvesClientIdFromHeader() {
        ClientRateLimiter limiter = new ClientRateLimiter(100, 10.0, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Client-Id", "  header-client  ");

        String clientId = interceptor.resolveClientId(request("query { a }", headers, null));

        assertThat(clientId).isEqualTo("header-client");
    }

    @Test
    @DisplayName("falls back to the authenticated principal name when no header is present")
    void resolvesClientIdFromPrincipal() {
        ClientRateLimiter limiter = new ClientRateLimiter(100, 10.0, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice", "pw", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        String clientId = interceptor.resolveClientId(request("query { a }", new HttpHeaders(), null));

        assertThat(clientId).isEqualTo("alice");
    }

    @Test
    @DisplayName("falls back to the remote address when no header or principal is present")
    void resolvesClientIdFromRemoteAddress() {
        ClientRateLimiter limiter = new ClientRateLimiter(100, 10.0, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());
        InetSocketAddress remote = new InetSocketAddress("10.1.2.3", 5000);

        String clientId = interceptor.resolveClientId(request("query { a }", new HttpHeaders(), remote));

        assertThat(clientId).isEqualTo("10.1.2.3");
    }

    @Test
    @DisplayName("falls back to anonymous when nothing identifies the client")
    void resolvesAnonymousClientId() {
        ClientRateLimiter limiter = new ClientRateLimiter(100, 10.0, 100);
        QueryCostRateLimitInterceptor interceptor = new QueryCostRateLimitInterceptor(limiter, properties());

        String clientId = interceptor.resolveClientId(request("query { a }", new HttpHeaders(), null));

        assertThat(clientId).isEqualTo("anonymous");
    }
}
