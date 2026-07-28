package com.adhar.kit.ai.prompt;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the shared {@link PromptSubstitutor} placeholder logic.
 */
class PromptSubstitutorTest {

    public static class Product {
        public String getName() {
            return "Widget";
        }

        public boolean isActive() {
            return true;
        }
    }

    @Test
    void substitutesSimplePlaceholders() {
        String result = PromptSubstitutor.substitute("Hello {name}", Map.of("name", "Alice"));
        assertThat(result).isEqualTo("Hello Alice");
    }

    @Test
    void resolvesNestedProperties() {
        String result = PromptSubstitutor.substitute(
                "Name is {product.name} active {product.active}", Map.of("product", new Product()));
        assertThat(result).isEqualTo("Name is Widget active true");
    }

    @Test
    void missingParameterBecomesEmptyString() {
        String result = PromptSubstitutor.substitute("Hello {name}!", Map.of());
        assertThat(result).isEqualTo("Hello !");
    }

    @Test
    void unknownNestedGetterBecomesEmptyString() {
        String result = PromptSubstitutor.substitute("{product.missing}", Map.of("product", new Product()));
        assertThat(result).isEmpty();
    }

    @Test
    void nullValueBecomesEmptyString() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", null);
        assertThat(PromptSubstitutor.substitute("Hi {name}", params)).isEqualTo("Hi ");
    }

    @Test
    void nullAndEmptyTemplatesReturnedAsIs() {
        assertThat(PromptSubstitutor.substitute(null, Map.of())).isNull();
        assertThat(PromptSubstitutor.substitute("", Map.of())).isEmpty();
    }

    @Test
    void nullParamsTreatedAsEmpty() {
        assertThat(PromptSubstitutor.substitute("Hello {name}", null)).isEqualTo("Hello ");
    }

    @Test
    void specialReplacementCharactersAreQuoted() {
        String result = PromptSubstitutor.substitute("Value: {v}", Map.of("v", "$100 \\ back"));
        assertThat(result).isEqualTo("Value: $100 \\ back");
    }
}
