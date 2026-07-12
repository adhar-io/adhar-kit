package com.adhar.kit.ai.service;

import com.adhar.kit.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiModelFactory} provider/model selection and caching.
 */
@ExtendWith(MockitoExtension.class)
class AiModelFactoryTest {

    @Mock
    private ChatModel defaultChatModel;
    @Mock
    private EmbeddingModel defaultEmbeddingModel;
    @Mock
    private AiProperties aiProperties;

    private AiModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AiModelFactory(defaultChatModel, defaultEmbeddingModel, aiProperties);
    }

    @Test
    void getDefaultModelsReturnInjectedBeans() {
        assertThat(factory.getDefaultChatModel()).isSameAs(defaultChatModel);
        assertThat(factory.getDefaultEmbeddingModel()).isSameAs(defaultEmbeddingModel);
    }

    @Test
    void getChatModelForKnownProviders() {
        assertThat(factory.getChatModel("openai", "gpt-4")).isSameAs(defaultChatModel);
        assertThat(factory.getChatModel("azure", "gpt-4")).isSameAs(defaultChatModel);
        assertThat(factory.getChatModel("ollama", "llama2")).isSameAs(defaultChatModel);
    }

    @Test
    void getChatModelForUnknownProviderFallsBackToDefault() {
        assertThat(factory.getChatModel("unknown", "x")).isSameAs(defaultChatModel);
    }

    @Test
    void getChatModelCachesByProviderAndModel() {
        ChatModel first = factory.getChatModel("openai", "gpt-4");
        ChatModel second = factory.getChatModel("openai", "gpt-4");
        assertThat(first).isSameAs(second);
    }

    @Test
    void getEmbeddingModelForKnownAndUnknownProviders() {
        assertThat(factory.getEmbeddingModel("openai")).isSameAs(defaultEmbeddingModel);
        assertThat(factory.getEmbeddingModel("azure")).isSameAs(defaultEmbeddingModel);
        assertThat(factory.getEmbeddingModel("ollama")).isSameAs(defaultEmbeddingModel);
        assertThat(factory.getEmbeddingModel("other")).isSameAs(defaultEmbeddingModel);
    }

    @Test
    void isModelAvailableReturnsTrueForResolvableModel() {
        assertThat(factory.isModelAvailable("openai", "gpt-4")).isTrue();
    }

    @Test
    void isModelAvailableReturnsTrueForUnknownProviderViaFallback() {
        assertThat(factory.isModelAvailable("unknown", "x")).isTrue();
    }

    @Test
    void embeddingModelIsCachedPerProvider() {
        EmbeddingModel first = factory.getEmbeddingModel("openai");
        EmbeddingModel second = factory.getEmbeddingModel("openai");
        assertThat(first).isSameAs(second);
    }

    // ==================== Provider -> model name selection ====================
    // These private helpers encapsulate the provider/use-case to model mapping.
    // They are exercised via reflection to lock in the expected mappings.

    private String invoke(String methodName, String provider) throws Exception {
        AiProperties realProps = new AiProperties();
        AiModelFactory f = new AiModelFactory(defaultChatModel, defaultEmbeddingModel, realProps);
        Method m = AiModelFactory.class.getDeclaredMethod(methodName, String.class);
        m.setAccessible(true);
        return (String) m.invoke(f, provider);
    }

    @Test
    void conversationModelMapping() throws Exception {
        assertThat(invoke("getConversationModel", "openai")).isEqualTo("gpt-3.5-turbo");
        assertThat(invoke("getConversationModel", "azure")).isEqualTo("gpt-35-turbo");
        assertThat(invoke("getConversationModel", "ollama")).isEqualTo("llama2");
        assertThat(invoke("getConversationModel", "other")).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    void generationModelMapping() throws Exception {
        assertThat(invoke("getGenerationModel", "openai")).isEqualTo("gpt-4");
        assertThat(invoke("getGenerationModel", "azure")).isEqualTo("gpt-4");
        assertThat(invoke("getGenerationModel", "ollama")).isEqualTo("llama2");
        assertThat(invoke("getGenerationModel", "other")).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    void analysisModelMapping() throws Exception {
        assertThat(invoke("getAnalysisModel", "openai")).isEqualTo("gpt-4");
        assertThat(invoke("getAnalysisModel", "azure")).isEqualTo("gpt-4");
        assertThat(invoke("getAnalysisModel", "ollama")).isEqualTo("llama2");
        assertThat(invoke("getAnalysisModel", "other")).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    void codeModelMapping() throws Exception {
        assertThat(invoke("getCodeModel", "openai")).isEqualTo("gpt-4");
        assertThat(invoke("getCodeModel", "azure")).isEqualTo("gpt-4");
        assertThat(invoke("getCodeModel", "ollama")).isEqualTo("codellama");
        assertThat(invoke("getCodeModel", "other")).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    void defaultModelMapping() throws Exception {
        assertThat(invoke("getDefaultModel", "openai")).isEqualTo("gpt-3.5-turbo");
        assertThat(invoke("getDefaultModel", "azure")).isEqualTo("gpt-35-turbo");
        assertThat(invoke("getDefaultModel", "ollama")).isEqualTo("llama2");
        assertThat(invoke("getDefaultModel", "other")).isEqualTo("gpt-3.5-turbo");
    }
}
