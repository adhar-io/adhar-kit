package com.adhar.adharkit.ai.config;

import com.adhar.adharkit.ai.service.AiModelFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Main configuration class for AI services.
 * Sets up Spring AI clients, vector stores, and enterprise features.
 */
@Configuration
@EnableCaching
@EnableAsync
public class AiConfiguration {

    private final AiProperties aiProperties;

    public AiConfiguration(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * Primary OpenAI Chat Model for text generation.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "adhar.ai.openai.api-key")
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel openAiChatModel() {
        var openAiApi = new OpenAiApi(aiProperties.getOpenAi().getApiKey());
        return new OpenAiChatModel(openAiApi);
    }

    /**
     * OpenAI Embedding Model for vector embeddings.
     */
    @Bean
    @ConditionalOnProperty(name = "adhar.ai.openai.api-key")
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel openAiEmbeddingModel() {
        var openAiApi = new OpenAiApi(aiProperties.getOpenAi().getApiKey());
        return new OpenAiEmbeddingModel(openAiApi);
    }

    /**
     * AI Model Factory for dynamic model selection.
     */
    @Bean
    public AiModelFactory aiModelFactory(ChatModel chatModel, EmbeddingModel embeddingModel) {
        return new AiModelFactory(chatModel, embeddingModel, aiProperties);
    }

    /**
     * Cache Manager for AI responses.
     */
    @Bean
    @ConditionalOnProperty(name = "adhar.ai.caching.enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager aiCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification(
            "maximumSize=" + aiProperties.getCaching().getMaxSize() +
            ",expireAfterWrite=" + aiProperties.getCaching().getTtl().toSeconds() + "s"
        );
        return cacheManager;
    }

    /**
     * Async executor for AI operations.
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
