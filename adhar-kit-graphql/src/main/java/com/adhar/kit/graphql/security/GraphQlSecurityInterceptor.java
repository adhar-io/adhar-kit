package com.adhar.kit.graphql.security;

import com.adhar.kit.graphql.config.GraphQlProperties;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
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
     * <p>The document is parsed into an AST and structurally inspected for {@code __schema}
     * or {@code __type} <em>field selections</em> (at any depth, including inside inline
     * fragments and named fragment definitions). This avoids the false positives a naive
     * substring search produces &mdash; e.g. a query with an unrelated string argument or
     * field literally containing the text {@code __schema} would not be flagged.</p>
     *
     * <p>If the document fails to parse, this falls back to a conservative substring
     * check so that a malformed-but-suspicious document is not silently let through.</p>
     *
     * @param document the GraphQL query document
     * @return true if the document appears to be an introspection query
     */
    private boolean isIntrospectionQuery(String document) {
        if (document == null || document.isBlank()) {
            return false;
        }
        try {
            Document parsed = new Parser().parseDocument(document);
            return containsIntrospectionField(parsed);
        } catch (Exception e) {
            log.debug("Falling back to substring-based introspection detection ({})", e.getMessage());
            return document.contains(INTROSPECTION_FIELD) || document.contains(INTROSPECTION_TYPE_FIELD);
        }
    }

    private boolean containsIntrospectionField(Document document) {
        for (Definition<?> definition : document.getDefinitions()) {
            SelectionSet selectionSet = null;
            if (definition instanceof OperationDefinition operationDefinition) {
                selectionSet = operationDefinition.getSelectionSet();
            } else if (definition instanceof FragmentDefinition fragmentDefinition) {
                selectionSet = fragmentDefinition.getSelectionSet();
            }
            if (selectionSet != null && selectionSetHasIntrospectionField(selectionSet)) {
                return true;
            }
        }
        return false;
    }

    private boolean selectionSetHasIntrospectionField(SelectionSet selectionSet) {
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                if (INTROSPECTION_FIELD.equals(field.getName()) || INTROSPECTION_TYPE_FIELD.equals(field.getName())) {
                    return true;
                }
                if (field.getSelectionSet() != null && selectionSetHasIntrospectionField(field.getSelectionSet())) {
                    return true;
                }
            } else if (selection instanceof InlineFragment inlineFragment
                    && inlineFragment.getSelectionSet() != null
                    && selectionSetHasIntrospectionField(inlineFragment.getSelectionSet())) {
                return true;
            }
        }
        return false;
    }
}
