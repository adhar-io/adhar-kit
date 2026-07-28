package com.adhar.kit.graphql.allowlist;

import graphql.ExecutionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllowedQueryInterceptor}.
 */
class AllowedQueryInterceptorTest {

    private static final String QUERY = "query GetUser { user(id: 1) { id name } }";

    private AllowedQueryRegistry registry;
    private AllowedQueryInterceptor interceptor;
    private final WebGraphQlResponse mockResponse = mock(WebGraphQlResponse.class);

    @BeforeEach
    void setUp() {
        registry = new AllowedQueryRegistry("graphql/allowed-queries");
        interceptor = new AllowedQueryInterceptor(registry);
    }

    private WebGraphQlRequest request(String document, String operationName, Map<String, Object> extensions) {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        when(request.getDocument()).thenReturn(document);
        when(request.getOperationName()).thenReturn(operationName);
        when(request.getExtensions()).thenReturn(extensions);
        when(request.toExecutionInput()).thenReturn(ExecutionInput.newExecutionInput(
                document == null || document.isBlank() ? "query { __typename }" : document).build());
        return request;
    }

    private WebGraphQlInterceptor.Chain capturingChain(AtomicReference<WebGraphQlRequest> captured) {
        return req -> {
            captured.set(req);
            return Mono.just(mockResponse);
        };
    }

    @Test
    @DisplayName("allows a request whose document exactly matches an approved query")
    void allowsByExactDocument() {
        registry.register(QUERY);
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request(QUERY, "GetUser", Map.of()), capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @DisplayName("allows a hash-only APQ request whose hash is registered")
    void allowsByApqHash() {
        String hash = registry.register(QUERY);
        Map<String, Object> extensions = Map.of("persistedQuery", Map.of("sha256Hash", hash));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request(null, null, extensions), capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @DisplayName("allows a request matched by its registered operation name")
    void allowsByOperationName() {
        registry.register(QUERY);
        // a different document body but the same (approved) operation name
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("query GetUser { user(id: 1) { id } }", "GetUser", Map.of()), capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @DisplayName("resolves the operation name from the document when the request omits it")
    void allowsByOperationNameParsedFromDocument() {
        registry.register(QUERY);
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("query GetUser { user(id: 2) { name } }", null, Map.of()), capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @DisplayName("rejects a non-allow-listed query with a QueryNotAllowed error and does not call the chain")
    void rejectsNonAllowListed() {
        registry.register(QUERY);
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("query Other { secret }", "Other", Map.of()), capturingChain(captured));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(AllowedQueryInterceptor.QUERY_NOT_ALLOWED);
        assertThat(captured.get()).as("chain must not run for a rejected query").isNull();
    }

    @Test
    @DisplayName("rejects an APQ hash that is not registered")
    void rejectsUnknownApqHash() {
        registry.register(QUERY);
        Map<String, Object> extensions = Map.of("persistedQuery", Map.of("sha256Hash", "deadbeef"));

        Mono<WebGraphQlResponse> result = interceptor.intercept(request(null, null, extensions),
                req -> Mono.just(mockResponse));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(AllowedQueryInterceptor.QUERY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("rejects an unparseable document whose operation name cannot be resolved")
    void rejectsUnparseableDocument() {
        registry.register(QUERY);

        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("{{{ not valid graphql", null, Map.of()), req -> Mono.just(mockResponse));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(AllowedQueryInterceptor.QUERY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("rejects when extensions are absent and the query is not allow-listed")
    void rejectsWithNullExtensions() {
        registry.register(QUERY);

        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("query Other { secret }", "Other", null), req -> Mono.just(mockResponse));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(AllowedQueryInterceptor.QUERY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("ignores a persistedQuery extension that carries no sha256Hash")
    void ignoresPersistedQueryWithoutHash() {
        registry.register(QUERY);
        Map<String, Object> extensions = Map.of("persistedQuery", Map.of("version", 1));

        // no hash to match on, and the document is allow-listed, so it passes through
        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request(QUERY, "GetUser", extensions), req -> Mono.just(mockResponse));

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("null registry is rejected by the constructor")
    void constructorRejectsNullRegistry() {
        assertThatThrownBy(() -> new AllowedQueryInterceptor(null)).isInstanceOf(NullPointerException.class);
    }
}
