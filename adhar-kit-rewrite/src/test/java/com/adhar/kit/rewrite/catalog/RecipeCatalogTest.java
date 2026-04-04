package com.adhar.kit.rewrite.catalog;

import com.adhar.kit.rewrite.catalog.RecipeCatalog.Category;
import com.adhar.kit.rewrite.catalog.RecipeCatalog.RecipeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCatalogTest {

    @ParameterizedTest
    @ValueSource(strings = {"java-25", "spring-boot-4", "adhar-full-modernization"})
    void get_returnsKnownRecipeSets(String key) {
        RecipeSet result = RecipeCatalog.get(key);

        assertThat(result).isNotNull();
        assertThat(result.displayName()).isNotBlank();
    }

    @Test
    void get_returnsNullForUnknownKey() {
        RecipeSet result = RecipeCatalog.get("nonexistent-recipe");

        assertThat(result).isNull();
    }

    @Test
    void getAll_returnsNonEmptyMap() {
        Map<String, RecipeSet> all = RecipeCatalog.getAll();

        assertThat(all).isNotEmpty();
        assertThat(all.size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void getByCategory_javaMigration_returnsJavaRecipesOnly() {
        List<Map.Entry<String, RecipeSet>> javaRecipes = RecipeCatalog.getByCategory(Category.JAVA_MIGRATION);

        assertThat(javaRecipes).isNotEmpty();
        assertThat(javaRecipes).allSatisfy(entry -> {
            assertThat(entry.getValue().category()).isEqualTo(Category.JAVA_MIGRATION);
            assertThat(entry.getKey()).startsWith("java-");
        });
        assertThat(javaRecipes).extracting(Map.Entry::getKey)
                .contains("java-17", "java-21", "java-25");
    }

    @Test
    void getByCategory_adharKit_returnsAdharRecipes() {
        List<Map.Entry<String, RecipeSet>> adharRecipes = RecipeCatalog.getByCategory(Category.ADHAR_KIT);

        assertThat(adharRecipes).isNotEmpty();
        assertThat(adharRecipes).allSatisfy(entry ->
                assertThat(entry.getValue().category()).isEqualTo(Category.ADHAR_KIT)
        );
        assertThat(adharRecipes).extracting(Map.Entry::getKey)
                .contains("adhar-convenience-api", "adhar-spring-boot-4", "adhar-full-modernization");
    }

    @Test
    void listKeys_containsExpectedKeys() {
        List<String> keys = RecipeCatalog.listKeys();

        assertThat(keys).contains(
                "java-17", "java-21", "java-25",
                "spring-boot-3", "spring-boot-4",
                "junit-5", "assertj", "mockito-5",
                "security-best-practices",
                "adhar-convenience-api", "adhar-spring-boot-4",
                "adhar-cloudevents", "adhar-full-modernization",
                "code-cleanup", "logging-best-practices"
        );
    }

    @Test
    void eachRecipeSet_hasNonEmptyDisplayNameDescriptionAndRecipeNames() {
        Map<String, RecipeSet> all = RecipeCatalog.getAll();

        assertThat(all).allSatisfy((key, recipeSet) -> {
            assertThat(recipeSet.displayName())
                    .as("displayName for key '%s'", key)
                    .isNotBlank();
            assertThat(recipeSet.description())
                    .as("description for key '%s'", key)
                    .isNotBlank();
            assertThat(recipeSet.recipeNames())
                    .as("recipeNames for key '%s'", key)
                    .isNotEmpty();
        });
    }

    @Test
    void eachRecipeSet_hasCategoryCorrectlyAssigned() {
        Map<String, RecipeSet> all = RecipeCatalog.getAll();

        assertThat(all).allSatisfy((key, recipeSet) ->
                assertThat(recipeSet.category())
                        .as("category for key '%s'", key)
                        .isNotNull()
        );

        assertThat(RecipeCatalog.get("java-25").category()).isEqualTo(Category.JAVA_MIGRATION);
        assertThat(RecipeCatalog.get("spring-boot-4").category()).isEqualTo(Category.SPRING_MIGRATION);
        assertThat(RecipeCatalog.get("junit-5").category()).isEqualTo(Category.TESTING);
        assertThat(RecipeCatalog.get("security-best-practices").category()).isEqualTo(Category.SECURITY);
        assertThat(RecipeCatalog.get("adhar-convenience-api").category()).isEqualTo(Category.ADHAR_KIT);
        assertThat(RecipeCatalog.get("code-cleanup").category()).isEqualTo(Category.CODE_QUALITY);
    }

    @Test
    void eachRecipeSet_hasAtLeastOneRecipeName() {
        Map<String, RecipeSet> all = RecipeCatalog.getAll();

        assertThat(all).allSatisfy((key, recipeSet) ->
                assertThat(recipeSet.recipeNames())
                        .as("recipeNames for key '%s'", key)
                        .hasSizeGreaterThanOrEqualTo(1)
        );
    }
}
