package com.adhar.kit.rewrite.facade;

import com.adhar.kit.rewrite.catalog.RecipeCatalog.RecipeSet;
import com.adhar.kit.rewrite.facade.RewriteFacade.FileChange;
import com.adhar.kit.rewrite.facade.RewriteFacade.RewriteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewriteFacadeTest {

    private final RewriteFacade facade = RewriteFacade.getInstance();

    @Test
    void getInstance_returnsSameInstance() {
        RewriteFacade first = RewriteFacade.getInstance();
        RewriteFacade second = RewriteFacade.getInstance();

        assertThat(first).isSameAs(second);
    }

    @Test
    void listRecipeSets_returnsCatalog() {
        Map<String, RecipeSet> recipeSets = facade.listRecipeSets();

        assertThat(recipeSets).isNotEmpty();
        assertThat(recipeSets).containsKey("java-25");
        assertThat(recipeSets).containsKey("spring-boot-4");
        assertThat(recipeSets).containsKey("adhar-full-modernization");
    }

    @Test
    void listRecipeSetKeys_containsExpectedKeys() {
        List<String> keys = facade.listRecipeSetKeys();

        assertThat(keys).contains("java-25", "spring-boot-4", "adhar-full-modernization");
        assertThat(keys).isNotEmpty();
    }

    @Test
    void getRecipeSet_forKnownKey_returnsNonNull() {
        RecipeSet result = facade.getRecipeSet("java-25");

        assertThat(result).isNotNull();
        assertThat(result.displayName()).isNotBlank();
        assertThat(result.recipeNames()).isNotEmpty();
    }

    @Test
    void getRecipeSet_forUnknownKey_returnsNull() {
        RecipeSet result = facade.getRecipeSet("does-not-exist");

        assertThat(result).isNull();
    }

    @Test
    void health_returnsExpectedKeys() {
        Map<String, Object> health = facade.health();

        assertThat(health).containsKey("status");
        assertThat(health).containsKey("recipeSets");
        assertThat(health).containsKey("availableKeys");
        assertThat(health).containsKey("categories");

        assertThat(health.get("status")).isEqualTo("UP");
        assertThat((int) health.get("recipeSets")).isGreaterThan(0);
        assertThat((int) health.get("categories")).isGreaterThan(0);
        assertThat(health.get("availableKeys")).isInstanceOf(List.class);
    }

    // -------------------------------------------------------------------------
    // dryRun / apply by recipe set key
    // -------------------------------------------------------------------------

    @Test
    void dryRun_byKey_generatesConfigAndReturnsDryRunResult(@TempDir Path projectDir) {
        RewriteResult result = facade.dryRun("java-25", projectDir);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.changedFileCount()).isZero();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.changes()).hasSize(1);

        FileChange change = result.changes().get(0);
        assertThat(change.recipeName()).isEqualTo("RewriteFacade");
        assertThat(change.path()).endsWith("rewrite.yml");
        assertThat(change.diff()).contains("dry-run").contains("rewrite-maven-plugin:dryRun");

        Path configFile = projectDir.resolve("rewrite.yml");
        assertThat(configFile).exists();
    }

    @Test
    void apply_byKey_generatesConfigAndReturnsApplyResult(@TempDir Path projectDir) {
        RewriteResult result = facade.apply("spring-boot-4", projectDir);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.changedFileCount()).isEqualTo(1);
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().get(0).diff()).contains("rewrite-maven-plugin:run");

        assertThat(projectDir.resolve("rewrite.yml")).exists();
    }

    @Test
    void dryRun_unknownKey_throwsWithAvailableKeys(@TempDir Path projectDir) {
        assertThatThrownBy(() -> facade.dryRun("nope", projectDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown recipe set key")
                .hasMessageContaining("nope")
                .hasMessageContaining("Available keys");
    }

    @Test
    void apply_unknownKey_throws(@TempDir Path projectDir) {
        assertThatThrownBy(() -> facade.apply("nope", projectDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown recipe set key");
    }

    // -------------------------------------------------------------------------
    // dryRun / apply by explicit recipe name list
    // -------------------------------------------------------------------------

    @Test
    void dryRun_byList_writesYamlWithAllRecipes(@TempDir Path projectDir) throws Exception {
        List<String> recipes = List.of("com.example.RecipeA", "com.example.RecipeB");

        RewriteResult result = facade.dryRun(recipes, projectDir);

        assertThat(result.dryRun()).isTrue();
        String yaml = Files.readString(projectDir.resolve("rewrite.yml"));
        assertThat(yaml)
                .contains("type: specs.openrewrite.org/v1beta/recipe")
                .contains("name: com.adhar.kit.rewrite.GeneratedRecipe")
                .contains("recipeList:")
                .contains("- com.example.RecipeA")
                .contains("- com.example.RecipeB");
        // dry-run command joins recipes with comma
        assertThat(result.changes().get(0).diff())
                .contains("-Drewrite.activeRecipes=com.example.RecipeA,com.example.RecipeB");
    }

    @Test
    void apply_byList_writesYamlAndReturnsChangedCount(@TempDir Path projectDir) {
        RewriteResult result = facade.apply(List.of("com.example.OnlyRecipe"), projectDir);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.changedFileCount()).isEqualTo(1);
        assertThat(projectDir.resolve("rewrite.yml")).exists();
    }

    @Test
    void generateRewriteYml_createsMissingParentDirectories(@TempDir Path baseDir) {
        Path nestedProject = baseDir.resolve("nested").resolve("project");

        RewriteResult result = facade.dryRun(List.of("com.example.RecipeA"), nestedProject);

        assertThat(nestedProject.resolve("rewrite.yml")).exists();
        assertThat(result.changes()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // isRecipeAvailable
    // -------------------------------------------------------------------------

    @Test
    void isRecipeAvailable_unknownKey_returnsFalse() {
        assertThat(facade.isRecipeAvailable("does-not-exist")).isFalse();
    }

    @Test
    void isRecipeAvailable_allRecipesOnClasspath_returnsTrue() {
        // adhar-convenience-api references an in-module recipe class that is always present.
        assertThat(facade.isRecipeAvailable("adhar-convenience-api")).isTrue();
    }

    @Test
    void isRecipeAvailable_recipeClassMissing_returnsFalse() {
        // quarkus-3 references org.openrewrite.quarkus.* which is not on the test classpath.
        assertThat(facade.isRecipeAvailable("quarkus-3")).isFalse();
    }
}
