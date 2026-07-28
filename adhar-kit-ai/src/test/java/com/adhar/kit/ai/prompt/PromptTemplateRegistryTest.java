package com.adhar.kit.ai.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PromptTemplateRegistry} (programmatic registration,
 * classpath loading and rendering).
 */
class PromptTemplateRegistryTest {

    /** Registry with classpath scanning disabled for deterministic isolation. */
    private PromptTemplateRegistry emptyRegistry() {
        return new PromptTemplateRegistry(null);
    }

    @Test
    void registersAndRendersTemplate() {
        PromptTemplateRegistry registry = emptyRegistry();
        registry.register("greet", "Hello {name}");

        assertThat(registry.contains("greet")).isTrue();
        assertThat(registry.get("greet")).isEqualTo("Hello {name}");
        assertThat(registry.render("greet", Map.of("name", "Bob"))).isEqualTo("Hello Bob");
        assertThat(registry.names()).containsExactly("greet");
    }

    @Test
    void overwritesExistingTemplate() {
        PromptTemplateRegistry registry = emptyRegistry();
        registry.register("t", "v1 {x}");
        registry.register("t", "v2 {x}");
        assertThat(registry.render("t", Map.of("x", "z"))).isEqualTo("v2 z");
    }

    @Test
    void removeReturnsWhetherPresent() {
        PromptTemplateRegistry registry = emptyRegistry();
        registry.register("t", "body");
        assertThat(registry.remove("t")).isTrue();
        assertThat(registry.remove("t")).isFalse();
        assertThat(registry.contains("t")).isFalse();
    }

    @Test
    void getUnknownTemplateThrows() {
        assertThatThrownBy(() -> emptyRegistry().get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void renderUnknownTemplateThrows() {
        assertThatThrownBy(() -> emptyRegistry().render("missing", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankNameOrNullBody() {
        PromptTemplateRegistry registry = emptyRegistry();
        assertThatThrownBy(() -> registry.register(" ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register("n", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsTemplatesFromClasspath() {
        // Loads src/test/resources/ai/prompts/welcome.txt via the default pattern.
        PromptTemplateRegistry registry = new PromptTemplateRegistry();

        assertThat(registry.contains("welcome")).isTrue();
        String rendered = registry.render("welcome",
                Map.of("name", "Ada", "product", "AdharKit")).trim();
        assertThat(rendered).isEqualTo("Welcome Ada to AdharKit!");
    }

    @Test
    void nullPatternSkipsClasspathLoading() {
        assertThat(emptyRegistry().names()).isEmpty();
    }
}
