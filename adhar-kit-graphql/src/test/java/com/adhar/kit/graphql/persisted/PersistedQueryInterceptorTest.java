package com.adhar.kit.graphql.persisted;

import graphql.ExecutionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PersistedQueryInterceptor}.
 *
 * <p>Uses a mocked {@link WebGraphQlRequest} (per-getter stubbed, as the class is a
 * concrete Spring type) to exercise the Apollo Automatic Persisted Query protocol.</p>
 */
class PersistedQueryInterceptorTest {

    private static final String HELLO_QUERY = "query { hello }";

    private InMemoryPersistedQueryCache cache;
    private PersistedQueryInterceptor interceptor;
    private final WebGraphQlResponse mockResponse = mock(WebGraphQlResponse.class);

    @BeforeEach
    void setUp() {
        cache = new InMemoryPersistedQueryCache(10);
        interceptor = new PersistedQueryInterceptor(cache);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a stubbed mock WebGraphQlRequest sufficient for the interceptor to operate on. */
    private WebGraphQlRequest request(String document, Map<String, Object> extensions) {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        when(request.getDocument()).thenReturn(document);
        when(request.getExtensions()).thenReturn(extensions);
        when(request.getOperationName()).thenReturn(null);
        when(request.getVariables()).thenReturn(Map.of());
        when(request.getUri()).thenReturn(UriComponentsBuilder.fromUriString("https://example.org/graphql").build());
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getCookies()).thenReturn(new LinkedMultiValueMap<>());
        when(request.getRemoteAddress()).thenReturn(null);
        when(request.getAttributes()).thenReturn(Map.of());
        when(request.getId()).thenReturn("exec-1");
        when(request.getLocale()).thenReturn(Locale.US);
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
    @DisplayName("passes through unchanged when there is no persistedQuery extension")
    void passesThroughWithoutExtension() {
        WebGraphQlRequest request = request(HELLO_QUERY, Map.of());
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isSameAs(request);
    }

    @Test
    @DisplayName("passes through unchanged when persistedQuery extension has no sha256Hash")
    void passesThroughWithoutHash() {
        WebGraphQlRequest request = request(HELLO_QUERY, Map.of("persistedQuery", Map.of()));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isSameAs(request);
    }

    @Test
    @DisplayName("hash-only request with a cache hit resolves the query and continues the chain")
    void hashOnlyCacheHit() {
        String hash = sha256(HELLO_QUERY);
        cache.put(hash, HELLO_QUERY);
        WebGraphQlRequest request = request(null, Map.of("persistedQuery", Map.of("sha256Hash", hash)));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getDocument()).isEqualTo(HELLO_QUERY);
    }

    @Test
    @DisplayName("hash-only request with a cache miss returns PersistedQueryNotFound without calling the chain")
    void hashOnlyCacheMiss() {
        String hash = sha256(HELLO_QUERY);
        WebGraphQlRequest request = request(null, Map.of("persistedQuery", Map.of("sha256Hash", hash)));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(PersistedQueryInterceptor.PERSISTED_QUERY_NOT_FOUND);
        assertThat(captured.get()).as("chain must not be invoked on a persisted-query miss").isNull();
    }

    @Test
    @DisplayName("query + matching hash caches the query and continues the chain")
    void queryWithMatchingHashCachesAndContinues() {
        String hash = sha256(HELLO_QUERY);
        WebGraphQlRequest request = request(HELLO_QUERY, Map.of("persistedQuery", Map.of("sha256Hash", hash)));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        assertThat(result.block()).isSameAs(mockResponse);
        assertThat(captured.get()).isSameAs(request);
        assertThat(cache.get(hash)).contains(HELLO_QUERY);
    }

    @Test
    @DisplayName("query + mismatching hash is rejected without caching or calling the chain")
    void queryWithMismatchingHashRejected() {
        WebGraphQlRequest request = request(HELLO_QUERY, Map.of("persistedQuery", Map.of("sha256Hash", "not-the-real-hash")));
        AtomicReference<WebGraphQlRequest> captured = new AtomicReference<>();

        Mono<WebGraphQlResponse> result = interceptor.intercept(request, capturingChain(captured));

        WebGraphQlResponse response = result.block();
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().getFirst().getMessage())
                .isEqualTo(PersistedQueryInterceptor.HASH_MISMATCH_MESSAGE);
        assertThat(captured.get()).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("constructor rejects a null cache")
    void constructorRejectsNullCache() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PersistedQueryInterceptor(null))
                .isInstanceOf(NullPointerException.class);
    }
}
