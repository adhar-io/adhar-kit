package com.adhar.kit.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryComplexityInstrumentation}.
 */
class QueryComplexityInstrumentationTest {

    private final ExecutionResult passingResult =
            ExecutionResult.newExecutionResult().data("ok").build();

    private InstrumentationExecutionParameters params(String query) {
        InstrumentationExecutionParameters p = mock(InstrumentationExecutionParameters.class);
        when(p.getQuery()).thenReturn(query);
        return p;
    }

    private ExecutionResult run(QueryComplexityInstrumentation instr, String query) {
        CompletableFuture<ExecutionResult> future =
                instr.instrumentExecutionResult(passingResult, params(query), null);
        return future.join();
    }

    @Test
    @DisplayName("simple query within limits passes unchanged")
    void simpleQueryPasses() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        ExecutionResult result = run(instr, "query { user { id name } }");

        assertThat(result).isSameAs(passingResult);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("null query is passed through unchanged")
    void nullQueryPasses() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        ExecutionResult result = run(instr, null);

        assertThat(result).isSameAs(passingResult);
    }

    @Test
    @DisplayName("blank query is passed through unchanged")
    void blankQueryPasses() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        ExecutionResult result = run(instr, "   ");

        assertThat(result).isSameAs(passingResult);
    }

    @Test
    @DisplayName("unparseable query is passed through unchanged")
    void unparseableQueryPasses() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        ExecutionResult result = run(instr, "this is not valid graphql {{{");

        assertThat(result).isSameAs(passingResult);
    }

    @Test
    @DisplayName("query exceeding complexity limit is rejected with an error")
    void complexityExceeded() {
        // complexity 1 = only count fields; limit 0 forces rejection for any field
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(0, 10);

        ExecutionResult result = run(instr, "query { user { id } }");

        assertThat(result).isNotSameAs(passingResult);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .contains("complexity")
                .contains("exceeds the maximum allowed complexity");
    }

    @Test
    @DisplayName("query exceeding depth limit is rejected with an error")
    void depthExceeded() {
        // generous complexity so only depth triggers; depth limit 1
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(1000, 1);

        ExecutionResult result = run(instr, "query { user { address { city } } }");

        assertThat(result).isNotSameAs(passingResult);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage())
                .contains("depth")
                .contains("exceeds the maximum allowed depth");
    }

    @Test
    @DisplayName("nested query within both limits passes")
    void nestedWithinLimits() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        ExecutionResult result =
                run(instr, "query { user { id profile { bio avatar } posts { title } } }");

        assertThat(result).isSameAs(passingResult);
    }

    @Test
    @DisplayName("query with no operation definition passes")
    void onlyFragmentDefinition() {
        QueryComplexityInstrumentation instr = new QueryComplexityInstrumentation(100, 10);

        // a lone fragment definition has no OperationDefinition selection set
        ExecutionResult result = run(instr, "fragment F on User { id name }");

        assertThat(result).isSameAs(passingResult);
    }
}
