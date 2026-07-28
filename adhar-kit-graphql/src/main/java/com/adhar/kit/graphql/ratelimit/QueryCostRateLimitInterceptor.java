package com.adhar.kit.graphql.ratelimit;

import com.adhar.kit.graphql.config.GraphQlProperties;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/**
 * {@link WebGraphQlInterceptor} that enforces per-client cost-based rate limiting
 * <strong>before</strong> execution.
 *
 * <p>For each request the estimated query cost is computed with
 * {@link QueryCostEstimator} (the same field-count metric enforced by the query
 * complexity instrumentation) and charged against the client's {@link TokenBucket}
 * via the {@link ClientRateLimiter}. When the bucket lacks sufficient tokens the
 * request is short-circuited with a {@code RateLimited} error and never executed.</p>
 *
 * <p>The client identity is resolved, in order, from:</p>
 * <ol>
 *   <li>the configured client-id header ({@code adhar.graphql.rate-limit.client-id-header});</li>
 *   <li>the authenticated Spring Security principal name;</li>
 *   <li>the request's remote address;</li>
 *   <li>a shared {@code "anonymous"} bucket as a last resort.</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class QueryCostRateLimitInterceptor implements WebGraphQlInterceptor {

    /** Error message returned for a rejected, over-budget query. */
    public static final String RATE_LIMITED = "RateLimited";

    /** Error code carried in the error extensions for rate-limited queries. */
    public static final String RATE_LIMITED_CODE = "RATE_LIMITED";

    private static final String ANONYMOUS_CLIENT = "anonymous";

    private final ClientRateLimiter rateLimiter;
    private final String clientIdHeader;

    /**
     * Creates a new interceptor.
     *
     * @param rateLimiter the per-client rate limiter
     * @param properties  the GraphQL properties (for the client-id header name)
     */
    public QueryCostRateLimitInterceptor(ClientRateLimiter rateLimiter, GraphQlProperties properties) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.clientIdHeader = properties.getRateLimit().getClientIdHeader();
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        int cost = QueryCostEstimator.estimateCost(request.getDocument(), request.getOperationName());
        String clientId = resolveClientId(request);

        if (rateLimiter.tryAcquire(clientId, cost)) {
            return chain.next(request);
        }

        log.warn("Rate limit exceeded for client {} (estimated cost {})", clientId, cost);
        return errorResponse(request, cost);
    }

    /**
     * Resolves the client identifier for the given request.
     *
     * @param request the incoming request
     * @return a non-null client identifier
     */
    String resolveClientId(WebGraphQlRequest request) {
        String header = request.getHeaders() != null ? request.getHeaders().getFirst(clientIdHeader) : null;
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return ANONYMOUS_CLIENT;
    }

    private Mono<WebGraphQlResponse> errorResponse(WebGraphQlRequest request, int cost) {
        GraphQLError error = GraphQLError.newError()
                .message(RATE_LIMITED)
                .extensions(Map.of("code", RATE_LIMITED_CODE, "cost", cost))
                .build();
        ExecutionResult result = ExecutionResult.newExecutionResult().addError(error).build();
        ExecutionInput input = request.toExecutionInput();
        ExecutionGraphQlResponse executionResponse = new DefaultExecutionGraphQlResponse(input, result);
        return Mono.just(new WebGraphQlResponse(executionResponse));
    }
}
