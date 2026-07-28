package com.adhar.kit.ai.config;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.prompt.PromptTemplateRegistry;
import com.adhar.kit.ai.tool.ToolCallingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Connects the {@link AiFacade} singleton to the real Spring-managed AI stack at
 * startup.
 *
 * <p>The facade defaults to a no-op provider that throws on every operation. Once
 * the Spring context is up and a {@code ChatModel}-backed
 * {@link com.adhar.kit.ai.service.AiService} is available, this initializer swaps in
 * a delegating adapter so {@code AiFacade.getInstance()} calls actually reach Spring
 * AI. When no chat service is configured the facade is left in its graceful-degradation
 * state, but the Spring-managed {@link PromptTemplateRegistry} is still installed.</p>
 */
@Slf4j
public class AiFacadeInitializer {

    private final AiFacade facade;
    private final ObjectProvider<com.adhar.kit.ai.service.AiService> serviceProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectProvider<ToolCallingService> toolCallingServiceProvider;
    private final PromptTemplateRegistry promptTemplateRegistry;

    public AiFacadeInitializer(AiFacade facade,
                               ObjectProvider<com.adhar.kit.ai.service.AiService> serviceProvider,
                               ObjectProvider<EmbeddingModel> embeddingModelProvider,
                               ObjectProvider<ToolCallingService> toolCallingServiceProvider,
                               PromptTemplateRegistry promptTemplateRegistry) {
        this.facade = facade;
        this.serviceProvider = serviceProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.toolCallingServiceProvider = toolCallingServiceProvider;
        this.promptTemplateRegistry = promptTemplateRegistry;
    }

    @PostConstruct
    public void connect() {
        com.adhar.kit.ai.service.AiService service = serviceProvider.getIfAvailable();
        if (service == null) {
            log.info("No Spring AI chat service available; AiFacade left in graceful-degradation mode");
            return;
        }
        facade.connect(service,
                embeddingModelProvider.getIfAvailable(),
                toolCallingServiceProvider.getIfAvailable(),
                promptTemplateRegistry);
    }
}
