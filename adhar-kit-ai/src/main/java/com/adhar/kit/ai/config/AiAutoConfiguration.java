package com.adhar.kit.ai.config;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.aspect.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration for Adhar Kit AI module.
 *
 * <p>Automatically configures AI aspects and components when the module is present.</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <pre>
 * adhar.ai.enabled=true
 * adhar.ai.annotations.enabled=true
 * adhar.ai.cache.enabled=true
 * adhar.ai.metrics.enabled=true
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(
    prefix = "adhar.ai",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class AiAutoConfiguration {

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

