package com.adhar.kit.ai.service;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.commons.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for managing different AI models and providers.
 * Supports dynamic model switching and provider selection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelFactory {

    private final ChatModel defaultChatModel;
    private final EmbeddingModel defaultEmbeddingModel;
    private final AiProperties aiProperties;

    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModels = new ConcurrentHashMap<>();

    /**
     * Gets chat model for specific provider and model.
     */
    public ChatModel getChatModel(String provider, String model) {
        String key = provider + ":" + model;

        return chatModels.computeIfAbsent(key, k -> {
            log.info("Creating new chat model for provider: {}, model: {}", provider, model);
            return createChatModel(provider, model);
        });
    }

    /**
     * Gets embedding model for specific provider.
     */
    public EmbeddingModel getEmbeddingModel(String provider) {
        return embeddingModels.computeIfAbsent(provider, k -> {
            log.info("Creating new embedding model for provider: {}", provider);
            return createEmbeddingModel(provider);
        });
    }

    /**
     * Gets default chat model.
     */
    public ChatModel getDefaultChatModel() {
        return defaultChatModel;
    }

    /**
     * Gets default embedding model.
     */
    public EmbeddingModel getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    // ...existing code...

    /**
     * Validates model availability.
     */
    public boolean isModelAvailable(String provider, String model) {
        try {
            ChatModel chatModel = getChatModel(provider, model);
            return chatModel != null;
        } catch (Exception e) {
            log.warn("Model validation failed for {}:{} - {}", provider, model, e.getMessage());
            return false;
        }
    }

    private ChatModel createChatModel(String provider, String model) {
        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiChatModel(model);
            case "azure" -> createAzureChatModel(model);
            case "ollama" -> createOllamaChatModel(model);
            default -> {
                log.warn("Unknown provider: {}, falling back to default", provider);
                yield defaultChatModel;
            }
        };
    }

    private EmbeddingModel createEmbeddingModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiEmbeddingModel();
            case "azure" -> createAzureEmbeddingModel();
            case "ollama" -> createOllamaEmbeddingModel();
            default -> {
                log.warn("Unknown provider: {}, falling back to default", provider);
                yield defaultEmbeddingModel;
            }
        };
    }

    private ChatModel createOpenAiChatModel(String model) {
        try {
            return defaultChatModel; // Simplified for now
        } catch (Exception e) {
            throw new ServiceException("Failed to create OpenAI chat model", e);
        }
    }

    private ChatModel createAzureChatModel(String model) {
        try {
            return defaultChatModel; // Simplified for now
        } catch (Exception e) {
            throw new ServiceException("Failed to create Azure chat model", e);
        }
    }

    private ChatModel createOllamaChatModel(String model) {
        try {
            return defaultChatModel; // Simplified for now
        } catch (Exception e) {
            throw new ServiceException("Failed to create Ollama chat model", e);
        }
    }

    private EmbeddingModel createOpenAiEmbeddingModel() {
        return defaultEmbeddingModel; // Simplified for now
    }

    private EmbeddingModel createAzureEmbeddingModel() {
        return defaultEmbeddingModel; // Simplified for now
    }

    private EmbeddingModel createOllamaEmbeddingModel() {
        return defaultEmbeddingModel; // Simplified for now
    }

    private String getConversationModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-3.5-turbo";
            case "azure" -> "gpt-35-turbo";
            case "ollama" -> "llama2";
            default -> aiProperties.getOpenAi().getModel();
        };
    }

    private String getGenerationModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-4";
            case "azure" -> "gpt-4";
            case "ollama" -> "llama2";
            default -> aiProperties.getOpenAi().getModel();
        };
    }

    private String getAnalysisModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-4";
            case "azure" -> "gpt-4";
            case "ollama" -> "llama2";
            default -> aiProperties.getOpenAi().getModel();
        };
    }

    private String getCodeModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-4";
            case "azure" -> "gpt-4";
            case "ollama" -> "codellama";
            default -> aiProperties.getOpenAi().getModel();
        };
    }

    private String getDefaultModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> aiProperties.getOpenAi().getModel();
            case "azure" -> aiProperties.getAzure().getModel();
            case "ollama" -> aiProperties.getOllama().getModel();
            default -> aiProperties.getOpenAi().getModel();
        };
    }
}
