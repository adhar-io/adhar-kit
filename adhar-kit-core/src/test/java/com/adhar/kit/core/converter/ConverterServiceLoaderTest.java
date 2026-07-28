package com.adhar.kit.core.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConverterRegistry ServiceLoader discovery")
class ConverterServiceLoaderTest {

    @Test
    @DisplayName("loadFromServiceLoader discovers META-INF/services providers")
    void loadFromServiceLoaderDiscoversProviders() {
        ConverterRegistry registry = ConverterRegistry.empty().loadFromServiceLoader();

        assertThat(registry.hasConverter(ServiceLoadedColorConverter.Color.class)).isTrue();
        assertThat(registry.convert("green", ServiceLoadedColorConverter.Color.class))
            .isEqualTo(ServiceLoadedColorConverter.Color.GREEN);
    }

    @Test
    @DisplayName("default registry (and TypeConverter) picks up ServiceLoader providers")
    void defaultRegistryPicksUpProvider() {
        assertThat(TypeConverter.convert("blue", ServiceLoadedColorConverter.Color.class))
            .isEqualTo(ServiceLoadedColorConverter.Color.BLUE);
    }
}
