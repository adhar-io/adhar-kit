package com.adhar.kit.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * GraphQL instrumentation that enforces query complexity limits.
 *
 * <p>Calculates the complexity of incoming GraphQL queries based on field depth
 * and breadth, rejecting queries that exceed the configured maximum complexity.</p>
 *
 * <p>Complexity is calculated by counting the total number of fields requested
 * across all selection sets in the query. Each field contributes a complexity
 * score of 1, and nested fields compound the total.</p>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   graphql:
 *     max-query-complexity: 200
 *     max-query-depth: 10
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class QueryComplexityInstrumentation extends SimplePerformantInstrumentation {

    private final int maxComplexity;
    private final int maxDepth;

    /**
     * Creates a new instrumentation with the specified limits.
     *
     * @param maxComplexity the maximum allowed query complexity score
     * @param maxDepth      the maximum allowed query depth
     */
    public QueryComplexityInstrumentation(int maxComplexity, int maxDepth) {
        this.maxComplexity = maxComplexity;
        this.maxDepth = maxDepth;
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult,
            InstrumentationExecutionParameters parameters,
            InstrumentationState state) {

        String query = parameters.getQuery();
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(executionResult);
        }

        Document document;
        try {
            document = Parser.parse(query);
        } catch (Exception e) {
            log.warn("Failed to parse query for complexity analysis: {}", e.getMessage());
            return CompletableFuture.completedFuture(executionResult);
        }

        int complexity = 0;
        int depth = 0;

        for (var definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operationDef) {
                SelectionSet selectionSet = operationDef.getSelectionSet();
                if (selectionSet != null) {
                    complexity += calculateComplexity(selectionSet);
                    depth = Math.max(depth, calculateDepth(selectionSet));
                }
            }
        }

        if (complexity > maxComplexity) {
            log.warn("Query rejected: complexity {} exceeds maximum {}", complexity, maxComplexity);
            GraphQLError error = GraphQLError.newError()
                    .message("Query complexity %d exceeds the maximum allowed complexity of %d"
                            .formatted(complexity, maxComplexity))
                    .build();
            return CompletableFuture.completedFuture(
                    ExecutionResult.newExecutionResult()
                            .addError(error)
                            .build());
        }

        if (depth > maxDepth) {
            log.warn("Query rejected: depth {} exceeds maximum {}", depth, maxDepth);
            GraphQLError error = GraphQLError.newError()
                    .message("Query depth %d exceeds the maximum allowed depth of %d"
                            .formatted(depth, maxDepth))
                    .build();
            return CompletableFuture.completedFuture(
                    ExecutionResult.newExecutionResult()
                            .addError(error)
                            .build());
        }

        log.debug("Query accepted: complexity={}, depth={}", complexity, depth);
        return CompletableFuture.completedFuture(executionResult);
    }

    /**
     * Recursively calculates the total field count (complexity) of a selection set.
     */
    private int calculateComplexity(SelectionSet selectionSet) {
        int complexity = 0;
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                complexity++;
                if (field.getSelectionSet() != null) {
                    complexity += calculateComplexity(field.getSelectionSet());
                }
            }
        }
        return complexity;
    }

    /**
     * Recursively calculates the maximum nesting depth of a selection set.
     */
    private int calculateDepth(SelectionSet selectionSet) {
        int maxChildDepth = 0;
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field && field.getSelectionSet() != null) {
                int childDepth = calculateDepth(field.getSelectionSet());
                maxChildDepth = Math.max(maxChildDepth, childDepth);
            }
        }
        return maxChildDepth + 1;
    }
}
