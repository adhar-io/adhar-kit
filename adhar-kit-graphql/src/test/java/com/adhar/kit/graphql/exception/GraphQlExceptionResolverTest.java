package com.adhar.kit.graphql.exception;

import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.graphql.execution.ErrorType;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GraphQlExceptionResolver}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphQlExceptionResolverTest {

    private final GraphQlExceptionResolver resolver = new GraphQlExceptionResolver();

    @Mock
    private DataFetchingEnvironment env;
    @Mock
    private ExecutionStepInfo stepInfo;

    @BeforeEach
    void setUp() {
        Field field = Field.newField("myField").build();
        when(env.getField()).thenReturn(field);
        lenient().when(env.getExecutionStepInfo()).thenReturn(stepInfo);
        lenient().when(stepInfo.getPath()).thenReturn(ResultPath.rootPath());
    }

    private GraphQLError resolve(Throwable ex) {
        return resolver.resolveToSingleError(ex, env);
    }

    @Test
    @DisplayName("IllegalArgumentException maps to BAD_REQUEST and keeps message")
    void illegalArgument() {
        GraphQLError error = resolve(new IllegalArgumentException("bad id"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("bad id");
    }

    @Test
    @DisplayName("SecurityException maps to UNAUTHORIZED with generic message")
    void security() {
        GraphQLError error = resolve(new SecurityException("token expired"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        assertThat(error.getMessage()).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("UnsupportedOperationException maps to FORBIDDEN")
    void unsupported() {
        GraphQLError error = resolve(new UnsupportedOperationException("nope"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        assertThat(error.getMessage()).isEqualTo("Operation not permitted");
    }

    @Test
    @DisplayName("NoSuchElement-style exception maps to NOT_FOUND")
    void notFoundBySuffix() {
        GraphQLError error = resolve(new NoSuchElementException("missing user"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("missing user");
    }

    @Test
    @DisplayName("custom *NotFoundException maps to NOT_FOUND")
    void notFoundByClassName() {
        GraphQLError error = resolve(new UserNotFoundException("no user 5"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("no user 5");
    }

    @Test
    @DisplayName("unknown exception maps to INTERNAL_ERROR with masked message")
    void internalError() {
        GraphQLError error = resolve(new RuntimeException("db connection blew up"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(error.getMessage()).isEqualTo("An internal error occurred");
    }

    @Test
    @DisplayName("CompletionException is unwrapped to its cause")
    void unwrapsCompletionException() {
        CompletionException wrapped = new CompletionException(new IllegalArgumentException("inner bad"));

        GraphQLError error = resolve(wrapped);

        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("inner bad");
    }

    @Test
    @DisplayName("CompletionException with null cause is treated as itself (internal error)")
    void completionExceptionNullCause() {
        GraphQLError error = resolve(new CompletionException("no cause", null));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
    }

    /** Test-only exception whose simple name contains "NotFoundException". */
    private static final class UserNotFoundException extends RuntimeException {
        UserNotFoundException(String message) {
            super(message);
        }
    }
}
