package com.adhar.kit.ai.config;

import com.adhar.kit.ai.service.AiModelFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Main configuration class for AI services.
 * Sets up Spring AI clients, vector stores, and enterprise features.
 *
 * <p><b>Spring AI 2.0 note:</b> the {@link ChatModel} and {@link EmbeddingModel}
 * beans are provided by Spring AI's model auto-configuration (the
 * {@code spring-ai-starter-model-*} starters). Configure a provider through the
 * native Spring AI properties, e.g. {@code spring.ai.openai.api-key},
 * {@code spring.ai.anthropic.api-key} or {@code spring.ai.ollama.base-url}.
 * Spring AI 2.0 rebuilt the OpenAI integration on top of the official
 * {@code com.openai:openai-java} SDK, so the client is no longer constructed by
 * hand here.</p>
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
