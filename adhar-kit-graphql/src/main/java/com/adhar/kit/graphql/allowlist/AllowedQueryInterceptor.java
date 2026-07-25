package com.adhar.kit.graphql.allowlist;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link WebGraphQlInterceptor} that rejects any GraphQL request whose query is not
 * registered in the {@link AllowedQueryRegistry}.
 *
 * <p>A request is allowed when any of the following match:</p>
 * <ul>
 *   <li>The Apollo APQ {@code extensions.persistedQuery.sha256Hash} value is a
 *       registered hash (integrating with the existing
 *       {@link com.adhar.kit.graphql.persisted.PersistedQueryInterceptor APQ interceptor},
 *       hash-only requests are checked without needing the resolved document);</li>
 *   <li>the SHA-256 hash of the request document matches a registered query;</li>
 *   <li>the request's operation name is registered.</li>
 * </ul>
 *
 * <p>Everything else is short-circuited before execution with a
 * {@code QueryNotAllowed} error.</p>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   graphql:
 *     allow-list:
 *       enabled: true
 *       location: graphql/allowed-queries
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AllowedQueryInterceptor implements WebGraphQlInterceptor {

    /** Error message returned for a rejected, non-allow-listed query. */
    public static final String QUERY_NOT_ALLOWED = "QueryNotAllowed";

    /** Error code carried in the error extensions for rejected queries. */
    public static final String QUERY_NOT_ALLOWED_CODE = "QUERY_NOT_ALLOWED";

    private static final String EXTENSION_KEY = "persistedQuery";
    private static final String HASH_KEY = "sha256Hash";

    private final AllowedQueryRegistry registry;

    /**
     * Creates a new interceptor backed by the given registry.
     *
     * @param registry the allow-list registry
     */
    public AllowedQueryInterceptor(AllowedQueryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Optional<String> apqHash = extractApqHash(request);
        if (apqHash.isPresent() && registry.isAllowedHash(apqHash.get())) {
            return chain.next(request);
        }

        String document = request.getDocument();
        if (document != null && !document.isBlank()) {
            if (registry.isAllowedQuery(document)) {
                return chain.next(request);
            }
            String operationName = resolveOperationName(request, document);
            if (operationName != null && registry.isAllowedOperationName(operationName)) {
                return chain.next(request);
            }
        }

        log.warn("Rejected non-allow-listed GraphQL query (operationName: {})", request.getOperationName());
        return errorResponse(request);
    }

    private String resolveOperationName(WebGraphQlRequest request, String document) {
        if (request.getOperationName() != null && !request.getOperationName().isBlank()) {
            return request.getOperationName();
        }
        try {
            Document parsed = new Parser().parseDocument(document);
            for (Definition<?> definition : parsed.getDefinitions()) {
                if (definition instanceof OperationDefinition operation && operation.getName() != null) {
                    return operation.getName();
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse document for operation-name allow-list matching: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractApqHash(WebGraphQlRequest request) {
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

    private Mono<WebGraphQlResponse> errorResponse(WebGraphQlRequest request) {
        GraphQLError error = GraphQLError.newError()
                .message(QUERY_NOT_ALLOWED)
                .extensions(Map.of("code", QUERY_NOT_ALLOWED_CODE))
                .build();
        ExecutionResult result = ExecutionResult.newExecutionResult().addError(error).build();
        ExecutionInput input = request.toExecutionInput();
        ExecutionGraphQlResponse executionResponse = new DefaultExecutionGraphQlResponse(input, result);
        return Mono.just(new WebGraphQlResponse(executionResponse));
    }
}
