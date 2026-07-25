package com.adhar.kit.rewrite.catalog;

import com.adhar.kit.rewrite.catalog.RecipeCatalog.Category;
import com.adhar.kit.rewrite.catalog.RecipeCatalog.RecipeSet;
import com.adhar.kit.rewrite.catalog.RecipeCatalogValidator.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCatalogValidatorTest {

    @Test
    void isResolvable_forInModuleRecipeClass_returnsTrue() {
        assertThat(RecipeCatalogValidator.isResolvable(
                "com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts")).isTrue();
    }

    @Test
    void isResolvable_forBogusRecipeClass_returnsFalse() {
        assertThat(RecipeCatalogValidator.isResolvable(
                "com.adhar.kit.rewrite.recipe.adhar.TotallyBogusRecipe")).isFalse();
    }

    @Test
    void validate_catalogWithOnlyResolvableRecipes_isValid() {
        Map<String, RecipeSet> catalog = Map.of(
                "adhar-convenience-api", new RecipeSet(
                        "Adopt Adhar Kit Convenience API",
                        "desc",
                        Category.ADHAR_KIT,
                        List.of("com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts")
                )
        );

        ValidationReport report = RecipeCatalogValidator.validate(catalog);

        assertThat(report.isValid()).isTrue();
        assertThat(report.unresolvedCount()).isZero();
        assertThat(report.catalogSize()).isEqualTo(1);
        assertThat(report.totalRecipeNames()).isEqualTo(1);
        assertThat(report.unresolvedRecipesByKey()).isEmpty();
        assertThat(report.toSummary()).contains("resolved successfully");
    }

    @Test
    void validate_catalogWithDeliberatelyBogusRecipeName_flagsIt() {
        Map<String, RecipeSet> catalog = Map.of(
                "totally-bogus", new RecipeSet(
                        "Totally Bogus",
                        "desc",
                        Category.CODE_QUALITY,
                        List.of("com.example.DoesNotExist", "com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts")
                )
        );

        ValidationReport report = RecipeCatalogValidator.validate(catalog);

        assertThat(report.isValid()).isFalse();
        assertThat(report.unresolvedCount()).isEqualTo(1);
        assertThat(report.totalRecipeNames()).isEqualTo(2);
        assertThat(report.unresolvedRecipesByKey()).containsKey("totally-bogus");
        assertThat(report.unresolvedRecipesByKey().get("totally-bogus")).containsExactly("com.example.DoesNotExist");
        assertThat(report.allUnresolvedRecipeNames()).containsExactly("com.example.DoesNotExist");
        assertThat(report.toSummary())
                .contains("1 unresolved recipe")
                .contains("totally-bogus")
                .contains("com.example.DoesNotExist");
    }

    @Test
    void validateAll_realCatalog_flagsRecipesNotOnClasspath() {
        ValidationReport report = RecipeCatalogValidator.validateAll();

        assertThat(report.catalogSize()).isEqualTo(RecipeCatalog.getAll().size());
        // org.openrewrite.quarkus.* is deliberately not a declared dependency of this module
        // (see RewriteFacadeTest#isRecipeAvailable_recipeClassMissing_returnsFalse), so the real
        // catalog is expected to surface it as unresolved.
        assertThat(report.isValid()).isFalse();
        assertThat(report.allUnresolvedRecipeNames()).contains("org.openrewrite.quarkus.Quarkus1to2Migration");
        assertThat(report.unresolvedRecipesByKey()).containsKey("quarkus-3");
    }

    @Test
    void validateAll_everyAdharKitRecipeClass_resolves() {
        ValidationReport report = RecipeCatalogValidator.validateAll();

        List<String> adharRecipeNames = RecipeCatalog.getByCategory(Category.ADHAR_KIT).stream()
                .flatMap(entry -> entry.getValue().recipeNames().stream())
                .filter(name -> name.startsWith("com.adhar.kit.rewrite."))
                .toList();

        assertThat(adharRecipeNames).isNotEmpty();
        assertThat(adharRecipeNames).noneMatch(report.allUnresolvedRecipeNames()::contains);
    }
}
