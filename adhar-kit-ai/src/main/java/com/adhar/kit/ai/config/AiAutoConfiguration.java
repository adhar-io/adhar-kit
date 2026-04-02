package com.adhar.kit.ai.config;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.aspect.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration for Adhar Kit AI module.
 *
 * <p>Automatically configures AI aspects and components when the module is present.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Multi-provider AI integration (OpenAI, Anthropic, Google, local models)</li>
 *   <li>@AiChat annotation for declarative chat completions</li>
 *   <li>@AiEmbedding annotation for vector embeddings</li>
 *   <li>@AiCache annotation for response caching</li>
 *   <li>@AiMetrics annotation for performance metrics</li>
 * </ul>
 *
 * <p><b>Configuration Properties:</b></p>
 * <pre>{@code
 * adhar:
 *   ai:
 *     enabled: true
 *     default-provider: openai
 *     annotations.enabled: true
 *     cache.enabled: true
 *     metrics.enabled: true
 *     openai:
 *       api-key: ${OPENAI_API_KEY}
 *       model: gpt-4
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(
    prefix = "adhar.ai",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AiAutoConfiguration {

    @PostConstruct
    public void logAiConfiguration() {
        log.info("Adhar AI module initialized - multi-provider AI integration enabled");
    }

    /**
     * Creates AiFacade bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public AiFacade aiFacade() {
        log.info("Initializing AiFacade");
        return AiFacade.getInstance();
    }

    /**
     * Creates AiChatAspect for @AiChat annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.ai.annotations",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiChatAspect aiChatAspect() {
        log.info("Enabling @AiChat annotation support");
        return new AiChatAspect();
    }

    /**
     * Creates AiEmbeddingAspect for @AiEmbedding annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.ai.annotations",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiEmbeddingAspect aiEmbeddingAspect() {
        log.info("Enabling @AiEmbedding annotation support");
        return new AiEmbeddingAspect();
    }

    /**
     * Creates AiCacheAspect for @AiCache annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.ai.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiCacheAspect aiCacheAspect() {
        log.info("Enabling @AiCache annotation support");
        return new AiCacheAspect();
    }

    /**
     * Creates AiMetricsAspect for @AiMetrics annotation processing.
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(
        prefix = "adhar.ai.metrics",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiMetricsAspect aiMetricsAspect(MeterRegistry meterRegistry) {
        log.info("Enabling @AiMetrics annotation support");
        return new AiMetricsAspect(meterRegistry);
    }
}

