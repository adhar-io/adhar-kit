package com.adhar.kit.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AI Auto Configuration.
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiAutoConfiguration.class);

    @Test
    void testAutoConfigurationLoads() {
        contextRunner.run(context -> {
            assertThat(context).isNotNull();
        });
    }

    @Test
    void testPropertiesBean() {
        contextRunner
                .withPropertyValues(
                        "adhar.ai.enabled=true",
                        "adhar.ai.default-model=gpt-4",
                        "adhar.ai.max-tokens=2000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProperties.class);
                    AiProperties properties = context.getBean(AiProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getDefaultModel()).isEqualTo("gpt-4");
                    assertThat(properties.getMaxTokens()).isEqualTo(2000);
                });
    }

    @Test
    void testDisabledConfiguration() {
        contextRunner
                .withPropertyValues("adhar.ai.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }
}

