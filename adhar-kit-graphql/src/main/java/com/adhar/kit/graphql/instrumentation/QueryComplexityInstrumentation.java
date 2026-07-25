package com.adhar.kit.graphql.instrumentation;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL instrumentation that enforces query complexity and depth limits
 * <strong>before</strong> execution begins.
 *
 * <p>This is a thin, backward-compatible wrapper around graphql-java's own
 * {@link MaxQueryComplexityInstrumentation} and {@link MaxQueryDepthInstrumentation},
 * combined via {@link ChainedInstrumentation}. Both delegate instrumentations hook
 * {@code beginExecuteOperation}, which runs after validation but strictly before any
 * {@code DataFetcher} is invoked. When a limit is exceeded they throw a
 * {@code graphql.execution.AbortExecutionException}, which graphql-java converts into
 * an {@link graphql.ExecutionResult} carrying an error &mdash; without ever calling a
 * single field data fetcher.</p>
 *
 * <p>This replaces a previous implementation that recomputed complexity/depth itself
 * and rejected the query only after {@code instrumentExecutionResult} ran (i.e. after
 * the query had already fully executed).</p>
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
public class QueryComplexityInstrumentation extends ChainedInstrumentation {

    private final int maxComplexity;
    private final int maxDepth;

    /**
     * Creates a new instrumentation with the specified limits.
     *
     * @param maxComplexity the maximum allowed query complexity score
     * @param maxDepth      the maximum allowed query depth
     */
    public QueryComplexityInstrumentation(int maxComplexity, int maxDepth) {
        super(buildDelegates(maxComplexity, maxDepth));
        this.maxComplexity = maxComplexity;
        this.maxDepth = maxDepth;
        log.debug("Query complexity instrumentation configured (pre-execution): maxComplexity={}, maxDepth={}",
                maxComplexity, maxDepth);
    }

    private static List<Instrumentation> buildDelegates(int maxComplexity, int maxDepth) {
        List<Instrumentation> delegates = new ArrayList<>();
        delegates.add(new MaxQueryComplexityInstrumentation(maxComplexity));
        delegates.add(new MaxQueryDepthInstrumentation(maxDepth));
        return delegates;
    }

    /**
     * Returns the configured maximum query complexity.
     *
     * @return the maximum allowed complexity score
     */
    public int getMaxComplexity() {
        return maxComplexity;
    }

    /**
     * Returns the configured maximum query depth.
     *
     * @return the maximum allowed depth
     */
    public int getMaxDepth() {
        return maxDepth;
    }
}
