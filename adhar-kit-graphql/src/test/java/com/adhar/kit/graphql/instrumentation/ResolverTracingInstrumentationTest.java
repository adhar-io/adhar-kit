package com.adhar.kit.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResolverTracingInstrumentation}.
 */
class ResolverTracingInstrumentationTest {

    private static final String SDL = """
            type Query {
              hello: String
              slow: String
              nested: Nested
            }
            type Nested {
              value: String
            }
            """;

    private GraphQL graphQl(ResolverTracingInstrumentation instrumentation) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("hello", env -> "world")
                        .dataFetcher("slow", env -> CompletableFuture.completedFuture("done"))
                        .dataFetcher("nested", env -> Map.of("value", "v")))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(schema).instrumentation(instrumentation).build();
    }

    @Test
    @DisplayName("records a Micrometer timer per non-trivial resolver invocation")
    void recordsTimers() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ResolverTracingInstrumentation instrumentation =
                new ResolverTracingInstrumentation(meterRegistry, false, false);

        ExecutionResult result = graphQl(instrumentation).execute("{ hello slow }");
        assertThat(result.getErrors()).isEmpty();

        Timer helloTimer = meterRegistry.find(ResolverTracingInstrumentation.TIMER_NAME)
                .tag("field", "hello").timer();
        assertThat(helloTimer).isNotNull();
        assertThat(helloTimer.count()).isEqualTo(1);

        Timer slowTimer = meterRegistry.find(ResolverTracingInstrumentation.TIMER_NAME)
                .tag("field", "slow").timer();
        assertThat(slowTimer).isNotNull();
        assertThat(slowTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("does not time trivial property fetchers by default but does when enabled")
    void trivialFetcherToggle() {
        SimpleMeterRegistry excluding = new SimpleMeterRegistry();
        graphQl(new ResolverTracingInstrumentation(excluding, false, false)).execute("{ nested { value } }");
        Timer excludedValueTimer = excluding.find(ResolverTracingInstrumentation.TIMER_NAME)
                .tag("field", "value").timer();
        assertThat(excludedValueTimer).isNull();

        SimpleMeterRegistry including = new SimpleMeterRegistry();
        graphQl(new ResolverTracingInstrumentation(including, false, true)).execute("{ nested { value } }");
        Timer includedValueTimer = including.find(ResolverTracingInstrumentation.TIMER_NAME)
                .tag("field", "value").timer();
        assertThat(includedValueTimer).isNotNull();
        assertThat(includedValueTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("adds an Apollo-style tracing extension only when enabled")
    void apolloTracingExtension() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        ExecutionResult without = graphQl(
                new ResolverTracingInstrumentation(meterRegistry, false, false)).execute("{ hello }");
        assertThat(without.getExtensions() == null || !without.getExtensions().containsKey("tracing")).isTrue();

        ExecutionResult with = graphQl(
                new ResolverTracingInstrumentation(meterRegistry, true, false)).execute("{ hello nested { value } }");
        assertThat(with.getExtensions()).isNotNull();
        assertThat(with.getExtensions()).containsKey(ResolverTracingInstrumentation.TRACING_EXTENSION);

        @SuppressWarnings("unchecked")
        Map<String, Object> tracing = (Map<String, Object>) with.getExtensions()
                .get(ResolverTracingInstrumentation.TRACING_EXTENSION);
        assertThat(tracing).containsKeys("version", "startTime", "endTime", "duration", "execution");

        @SuppressWarnings("unchecked")
        Map<String, Object> execution = (Map<String, Object>) tracing.get("execution");
        assertThat((java.util.List<?>) execution.get("resolvers")).isNotEmpty();
    }
}
