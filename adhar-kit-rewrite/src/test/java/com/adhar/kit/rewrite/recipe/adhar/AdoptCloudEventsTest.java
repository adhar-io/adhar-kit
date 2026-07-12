package com.adhar.kit.rewrite.recipe.adhar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdoptCloudEventsTest {

    private final String yaml = AdoptCloudEvents.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonBlankRecipe() {
        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("name: com.adhar.kit.rewrite.recipe.adhar.AdoptCloudEvents");
        assertThat(yaml).contains("displayName: Adopt CloudEvents Specification");
        assertThat(yaml).contains("recipeList:");
    }

    @Test
    void yaml_addsCloudEventImports() {
        assertThat(yaml).contains("io.cloudevents.CloudEvent");
        assertThat(yaml).contains("io.cloudevents.core.builder.CloudEventBuilder");
        assertThat(yaml).contains("onlyIfReferenced: true");
    }

    @Test
    void yaml_renamesBaseAndDomainEventToEnvelope() {
        assertThat(yaml).contains("com.adhar.kit.messaging.event.BaseEvent");
        assertThat(yaml).contains("com.adhar.kit.messaging.event.DomainEvent");
        assertThat(yaml).contains("com.adhar.kit.messaging.event.AdharCloudEvent");
    }

    @Test
    void yaml_renamesEventFactoryMethods() {
        assertThat(yaml).contains("newMethodName: cloudEvent");
        assertThat(yaml).contains("createEvent(..)");
        assertThat(yaml).contains("newEvent(..)");
    }
}
