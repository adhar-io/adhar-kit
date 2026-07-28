package com.adhar.kit.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Instrumentation that times every (non-trivial) {@link DataFetcher} invocation and
 * records it as a Micrometer {@code graphql.resolver} timer, tagged by parent type and
 * field name.
 *
 * <p>When Apollo-style tracing is enabled (a debug aid, controlled by
 * {@code adhar.graphql.tracing.apollo-tracing-enabled}) a {@code tracing} extension is
 * added to every response containing per-resolver start offsets and durations, following
 * the shape of the Apollo Tracing specification.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ResolverTracingInstrumentation extends SimplePerformantInstrumentation {

    /** Name of the Micrometer timer recorded per resolver invocation. */
    public static final String TIMER_NAME = "graphql.resolver";

    /** Apollo tracing extension key. */
    public static final String TRACING_EXTENSION = "tracing";

    private static final int APOLLO_TRACING_VERSION = 1;

    private final MeterRegistry meterRegistry;
    private final boolean apolloTracingEnabled;
    private final boolean includeTrivialDataFetchers;

    /**
     * Creates the instrumentation.
     *
     * @param meterRegistry              the Micrometer registry timers are recorded to
     * @param apolloTracingEnabled       whether to attach an Apollo-style tracing extension
     * @param includeTrivialDataFetchers whether to also time trivial (property) fetchers
     */
    public ResolverTracingInstrumentation(MeterRegistry meterRegistry,
                                          boolean apolloTracingEnabled,
                                          boolean includeTrivialDataFetchers) {
        this.meterRegistry = meterRegistry;
        this.apolloTracingEnabled = apolloTracingEnabled;
        this.includeTrivialDataFetchers = includeTrivialDataFetchers;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new TracingState();
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
                                                InstrumentationFieldFetchParameters parameters,
                                                InstrumentationState state) {
        if (parameters.isTrivialDataFetcher() && !includeTrivialDataFetchers) {
            return dataFetcher;
        }
        TracingState tracingState = (TracingState) state;
        return environment -> {
            long startNanos = System.nanoTime();
            long startOffset = startNanos - tracingState.startNanos;
            try {
                Object result = dataFetcher.get(environment);
                if (result instanceof CompletionStage<?> stage) {
                    return stage.whenComplete((r, t) ->
                            record(environment, startNanos, startOffset, tracingState));
                }
                record(environment, startNanos, startOffset, tracingState);
                return result;
            } catch (Exception ex) {
                record(environment, startNanos, startOffset, tracingState);
                throw ex;
            }
        };
    }

    private void record(DataFetchingEnvironment environment, long startNanos, long startOffset,
                        TracingState tracingState) {
        long durationNanos = System.nanoTime() - startNanos;
        String parentType = environment.getParentType() != null
                ? environment.getExecutionStepInfo().getObjectType().getName()
                : "Unknown";
        String fieldName = environment.getField().getName();

        try {
            meterRegistry.timer(TIMER_NAME, "parent", parentType, "field", fieldName)
                    .record(Duration.ofNanos(durationNanos));
        } catch (Exception e) {
            log.debug("Failed to record resolver timer for {}.{}: {}", parentType, fieldName, e.getMessage());
        }

        if (apolloTracingEnabled) {
            tracingState.add(new ResolverTrace(
                    environment.getExecutionStepInfo().getPath().toList(),
                    parentType,
                    fieldName,
                    environment.getExecutionStepInfo().getType().toString(),
                    startOffset,
                    durationNanos));
        }
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult,
                                                                        InstrumentationExecutionParameters parameters,
                                                                        InstrumentationState state) {
        if (!apolloTracingEnabled) {
            return CompletableFuture.completedFuture(executionResult);
        }
        TracingState tracingState = (TracingState) state;
        long endNanos = System.nanoTime();
        Map<String, Object> tracing = new LinkedHashMap<>();
        tracing.put("version", APOLLO_TRACING_VERSION);
        tracing.put("startTime", tracingState.startInstant.toString());
        tracing.put("endTime", Instant.now().toString());
        tracing.put("duration", endNanos - tracingState.startNanos);

        List<Map<String, Object>> resolvers = new ArrayList<>();
        for (ResolverTrace trace : tracingState.resolvers) {
            Map<String, Object> resolver = new LinkedHashMap<>();
            resolver.put("path", trace.path);
            resolver.put("parentType", trace.parentType);
            resolver.put("fieldName", trace.fieldName);
            resolver.put("returnType", trace.returnType);
            resolver.put("startOffset", trace.startOffset);
            resolver.put("duration", trace.duration);
            resolvers.add(resolver);
        }
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("resolvers", resolvers);
        tracing.put("execution", execution);

        Map<Object, Object> extensions = new LinkedHashMap<>();
        if (executionResult.getExtensions() != null) {
            extensions.putAll(executionResult.getExtensions());
        }
        extensions.put(TRACING_EXTENSION, tracing);

        ExecutionResult withTracing = ExecutionResultImpl.newExecutionResult()
                .from(executionResult)
                .extensions(extensions)
                .build();
        return CompletableFuture.completedFuture(withTracing);
    }

    /** Per-request tracing state. */
    static final class TracingState implements InstrumentationState {
        private final long startNanos = System.nanoTime();
        private final Instant startInstant = Instant.now();
        private final List<ResolverTrace> resolvers = new CopyOnWriteArrayList<>();

        void add(ResolverTrace trace) {
            resolvers.add(trace);
        }
    }

    /** Immutable record of a single resolver invocation. */
    record ResolverTrace(List<Object> path, String parentType, String fieldName, String returnType,
                         long startOffset, long duration) {
    }
}
