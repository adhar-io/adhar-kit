package com.adhar.kit.graphql.persisted;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link WebGraphQlInterceptor} implementing Apollo's Automatic Persisted Queries (APQ)
 * protocol.
 *
 * <p>Clients send an {@code extensions.persistedQuery.sha256Hash} value with every
 * request:</p>
 * <ul>
 *   <li><b>Hash only (no {@code query})</b>: the server looks up the hash in the
 *       {@link PersistedQueryCache}. If found, the cached query is substituted and the
 *       request proceeds normally. If not found, the request is short-circuited with a
 *       {@code PersistedQueryNotFound} error &mdash; the well-known signal that tells
 *       Apollo Client to retry with the full query text attached.</li>
 *   <li><b>Hash + query</b>: the server recomputes the SHA-256 of the supplied query and
 *       verifies it matches the supplied hash. On success, the query is cached under
 *       that hash for future hash-only requests and the request proceeds. On mismatch,
 *       the request is rejected.</li>
 * </ul>
 *
 * <p>Requests without a {@code persistedQuery} extension pass through unchanged.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class PersistedQueryInterceptor implements WebGraphQlInterceptor {

    /** Apollo APQ well-known error message for a hash-only miss. */
    public static final String PERSISTED_QUERY_NOT_FOUND = "PersistedQueryNotFound";

    /** Apollo APQ well-known error message for a hash/query mismatch. */
    public static final String HASH_MISMATCH_MESSAGE = "provided sha does not match query";

    private static final String EXTENSION_KEY = "persistedQuery";
    private static final String HASH_KEY = "sha256Hash";

    private final PersistedQueryCache cache;

    /**
     * Creates a new interceptor backed by the given cache.
     *
     * @param cache the persisted query cache to read from and write to
     */
    public PersistedQueryInterceptor(PersistedQueryCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Optional<String> hash = extractHash(request);
        if (hash.isEmpty()) {
            return chain.next(request);
        }

        String sha256Hash = hash.get();
        String document = request.getDocument();

        if (document == null || document.isBlank()) {
            return cache.get(sha256Hash)
                    .map(cachedQuery -> chain.next(withDocument(request, cachedQuery)))
                    .orElseGet(() -> {
                        log.debug("Persisted query miss for hash {}", sha256Hash);
                        return errorResponse(request, PERSISTED_QUERY_NOT_FOUND, "PERSISTED_QUERY_NOT_FOUND");
                    });
        }

        String computedHash = sha256Hex(document);
        if (!computedHash.equalsIgnoreCase(sha256Hash)) {
            log.warn("Persisted query hash mismatch: expected {}, computed {}", sha256Hash, computedHash);
            return errorResponse(request, HASH_MISMATCH_MESSAGE, "PERSISTED_QUERY_HASH_MISMATCH");
        }

        cache.put(sha256Hash, document);
        return chain.next(request);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractHash(WebGraphQlRequest request) {
        Map<String, Object> extensions = request.getExtensions();
        if (extensions == null) {
            return Optional.empty();
        }
        Object persistedQuery = extensions.get(EXTENSION_KEY);
        if (!(persistedQuery instanceof Map<?, ?> persistedQueryMap)) {
            return Optional.empty();
        }
        Object hashValue = ((Map<String, Object>) persistedQueryMap).get(HASH_KEY);
        if (!(hashValue instanceof String hashString) || hashString.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(hashString);
    }

    private WebGraphQlRequest withDocument(WebGraphQlRequest original, String document) {
        GraphQlRequest resolved = new DefaultGraphQlRequest(
                document, original.getOperationName(), original.getVariables(), original.getExtensions());
        return new WebGraphQlRequest(
                original.getUri().toUri(),
                original.getHeaders(),
                original.getCookies(),
                original.getRemoteAddress(),
                original.getAttributes(),
                resolved,
                original.getId(),
                original.getLocale());
    }

    private Mono<WebGraphQlResponse> errorResponse(WebGraphQlRequest request, String message, String code) {
        GraphQLError error = GraphQLError.newError()
                .message(message)
                .extensions(Map.of("code", code))
                .build();
        ExecutionResult result = ExecutionResult.newExecutionResult().addError(error).build();
        ExecutionInput input = request.toExecutionInput();
        ExecutionGraphQlResponse executionResponse = new DefaultExecutionGraphQlResponse(input, result);
        return Mono.just(new WebGraphQlResponse(executionResponse));
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
