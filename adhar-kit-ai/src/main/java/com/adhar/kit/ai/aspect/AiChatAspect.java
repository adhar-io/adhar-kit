package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiChat;
import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.prompt.PromptSubstitutor;
import com.adhar.kit.ai.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Aspect for processing @AiChat annotations.
 *
 * <p>Intercepts methods annotated with @AiChat and automatically invokes
 * AI chat completion, replacing the method implementation.</p>
 *
 * <p><b>Wiring / bridging note:</b> earlier versions of this aspect called the
 * {@code com.adhar.kit.ai.AiFacade} singleton, whose only real provider
 * implementation ({@code DefaultAiProvider}) throws
 * {@link UnsupportedOperationException} on every operation - meaning the
 * {@code @AiChat} annotation never actually reached Spring AI, even when a real
 * {@code ChatModel} was configured. This aspect now depends on the
 * Spring-managed {@link AiService} (the interface implemented by
 * {@code AiServiceImpl}, which wraps a real Spring AI {@code ChatModel}), so the
 * declarative annotation path exercises the exact same pipeline as the
 * {@code /api/v1/ai/chat} REST endpoint - including guardrails, rate limiting,
 * caching and token/cost tracking performed inside {@code AiServiceImpl}.</p>
 *
 * <p>The {@link AiService} bean only exists once a Spring AI {@code ChatModel}
 * is actually configured (an API key / base-url present for one of the
 * {@code spring-ai-starter-model-*} starters), so it is looked up lazily through
 * an {@link ObjectProvider} rather than injected eagerly. When no bean is
 * available - e.g. this environment/tests have no AI provider configured - the
 * aspect falls through to the original method body, preserving the previous
 * graceful-degradation behaviour.</p>
 *
 * <p><b>Supported Frameworks:</b></p>
 * <ul>
 *   <li>Spring Boot - Uses Spring AOP</li>
 *   <li>Quarkus - Uses Arc interceptors (adapter provided)</li>
 *   <li>Micronaut - Uses Micronaut AOP (adapter provided)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class AiChatAspect {

    private final ObjectProvider<AiService> aiServiceProvider;

    public AiChatAspect(ObjectProvider<AiService> aiServiceProvider) {
        this.aiServiceProvider = aiServiceProvider;
    }

    /**
     * Intercepts methods annotated with @AiChat.
     */
    @Around("@annotation(aiChat)")
    public Object processChatAnnotation(ProceedingJoinPoint joinPoint, AiChat aiChat) throws Throwable {
        AiService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            log.warn("No AiService bean available (no Spring AI ChatModel configured), executing original method");
            return joinPoint.proceed();
        }

        try {
            // Get method signature
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            // Build parameter map
            Map<String, Object> params = buildParameterMap(
                signature.getParameterNames(),
                joinPoint.getArgs()
            );

            // Substitute parameters in prompt
            String prompt = substituteParameters(aiChat.prompt(), params);
            String systemPrompt = aiChat.systemPrompt().isEmpty()
                ? null
                : substituteParameters(aiChat.systemPrompt(), params);

            log.debug("Processing @AiChat: prompt={}, systemPrompt={}, async={}, method={}",
                prompt, systemPrompt, aiChat.async(), method.getName());

            // Handle async execution
            if (aiChat.async()) {
                return handleAsyncChat(aiService, prompt, systemPrompt, aiChat);
            }

            // Synchronous execution
            return handleSyncChat(aiService, prompt, systemPrompt, aiChat);

        } catch (Exception e) {
            log.error("Error processing @AiChat annotation", e);
            throw new RuntimeException("AI chat failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles synchronous chat execution via the real {@link AiService} bean.
     */
    private String handleSyncChat(AiService aiService, String prompt, String systemPrompt, AiChat aiChat) {
        AiChatRequest request = buildRequest(prompt, systemPrompt, aiChat);
        AiChatResponse response = aiService.chat(request);
        return response != null ? response.getContent() : null;
    }

    /**
     * Handles asynchronous chat execution.
     */
    private Object handleAsyncChat(AiService aiService, String prompt, String systemPrompt, AiChat aiChat) {
        return CompletableFuture.supplyAsync(() -> handleSyncChat(aiService, prompt, systemPrompt, aiChat));
    }

    /**
     * Builds the {@link AiChatRequest} sent to the real AI service, carrying over
     * the annotation's temperature/maxTokens/model/stopSequences configuration.
     * The system prompt (if any) is passed as a leading SYSTEM history message,
     * since {@link AiChatRequest} models conversations rather than a bare
     * system-prompt field.
     */
    private AiChatRequest buildRequest(String prompt, String systemPrompt, AiChat aiChat) {
        AiChatRequest.AiChatRequestBuilder builder = AiChatRequest.builder()
            .message(prompt)
            .model(aiChat.model().isEmpty() ? null : aiChat.model())
            .parameters(AiChatRequest.AiParameters.builder()
                .temperature(aiChat.temperature())
                .maxTokens(aiChat.maxTokens())
                .stopSequences(aiChat.stopSequences().length > 0 ? List.of(aiChat.stopSequences()) : null)
                .build());

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.history(List.of(AiChatRequest.ChatMessage.builder()
                .role(AiChatRequest.MessageRole.SYSTEM)
                .content(systemPrompt)
                .build()));
        }

        return builder.build();
    }

    /**
     * Builds parameter map from method parameters.
     */
    private Map<String, Object> buildParameterMap(String[] paramNames, Object[] paramValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            params.put(paramNames[i], paramValues[i]);
        }
        return params;
    }

    /**
     * Substitutes {param} placeholders in template, delegating to the shared
     * {@link PromptSubstitutor} so the annotation path and the
     * {@link com.adhar.kit.ai.prompt.PromptTemplateRegistry} stay behaviourally
     * identical.
     */
    private String substituteParameters(String template, Map<String, Object> params) {
        return PromptSubstitutor.substitute(template, params);
    }
}
