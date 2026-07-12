package com.adhar.kit.rewrite.recipe.cross;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringToHelidonTest {

    private final String yaml = SpringToHelidon.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonBlankRecipe() {
        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("recipeList:");
    }

    @Test
    void yaml_mapsDiAndConfigAnnotations() {
        assertThat(yaml).contains("jakarta.enterprise.context.ApplicationScoped");
        assertThat(yaml).contains("jakarta.inject.Inject");
        assertThat(yaml).contains("org.eclipse.microprofile.config.inject.ConfigProperty");
    }

    @Test
    void yaml_mapsJaxRsAnnotations() {
        assertThat(yaml).contains("jakarta.ws.rs.Path");
        assertThat(yaml).contains("jakarta.ws.rs.GET");
        assertThat(yaml).contains("jakarta.ws.rs.POST");
    }
}
