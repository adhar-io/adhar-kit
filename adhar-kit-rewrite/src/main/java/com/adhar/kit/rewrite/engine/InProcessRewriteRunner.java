package com.adhar.kit.rewrite.engine;

import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.LargeSourceSet;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Executes {@link org.openrewrite.Recipe OpenRewrite recipes} directly against in-memory Java
 * sources, in-process, using OpenRewrite's own parsing and recipe-run machinery.
 *
 * <p>This replaces the previous "generate rewrite.yml and print an {@code mvn rewrite:run}
 * command" behavior in {@code RewriteFacade} for callers that want an immediate, real answer:
 * given a recipe and a set of source files, what would actually change, and what does the diff
 * look like?</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * List<InProcessRewriteRunner.SourceInput> sources = List.of(
 *         new InProcessRewriteRunner.SourceInput("Demo.java", "class Demo { }")
 * );
 * InProcessRewriteRunner.RewriteExecution execution =
 *         InProcessRewriteRunner.run(new UseConvenienceShortcuts(), sources);
 * execution.results().forEach(result -> System.out.println(result.diff()));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class InProcessRewriteRunner {

    private InProcessRewriteRunner() {}

    /**
     * Runs a single recipe against the given in-memory Java sources using a fresh
     * {@link InMemoryExecutionContext}.
     *
     * @param recipe  the OpenRewrite recipe to execute
     * @param sources the in-memory source files to parse and run the recipe against
     * @return the execution result, including per-file {@link Result} diffs
     */
    public static RewriteExecution run(Recipe recipe, List<SourceInput> sources) {
        return run(recipe, sources, newExecutionContext());
    }

    /**
     * Runs a single recipe against the given in-memory Java sources using the supplied execution
     * context.
     *
     * @param recipe  the OpenRewrite recipe to execute
     * @param sources the in-memory source files to parse and run the recipe against
     * @param ctx     the execution context to run the recipe with
     * @return the execution result, including per-file {@link Result} diffs
     */
    public static RewriteExecution run(Recipe recipe, List<SourceInput> sources, ExecutionContext ctx) {
        Objects.requireNonNull(recipe, "recipe must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        JavaParser parser = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(false)
                .build();

        List<Parser.Input> inputs = sources.stream()
                .map(source -> Parser.Input.fromString(Path.of(source.path()), source.content()))
                .toList();

        List<SourceFile> parsed = parser.parseInputs(inputs, null, ctx).toList();

        LargeSourceSet sourceSet = new InMemoryLargeSourceSet(parsed);
        RecipeRun recipeRun = recipe.run(sourceSet, ctx);
        List<Result> results = List.copyOf(recipeRun.getChangeset().getAllResults());

        log.debug("Recipe '{}' ran against {} source file(s) and produced {} changed file(s)",
                recipe.getDisplayName(), parsed.size(), results.size());

        return new RewriteExecution(recipe, results, recipeRun);
    }

    /**
     * Builds a new {@link InMemoryExecutionContext} suitable for in-process recipe execution.
     * Any recipe error is rethrown rather than silently swallowed.
     *
     * @return a new execution context
     */
    public static ExecutionContext newExecutionContext() {
        return new InMemoryExecutionContext(throwable -> {
            throw new RewriteExecutionException("OpenRewrite recipe execution failed", throwable);
        });
    }

    /**
     * An in-memory source file to feed into a recipe run.
     *
     * @param path    the (virtual) file path, used to determine the OpenRewrite parser and to
     *                label diffs/results
     * @param content the full source text
     */
    public record SourceInput(String path, String content) {
        public SourceInput {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }

    /**
     * The outcome of running a recipe against a set of in-memory sources.
     *
     * @param recipe    the recipe that was executed
     * @param results   the list of {@link Result per-file results}; only files that actually
     *                  changed appear here
     * @param recipeRun the full underlying OpenRewrite {@link RecipeRun}, exposed for callers that
     *                  need data tables or other advanced details
     */
    public record RewriteExecution(Recipe recipe, List<Result> results, RecipeRun recipeRun) {

        /**
         * @return the number of files that were changed by the recipe run
         */
        public int changedFileCount() {
            return results.size();
        }

        /**
         * @return true if the recipe changed at least one file
         */
        public boolean hasChanges() {
            return !results.isEmpty();
        }
    }

    /**
     * Wraps an unexpected failure raised while running a recipe in-process.
     */
    public static final class RewriteExecutionException extends RuntimeException {
        public RewriteExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
