package com.adhar.kit.ai.config;

import com.adhar.kit.ai.service.AiModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the bean factory methods in {@link AiConfiguration}.
 */
@ExtendWith(MockitoExtension.class)
class AiConfigurationTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private EmbeddingModel embeddingModel;

    private AiProperties properties;
    private AiConfiguration configuration;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.getCaching().setMaxSize(500);
        properties.getCaching().setTtl(Duration.ofMinutes(10));
        configuration = new AiConfiguration(properties);
    }

    @Test
    void aiModelFactoryBeanIsCreated() {
        AiModelFactory factory = configuration.aiModelFactory(chatModel, embeddingModel);
        assertThat(factory).isNotNull();
        assertThat(factory.getDefaultChatModel()).isSameAs(chatModel);
        assertThat(factory.getDefaultEmbeddingModel()).isSameAs(embeddingModel);
    }

    @Test
    void aiCacheManagerBeanIsCaffeineBacked() {
        CacheManager cacheManager = configuration.aiCacheManager();
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        // exercising the cache resolves the specification
        assertThat(cacheManager.getCache("ai-chat")).isNotNull();
    }

    @Test
    void aiTaskExecutorBeanIsInitialized() {
        Executor executor = configuration.aiTaskExecutor();
        assertThat(executor).isNotNull();
    }
}
