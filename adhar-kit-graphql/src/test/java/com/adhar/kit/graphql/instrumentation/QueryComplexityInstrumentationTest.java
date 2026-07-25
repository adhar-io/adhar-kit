package com.adhar.kit.graphql.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryComplexityInstrumentation}.
 *
 * <p>These tests exercise the instrumentation through a real graphql-java engine
 * (rather than calling internal lifecycle methods directly) specifically to prove that
 * rejection happens <strong>before</strong> execution: an over-limit query must never
 * cause a single {@code DataFetcher} invocation.</p>
 */
class QueryComplexityInstrumentationTest {

    private static final String SDL = """
            type Query {
                user: User
            }
            type User {
                id: ID
                name: String
                address: Address
            }
            type Address {
                city: String
                country: String
            }
            """;

    /**
     * Builds an executable schema whose "user" field data fetcher increments the given
     * counter every time it is invoked, so tests can assert whether execution reached
     * the data-fetching phase at all.
     */
    private GraphQLSchema schema(AtomicInteger userFetchCount) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
        DataFetcher<Map<String, Object>> userFetcher = env -> {
            userFetchCount.incrementAndGet();
            return Map.of(
                    "id", "1",
                    "name", "Ada",
                    "address", Map.of("city", "Metropolis", "country", "Freedonia"));
        };
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("user", userFetcher))
                .type(newTypeWiring("User").dataFetcher("id", new StaticDataFetcher("1")))
                .build();
        return new SchemaGenerator().makeExecutableSchema(registry, wiring);
    }

    private GraphQL graphQl(GraphQLSchema schema, QueryComplexityInstrumentation instrumentation) {
        return GraphQL.newGraphQL(schema).instrumentation(instrumentation).build();
    }

    @Test
    @DisplayName("query within limits executes and invokes data fetchers")
    void withinLimitsExecutes() {
        AtomicInteger userFetchCount = new AtomicInteger();
        GraphQL graphQl = graphQl(schema(userFetchCount), new QueryComplexityInstrumentation(100, 10));

        ExecutionResult result = graphQl.execute(
                ExecutionInput.newExecutionInput("query { user { id name } }").build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(userFetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("query exceeding complexity is rejected before any DataFetcher runs")
    void complexityExceededRejectsPreExecution() {
        AtomicInteger userFetchCount = new AtomicInteger();
        // "user { id name }" has complexity 3 (user + id + name); cap at 1 to force rejection.
        GraphQL graphQl = graphQl(schema(userFetchCount), new QueryComplexityInstrumentation(1, 10));

        ExecutionResult result = graphQl.execute(
                ExecutionInput.newExecutionInput("query { user { id name } }").build());

        assertThat(result.getErrors()).isNotEmpty();
        assertThat(userFetchCount.get())
                .as("DataFetcher must not be invoked once the complexity limit is exceeded")
                .isZero();
    }

    @Test
    @DisplayName("query exceeding depth is rejected before any DataFetcher runs")
    void depthExceededRejectsPreExecution() {
        AtomicInteger userFetchCount = new AtomicInteger();
        // "user { address { city } }" has depth 3; cap at 1 to force rejection.
        GraphQL graphQl = graphQl(schema(userFetchCount), new QueryComplexityInstrumentation(1000, 1));

        ExecutionResult result = graphQl.execute(
                ExecutionInput.newExecutionInput("query { user { address { city } } }").build());

        assertThat(result.getErrors()).isNotEmpty();
        assertThat(userFetchCount.get())
                .as("DataFetcher must not be invoked once the depth limit is exceeded")
                .isZero();
    }

    @Test
    @DisplayName("nested query within both limits executes and fetches once")
    void nestedWithinLimitsExecutes() {
        AtomicInteger userFetchCount = new AtomicInteger();
        GraphQL graphQl = graphQl(schema(userFetchCount), new QueryComplexityInstrumentation(100, 10));

        ExecutionResult result = graphQl.execute(
                ExecutionInput.newExecutionInput("query { user { id address { city country } } }").build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(userFetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("exposes the configured maximum complexity and depth")
    void exposesConfiguredLimits() {
        QueryComplexityInstrumentation instrumentation = new QueryComplexityInstrumentation(250, 12);

        assertThat(instrumentation.getMaxComplexity()).isEqualTo(250);
        assertThat(instrumentation.getMaxDepth()).isEqualTo(12);
    }

    @Test
    @DisplayName("chains both the complexity and depth delegate instrumentations")
    void chainsBothDelegates() {
        QueryComplexityInstrumentation instrumentation = new QueryComplexityInstrumentation(100, 10);

        assertThat(instrumentation.getInstrumentations()).hasSize(2);
    }
}
