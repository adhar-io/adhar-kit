package com.adhar.kit.rewrite.engine;

import com.adhar.kit.rewrite.engine.InProcessRewriteRunner.RewriteExecution;
import com.adhar.kit.rewrite.engine.InProcessRewriteRunner.SourceInput;
import com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InProcessRewriteRunnerTest {

    private static final String BEFORE = """
            class Demo {
                void run(AdharKitFacade adhar) {
                    adhar.getMetrics().increment("requests");
                }
            }
            """;

    @Test
    void run_appliesRecipeAndReportsChangedFile() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", BEFORE))
        );

        assertThat(execution.hasChanges()).isTrue();
        assertThat(execution.changedFileCount()).isEqualTo(1);
        assertThat(execution.results()).hasSize(1);

        Result result = execution.results().get(0);
        assertThat(result.diff()).contains("-        adhar.getMetrics().increment(\"requests\");");
        assertThat(result.diff()).contains("+        adhar.count(\"requests\");");
    }

    @Test
    void run_withNoMatchingSource_reportsNoChanges() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", "class Demo { void run() { } }"))
        );

        assertThat(execution.hasChanges()).isFalse();
        assertThat(execution.changedFileCount()).isZero();
        assertThat(execution.results()).isEmpty();
    }

    @Test
    void run_withMultipleSources_onlyReportsChangedOnes() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(
                        new SourceInput("Changed.java", BEFORE.replace("Demo", "Changed")),
                        new SourceInput("Unchanged.java", "class Unchanged { void run() { } }")
                )
        );

        assertThat(execution.changedFileCount()).isEqualTo(1);
        assertThat(execution.results().get(0).getAfter().getSourcePath().toString())
                .isEqualTo("Changed.java");
    }

    @Test
    void run_withExplicitExecutionContext_usesSuppliedContext() {
        RewriteExecution execution = InProcessRewriteRunner.run(
                new UseConvenienceShortcuts(),
                List.of(new SourceInput("Demo.java", BEFORE)),
                new InMemoryExecutionContext()
        );

        assertThat(execution.hasChanges()).isTrue();
    }

    @Test
    void run_nullRecipe_throwsNullPointerException() {
        assertThatThrownBy(() -> InProcessRewriteRunner.run(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void run_nullSources_throwsNullPointerException() {
        assertThatThrownBy(() -> InProcessRewriteRunner.run(new UseConvenienceShortcuts(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sourceInput_nullPath_throwsNullPointerException() {
        assertThatThrownBy(() -> new SourceInput(null, "content"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sourceInput_nullContent_throwsNullPointerException() {
        assertThatThrownBy(() -> new SourceInput("Demo.java", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void newExecutionContext_returnsUsableContext() {
        assertThat(InProcessRewriteRunner.newExecutionContext()).isNotNull();
    }
}
