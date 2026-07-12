package com.adhar.kit.rewrite.recipe.cross;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringToQuarkusTest {

    private final String yaml = SpringToQuarkus.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonBlankRecipe() {
        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("type: specs.openrewrite.org/v1beta/recipe");
        assertThat(yaml).contains("name: com.adhar.kit.rewrite.recipe.cross.SpringToQuarkus");
        assertThat(yaml).contains("displayName: Migrate Spring Boot to Quarkus");
        assertThat(yaml).contains("recipeList:");
    }

    @Test
    void yaml_mapsServiceAndComponentToApplicationScoped() {
        assertThat(yaml).contains("org.springframework.stereotype.Service");
        assertThat(yaml).contains("org.springframework.stereotype.Component");
        assertThat(yaml).contains("jakarta.enterprise.context.ApplicationScoped");
    }

    @Test
    void yaml_mapsAutowiredToInject() {
        assertThat(yaml).contains("org.springframework.beans.factory.annotation.Autowired");
        assertThat(yaml).contains("jakarta.inject.Inject");
    }

    @Test
    void yaml_mapsValueToConfigProperty() {
        assertThat(yaml).contains("org.springframework.beans.factory.annotation.Value");
        assertThat(yaml).contains("org.eclipse.microprofile.config.inject.ConfigProperty");
    }

    @Test
    void yaml_mapsSchedulingAndEvents() {
        assertThat(yaml).contains("io.quarkus.scheduler.Scheduled");
        assertThat(yaml).contains("jakarta.enterprise.event.Observes");
    }
}
