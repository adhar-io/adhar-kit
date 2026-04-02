package com.adhar.kit.graphql.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;

import java.util.concurrent.CompletionException;

/**
 * Centralized exception resolver for GraphQL data fetcher exceptions.
 *
 * <p>Converts Java exceptions into structured GraphQL errors with appropriate
 * error classifications, preventing internal details from leaking to clients.</p>
 *
 * <p><b>Mapped Exception Types:</b></p>
 * <ul>
 *   <li>{@link IllegalArgumentException} - {@code BAD_REQUEST}</li>
 *   <li>{@link SecurityException} - {@code UNAUTHORIZED}</li>
 *   <li>{@link UnsupportedOperationException} - {@code FORBIDDEN}</li>
 *   <li>{@link jakarta.persistence.EntityNotFoundException} and similar - {@code NOT_FOUND}</li>
 *   <li>All other exceptions - {@code INTERNAL_ERROR}</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        // Unwrap CompletionException to get the root cause
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                ? ex.getCause()
                : ex;

        String fieldName = env.getField().getName();

        return switch (cause) {
            case IllegalArgumentException _ -> {
                log.warn("Bad request in GraphQL field '{}': {}", fieldName, cause.getMessage());
                yield buildError(cause.getMessage(), ErrorType.BAD_REQUEST, env);
            }
            case SecurityException _ -> {
                log.warn("Unauthorized access in GraphQL field '{}': {}", fieldName, cause.getMessage());
                yield buildError("Access denied", ErrorType.UNAUTHORIZED, env);
            }
            case UnsupportedOperationException _ -> {
                log.warn("Forbidden operation in GraphQL field '{}': {}", fieldName, cause.getMessage());
                yield buildError("Operation not permitted", ErrorType.FORBIDDEN, env);
            }
            default -> {
                // Check for common "not found" exception class names to avoid hard dependency
                if (isNotFoundException(cause)) {
                    log.warn("Resource not found in GraphQL field '{}': {}", fieldName, cause.getMessage());
                    yield buildError(cause.getMessage(), ErrorType.NOT_FOUND, env);
                }
                log.error("Internal error in GraphQL field '{}': {}", fieldName, cause.getMessage(), cause);
                yield buildError("An internal error occurred", ErrorType.INTERNAL_ERROR, env);
            }
        };
    }

    /**
     * Builds a structured GraphQL error with the specified classification.
     */
    private GraphQLError buildError(String message, ErrorType errorType, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(errorType)
                .build();
    }

    /**
     * Checks whether the exception represents a "not found" scenario by inspecting
     * the class name hierarchy. This avoids requiring JPA or other persistence
     * dependencies on the classpath.
     */
    private boolean isNotFoundException(Throwable cause) {
        String className = cause.getClass().getSimpleName();
        return className.contains("NotFoundException")
                || className.contains("EntityNotFoundException")
                || className.contains("NoSuchElement");
    }
}
