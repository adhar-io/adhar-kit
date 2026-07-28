package com.adhar.kit.ai.config;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.aspect.*;
import com.adhar.kit.ai.guardrail.ContentSafetyGuardrail;
import com.adhar.kit.ai.guardrail.Guardrail;
import com.adhar.kit.ai.guardrail.GuardrailChain;
import com.adhar.kit.ai.guardrail.PiiGuardrail;
import com.adhar.kit.ai.guardrail.SensitiveDataGuardrail;
import com.adhar.kit.ai.prompt.PromptTemplateRegistry;
import com.adhar.kit.ai.security.AiSecurityValidator;
import com.adhar.kit.ai.tool.DefaultToolCallingService;
import com.adhar.kit.ai.tool.ToolCallingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

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
     *
     * <p>The {@link com.adhar.kit.ai.service.AiService} bean (backed by the real
     * Spring AI {@code ChatModel}) is looked up lazily via {@link ObjectProvider}
     * since it is only registered once a chat model provider is configured; see
     * {@link AiChatAspect} for the full bridging rationale.</p>
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.ai.annotations",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiChatAspect aiChatAspect(ObjectProvider<com.adhar.kit.ai.service.AiService> aiServiceProvider) {
        log.info("Enabling @AiChat annotation support");
        return new AiChatAspect(aiServiceProvider);
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
     *
     * <p>When an {@link EmbeddingModel} bean is present and the semantic cache is
     * enabled ({@code adhar.ai.caching.semantic.enabled=true}), the aspect gains an
     * embedding-similarity fallback on exact-hash misses; otherwise it keeps the
     * exact-hash-only behaviour.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "adhar.ai.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AiCacheAspect aiCacheAspect(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                       AiProperties aiProperties) {
        log.info("Enabling @AiCache annotation support");
        AiProperties.Caching.Semantic semantic = aiProperties.getCaching().getSemantic();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (semantic.isEnabled() && embeddingModel != null) {
            log.info("Semantic (embedding-similarity) response cache enabled (threshold={})",
                    semantic.getSimilarityThreshold());
            return new AiCacheAspect(embeddingModel, true, semantic.getSimilarityThreshold(),
                    semantic.getMaxEntries());
        }
        return new AiCacheAspect();
    }

    /**
     * Shared prompt template registry (programmatic + {@code ai/prompts/*.txt}).
     */
    @Bean
    @ConditionalOnMissingBean
    public PromptTemplateRegistry promptTemplateRegistry() {
        log.info("Initializing prompt template registry");
        return new PromptTemplateRegistry();
    }

    /**
     * Tool-calling service running an iteration-capped function-calling loop.
     * Only created when a Spring AI {@link ChatModel} is available.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnProperty(prefix = "adhar.ai.tools", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ToolCallingService toolCallingService(ChatModel chatModel,
                                                 ObjectProvider<ObjectMapper> objectMapperProvider,
                                                 AiProperties aiProperties) {
        log.info("Enabling AI tool-calling service (maxIterations={})",
                aiProperties.getTools().getMaxIterations());
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DefaultToolCallingService(chatModel, objectMapper, aiProperties.getTools().getMaxIterations());
    }

    // ==================== Guardrail chain ====================

    @Bean
    @ConditionalOnMissingBean(name = "contentSafetyGuardrail")
    @ConditionalOnBean(AiSecurityValidator.class)
    @ConditionalOnProperty(prefix = "adhar.ai.guardrails", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ContentSafetyGuardrail contentSafetyGuardrail(AiSecurityValidator securityValidator) {
        return new ContentSafetyGuardrail(securityValidator);
    }

    @Bean
    @ConditionalOnMissingBean(name = "piiGuardrail")
    @ConditionalOnBean(AiSecurityValidator.class)
    @ConditionalOnProperty(prefix = "adhar.ai.guardrails", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PiiGuardrail piiGuardrail(AiSecurityValidator securityValidator) {
        return new PiiGuardrail(securityValidator);
    }

    @Bean
    @ConditionalOnMissingBean(name = "sensitiveDataGuardrail")
    @ConditionalOnBean(AiSecurityValidator.class)
    @ConditionalOnProperty(prefix = "adhar.ai.guardrails", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SensitiveDataGuardrail sensitiveDataGuardrail(AiSecurityValidator securityValidator) {
        return new SensitiveDataGuardrail(securityValidator);
    }

    /**
     * Assembles all {@link Guardrail} beans (the default content-safety/PII/sensitive
     * trio plus any application-contributed guardrails) into an ordered chain.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Guardrail.class)
    @ConditionalOnProperty(prefix = "adhar.ai.guardrails", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GuardrailChain guardrailChain(ObjectProvider<Guardrail> guardrails) {
        List<Guardrail> ordered = guardrails.orderedStream().toList();
        log.info("Assembling guardrail chain from {} guardrail(s)", ordered.size());
        return new GuardrailChain(ordered);
    }

    /**
     * Connects the {@link AiFacade} singleton to the Spring-managed AI stack.
     */
    @Bean
    @ConditionalOnMissingBean
    public AiFacadeInitializer aiFacadeInitializer(
            AiFacade aiFacade,
            ObjectProvider<com.adhar.kit.ai.service.AiService> serviceProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectProvider<ToolCallingService> toolCallingServiceProvider,
            PromptTemplateRegistry promptTemplateRegistry) {
        return new AiFacadeInitializer(aiFacade, serviceProvider, embeddingModelProvider,
                toolCallingServiceProvider, promptTemplateRegistry);
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

