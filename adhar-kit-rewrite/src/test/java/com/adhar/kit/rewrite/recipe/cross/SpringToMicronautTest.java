package com.adhar.kit.rewrite.recipe.cross;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringToMicronautTest {

    private final String yaml = SpringToMicronaut.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonBlankRecipe() {
        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("name: com.adhar.kit.rewrite.recipe.cross.SpringToMicronaut");
        assertThat(yaml).contains("displayName: Migrate Spring Boot to Micronaut");
        assertThat(yaml).contains("recipeList:");
    }

    @Test
    void yaml_mapsStereotypesToSingleton() {
        assertThat(yaml).contains("org.springframework.stereotype.Service");
        assertThat(yaml).contains("org.springframework.stereotype.Repository");
        assertThat(yaml).contains("jakarta.inject.Singleton");
    }

    @Test
    void yaml_mapsControllerAnnotations() {
        assertThat(yaml).contains("io.micronaut.http.annotation.Controller");
        assertThat(yaml).contains("io.micronaut.http.annotation.Get");
        assertThat(yaml).contains("io.micronaut.http.annotation.Post");
        assertThat(yaml).contains("io.micronaut.http.annotation.Put");
        assertThat(yaml).contains("io.micronaut.http.annotation.Delete");
    }

    @Test
    void yaml_mapsValueToMicronautValue() {
        assertThat(yaml).contains("io.micronaut.context.annotation.Value");
    }
}
