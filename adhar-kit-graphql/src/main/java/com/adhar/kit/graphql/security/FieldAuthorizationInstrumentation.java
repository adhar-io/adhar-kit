package com.adhar.kit.graphql.security;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Field-level authorization instrumentation driven by a schema directive.
 *
 * <p>Fields annotated in the schema with the {@code @auth(roles: [...])} directive are
 * protected: before the field's {@link DataFetcher} runs, the roles carried by the
 * current Spring Security {@link Authentication} are checked against the directive's
 * required roles. If the caller holds none of them the fetcher is replaced by one that
 * throws a {@link SecurityException}, which the module's exception resolver maps to an
 * {@code UNAUTHORIZED} GraphQL error for that field.</p>
 *
 * <p>Declare the directive in your SDL, e.g.:</p>
 * <pre>{@code
 * directive @auth(roles: [String!]) on FIELD_DEFINITION
 *
 * type Query {
 *   adminReport: Report @auth(roles: ["ADMIN"])
 * }
 * }</pre>
 *
 * <p>Role matching is prefix-insensitive: a directive role {@code "ADMIN"} matches a
 * granted authority of either {@code "ADMIN"} or {@code "ROLE_ADMIN"}. A field with the
 * directive but an empty role list only requires an authenticated caller.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class FieldAuthorizationInstrumentation extends SimplePerformantInstrumentation {

    /** Name of the schema directive that guards a field. */
    public static final String AUTH_DIRECTIVE = "auth";

    /** Name of the directive argument holding the allowed roles. */
    public static final String ROLES_ARGUMENT = "roles";

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
                                                InstrumentationFieldFetchParameters parameters,
                                                InstrumentationState state) {
        GraphQLFieldDefinition field = parameters.getField();
        GraphQLAppliedDirective authDirective = field.getAppliedDirective(AUTH_DIRECTIVE);
        if (authDirective == null) {
            return dataFetcher;
        }
        List<String> requiredRoles = extractRoles(authDirective);
        String fieldName = field.getName();
        return environment -> {
            if (!isAuthorized(requiredRoles)) {
                log.warn("Access denied to field '{}' (requires roles {})", fieldName, requiredRoles);
                throw new SecurityException("Access denied to field '" + fieldName + "'");
            }
            return dataFetcher.get(environment);
        };
    }

    /**
     * Determines whether the current security context satisfies the required roles.
     *
     * @param requiredRoles the roles required by the field (empty means "any authenticated user")
     * @return true if authorized
     */
    boolean isAuthorized(List<String> requiredRoles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (requiredRoles.isEmpty()) {
            return true;
        }
        Set<String> granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null)
                .map(FieldAuthorizationInstrumentation::normalize)
                .collect(Collectors.toSet());
        return requiredRoles.stream()
                .map(FieldAuthorizationInstrumentation::normalize)
                .anyMatch(granted::contains);
    }

    private static String normalize(String role) {
        String trimmed = role.trim();
        return trimmed.startsWith(ROLE_PREFIX) ? trimmed.substring(ROLE_PREFIX.length()) : trimmed;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument argument = directive.getArgument(ROLES_ARGUMENT);
        if (argument == null) {
            return List.of();
        }
        Object value = argument.getValue();
        List<String> roles = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) {
                    roles.add(element.toString());
                }
            }
        } else if (value instanceof String single && !single.isBlank()) {
            roles.add(single);
        }
        return roles;
    }
}
