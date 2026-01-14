package com.adhar.kit.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AI Properties configuration.
 */
class AiPropertiesTest {

    @Test
    void testDefaultValues() {
        AiProperties properties = new AiProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-3.5-turbo");
        assertThat(properties.getMaxTokens()).isEqualTo(1000);
        assertThat(properties.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void testSettersAndGetters() {
        AiProperties properties = new AiProperties();

        properties.setEnabled(false);
        properties.setDefaultModel("gpt-4");
        properties.setMaxTokens(2000);
        properties.setTemperature(0.9);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultModel()).isEqualTo("gpt-4");
        assertThat(properties.getMaxTokens()).isEqualTo(2000);
        assertThat(properties.getTemperature()).isEqualTo(0.9);
    }

    @Test
    void testProviderDefaults() {
        AiProperties properties = new AiProperties();

        assertThat(properties.getProvider()).isEqualTo("openai");

        properties.setProvider("azure");
        assertThat(properties.getProvider()).isEqualTo("azure");
    }

    @Test
    void testTimeoutSettings() {
        AiProperties properties = new AiProperties();

        properties.setTimeout(5000);
        assertThat(properties.getTimeout()).isEqualTo(5000);
    }

    @Test
    void testRetrySettings() {
        AiProperties properties = new AiProperties();

        properties.setMaxRetries(3);
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }
}

