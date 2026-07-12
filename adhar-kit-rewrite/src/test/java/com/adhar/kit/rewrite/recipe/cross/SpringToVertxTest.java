package com.adhar.kit.rewrite.recipe.cross;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringToVertxTest {

    private final String yaml = SpringToVertx.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonBlankRecipe() {
        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("recipeList:");
    }

    @Test
    void yaml_mapsControllerAndConfigTypes() {
        assertThat(yaml).contains("io.vertx.ext.web.Router");
        assertThat(yaml).contains("io.vertx.config.ConfigRetriever");
        assertThat(yaml).contains("io.vertx.core.json.JsonObject");
    }

    @Test
    void yaml_changesHttpPackageRecursively() {
        assertThat(yaml).contains("org.springframework.http");
        assertThat(yaml).contains("io.vertx.core.http");
        assertThat(yaml).contains("recursive: true");
    }
}
