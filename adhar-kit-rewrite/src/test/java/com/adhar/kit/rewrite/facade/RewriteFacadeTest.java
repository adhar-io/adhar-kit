package com.adhar.kit.rewrite.facade;

import com.adhar.kit.rewrite.catalog.RecipeCatalog.RecipeSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
