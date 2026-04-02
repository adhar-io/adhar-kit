package com.adhar.kit.graphql.security;

import com.adhar.kit.graphql.config.GraphQlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Security interceptor for GraphQL requests.
 *
 * <p>Provides request-level security checks that run before query execution:</p>
 * <ul>
 *   <li>Authentication enforcement when {@code adhar.graphql.security.require-authentication} is enabled</li>
 *   <li>Introspection query blocking when {@code adhar.graphql.introspection-enabled} is false</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   graphql:
 *     introspection-enabled: false
 *     security:
 *       require-authentication: true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GraphQlSecurityInterceptor implements WebGraphQlInterceptor {

    private static final String INTROSPECTION_FIELD = "__schema";
    private static final String INTROSPECTION_TYPE_FIELD = "__type";

    private final GraphQlProperties properties;

    /**
     * Creates a new security interceptor with the given properties.
     *
     * @param properties the GraphQL configuration properties
     */
    public GraphQlSecurityInterceptor(GraphQlProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String document = request.getDocument();

        // Block introspection queries when disabled
        if (!properties.isIntrospectionEnabled() && isIntrospectionQuery(document)) {
            log.warn("Introspection query blocked (introspection is disabled)");
            return Mono.error(new SecurityException("GraphQL introspection is disabled"));
        }

        // Enforce authentication when configured
        if (properties.getSecurity().isRequireAuthentication()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthenticated GraphQL request blocked");
                return Mono.error(new SecurityException("Authentication is required for GraphQL requests"));
            }
            log.debug("Authenticated GraphQL request from principal: {}", authentication.getName());
        }

        return chain.next(request);
    }

    /**
     * Checks whether the given GraphQL document contains an introspection query.
     *
     * <p>Introspection queries are identified by the presence of {@code __schema}
     * or {@code __type} fields in the document text.</p>
     *
     * @param document the GraphQL query document
     * @return true if the document appears to be an introspection query
     */
    private boolean isIntrospectionQuery(String document) {
        if (document == null || document.isBlank()) {
            return false;
        }
        return document.contains(INTROSPECTION_FIELD) || document.contains(INTROSPECTION_TYPE_FIELD);
    }
}
