package com.adhar.kit.rewrite.report;

import com.adhar.kit.rewrite.engine.InProcessRewriteRunner.RewriteExecution;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;

import java.util.List;
import java.util.Objects;

/**
 * Summarizes the outcome of an in-process {@link org.openrewrite.Recipe} run: how many files
 * changed and the unified diff for each one. Renderable as Markdown (for human/PR-comment
 * consumption) or JSON (for machine consumption / tooling integration).
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * RewriteExecution execution = InProcessRewriteRunner.run(new UseConvenienceShortcuts(), sources);
 * RewriteImpactReport report = RewriteImpactReport.of(execution);
 * System.out.println(report.toMarkdown());
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class RewriteImpactReport {

    private final String recipeName;
    private final String recipeDisplayName;
    private final List<FileDiff> fileDiffs;

    private RewriteImpactReport(String recipeName, String recipeDisplayName, List<FileDiff> fileDiffs) {
        this.recipeName = recipeName;
        this.recipeDisplayName = recipeDisplayName;
        this.fileDiffs = List.copyOf(fileDiffs);
    }

    /**
     * Builds a report from the result of an {@link com.adhar.kit.rewrite.engine.InProcessRewriteRunner} run.
     *
     * @param execution the completed recipe execution
     * @return a report summarizing the changed files and their diffs
     */
    public static RewriteImpactReport of(RewriteExecution execution) {
        Objects.requireNonNull(execution, "execution must not be null");

        List<FileDiff> diffs = execution.results().stream()
                .map(RewriteImpactReport::toFileDiff)
                .toList();

        return new RewriteImpactReport(
                execution.recipe().getClass().getName(),
                execution.recipe().getDisplayName(),
                diffs
        );
    }

    private static FileDiff toFileDiff(Result result) {
        SourceFile reference = result.getAfter() != null ? result.getAfter() : result.getBefore();
        String path = reference != null ? reference.getSourcePath().toString() : "<unknown>";
        return new FileDiff(path, result.diff());
    }

    /**
     * @return the fully-qualified class name of the recipe that produced this report
     */
    public String recipeName() {
        return recipeName;
    }

    /**
     * @return the human-readable display name of the recipe that produced this report
     */
    public String recipeDisplayName() {
        return recipeDisplayName;
    }

    /**
     * @return the number of files changed by the recipe run
     */
    public int changedFileCount() {
        return fileDiffs.size();
    }

    /**
     * @return the per-file unified diffs, one entry per changed file
     */
    public List<FileDiff> fileDiffs() {
        return fileDiffs;
    }

    /**
     * Renders this report as a Markdown document, suitable for a PR comment or console output.
     *
     * @return the Markdown rendering of this report
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Rewrite Impact Report\n\n");
        sb.append("**Recipe:** ").append(recipeDisplayName).append(" (`").append(recipeName).append("`)\n\n");
        sb.append("**Changed files:** ").append(fileDiffs.size()).append("\n\n");

        if (fileDiffs.isEmpty()) {
            sb.append("_No changes were made._\n");
            return sb.toString();
        }

        for (FileDiff diff : fileDiffs) {
            sb.append("## ").append(diff.path()).append("\n\n");
            sb.append("```diff\n").append(diff.diff()).append("\n```\n\n");
        }
        return sb.toString();
    }

    /**
     * Renders this report as a JSON document (hand-rolled, dependency-free) with the shape:
     * <pre>{@code
     * {
     *   "recipe": "...",
     *   "recipeDisplayName": "...",
     *   "changedFileCount": N,
     *   "files": [ { "path": "...", "diff": "..." }, ... ]
     * }
     * }</pre>
     *
     * @return the JSON rendering of this report
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"recipe\": ").append(jsonString(recipeName)).append(",\n");
        sb.append("  \"recipeDisplayName\": ").append(jsonString(recipeDisplayName)).append(",\n");
        sb.append("  \"changedFileCount\": ").append(fileDiffs.size()).append(",\n");
        sb.append("  \"files\": [");
        for (int i = 0; i < fileDiffs.size(); i++) {
            FileDiff diff = fileDiffs.get(i);
            sb.append(i == 0 ? "\n" : "");
            sb.append("    {\n");
            sb.append("      \"path\": ").append(jsonString(diff.path())).append(",\n");
            sb.append("      \"diff\": ").append(jsonString(diff.diff())).append("\n");
            sb.append("    }").append(i < fileDiffs.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * A single file's unified diff as produced by an OpenRewrite {@link Result}.
     *
     * @param path the source path of the (post-change, if available) file
     * @param diff the unified diff text
     */
    public record FileDiff(String path, String diff) {
    }
}
