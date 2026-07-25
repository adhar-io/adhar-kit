package com.adhar.kit.rewrite.report;

import com.adhar.kit.rewrite.engine.InProcessRewriteRunner;
import com.adhar.kit.rewrite.engine.InProcessRewriteRunner.RewriteExecution;
import com.adhar.kit.rewrite.engine.InProcessRewriteRunner.SourceInput;
import com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteImpactReportTest {

    private static final String BEFORE = """
            class Demo {
                void run(AdharKitFacade adhar) {
                    adhar.getMetrics().increment("requests");
                }
            }
            """;

    @Test
    void of_summarizesChangedFileCountAndDiffs() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", BEFORE))
        );

        RewriteImpactReport report = RewriteImpactReport.of(execution);

        assertThat(report.changedFileCount()).isEqualTo(1);
        assertThat(report.recipeDisplayName()).isEqualTo("Adopt Adhar Kit Convenience API");
        assertThat(report.recipeName()).isEqualTo(UseConvenienceShortcuts.class.getName());
        assertThat(report.fileDiffs()).hasSize(1);
        assertThat(report.fileDiffs().get(0).path()).isEqualTo("Demo.java");
        assertThat(report.fileDiffs().get(0).diff()).contains("adhar.count");
    }

    @Test
    void of_withNoChanges_producesEmptyReport() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", "class Demo { void run() { } }"))
        );

        RewriteImpactReport report = RewriteImpactReport.of(execution);

        assertThat(report.changedFileCount()).isZero();
        assertThat(report.fileDiffs()).isEmpty();
    }

    @Test
    void toMarkdown_includesRecipeNameCountAndDiffFencedBlock() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", BEFORE))
        );
        String markdown = RewriteImpactReport.of(execution).toMarkdown();

        assertThat(markdown)
                .contains("# Rewrite Impact Report")
                .contains("Adopt Adhar Kit Convenience API")
                .contains("**Changed files:** 1")
                .contains("## Demo.java")
                .contains("```diff");
    }

    @Test
    void toMarkdown_withNoChanges_saysNoChangesWereMade() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", "class Demo { void run() { } }"))
        );
        String markdown = RewriteImpactReport.of(execution).toMarkdown();

        assertThat(markdown).contains("_No changes were made._");
    }

    @Test
    void toJson_containsExpectedFieldsAndEscapesDiffContent() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", BEFORE))
        );
        String json = RewriteImpactReport.of(execution).toJson();

        assertThat(json)
                .contains("\"recipe\": \"" + UseConvenienceShortcuts.class.getName() + "\"")
                .contains("\"recipeDisplayName\": \"Adopt Adhar Kit Convenience API\"")
                .contains("\"changedFileCount\": 1")
                .contains("\"path\": \"Demo.java\"")
                .contains("\\n");
        // Diff text must not contain a raw, unescaped newline inside the JSON string value.
        assertThat(json.split("\"diff\": \"")[1].split("\"\n")[0]).doesNotContain("\r");
    }

    @Test
    void toJson_withNoChanges_hasEmptyFilesArray() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", "class Demo { void run() { } }"))
        );
        String json = RewriteImpactReport.of(execution).toJson();

        assertThat(json)
                .contains("\"changedFileCount\": 0")
                .contains("\"files\": [")
                .contains("]");
    }
}
