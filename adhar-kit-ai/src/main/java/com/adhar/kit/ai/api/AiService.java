package com.adhar.kit.ai.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Universal AI Service API providing vendor-agnostic AI/LLM capabilities.
 *
 * <p>This interface provides a unified abstraction for working with multiple AI providers:</p>
 * <ul>
 *   <li><b>OpenAI</b> - GPT-4, GPT-3.5-turbo, text-embedding-ada-002, DALL-E</li>
 *   <li><b>Azure OpenAI</b> - Enterprise OpenAI on Azure</li>
 *   <li><b>Anthropic Claude</b> - Claude 3 Opus, Sonnet, Haiku</li>
 *   <li><b>Google Gemini</b> - Gemini Pro, Gemini Pro Vision</li>
 *   <li><b>AWS Bedrock</b> - Claude, Titan, Jurassic models</li>
 *   <li><b>Ollama</b> - Local LLMs (Llama 2, Mistral, CodeLlama)</li>
 *   <li><b>Hugging Face</b> - Open source models</li>
 * </ul>
 *
 * <p><b>Core Features:</b></p>
 * <ul>
 *   <li>Chat completions with streaming support</li>
 *   <li>Text embeddings for RAG and semantic search</li>
 *   <li>Image generation and vision capabilities</li>
 *   <li>Function/tool calling for agentic workflows</li>
 *   <li>Multi-modal interactions (text, images, audio)</li>
 *   <li>Conversation history and context management</li>
 *   <li>Token counting and cost estimation</li>
 *   <li>Response caching and optimization</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see com.adhar.kit.ai.AiFacade
 */
public interface AiService {

    // ==================== Chat Completions ====================

    /**
     * Sends a simple chat message and gets a response.
     *
     * @param message the user message
     * @return AI response
     */
    String chat(String message);

    /**
     * Sends a chat message with system prompt.
     *
     * @param systemPrompt the system instruction
     * @param message the user message
     * @return AI response
     */
    String chat(String systemPrompt, String message);

    /**
     * Chat with conversation history for context.
     *
     * @param conversationHistory previous messages
     * @param message new message
     * @return AI response
     */
    String chatWithContext(List<ChatMessage> conversationHistory, String message);

    /**
     * Chat with advanced options.
     *
     * @param request the chat request with all options
     * @return AI response
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Streams chat response in real-time.
     *
     * @param message the user message
     * @param callback called for each token/chunk
     */
    void chatStream(String message, Consumer<String> callback);

    /**
     * Streams chat response with full request.
     *
     * @param request the chat request
     * @param callback called for each chunk
     */
    void chatStream(ChatRequest request, Consumer<ChatChunk> callback);

    /**
     * Async chat completion.
     *
     * @param message the user message
     * @return future with response
     */
    CompletableFuture<String> chatAsync(String message);

    // ==================== Embeddings ====================

    /**
     * Generates embedding vector for text.
     *
     * @param text the input text
     * @return embedding vector
     */
    List<Float> embed(String text);

    /**
     * Generates embeddings for multiple texts.
     *
     * @param texts list of texts
     * @return list of embedding vectors
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * Calculates cosine similarity between two texts.
     *
     * @param text1 first text
     * @param text2 second text
     * @return similarity score (0-1)
     */
    double similarity(String text1, String text2);

    /**
     * Finds most similar texts using embeddings.
     *
     * @param query the search query
     * @param candidates candidate texts
     * @param topK number of results
     * @return most similar texts with scores
     */
    List<SimilarityResult> findSimilar(String query, List<String> candidates, int topK);

    // ==================== Image Generation ====================

    /**
     * Generates an image from text description.
     *
     * @param prompt the image description
     * @return generated image URL or base64
     */
    ImageResult generateImage(String prompt);

    /**
     * Generates an image with options.
     *
     * @param request the image generation request
     * @return generated image(s)
     */
    ImageResult generateImage(ImageRequest request);

    /**
     * Analyzes an image and answers questions.
     *
     * @param imageUrl the image URL
     * @param question question about the image
     * @return AI response
     */
    String analyzeImage(String imageUrl, String question);

    // ==================== Function Calling ====================

    /**
     * Chat with function/tool calling capability.
     *
     * @param message the user message
     * @param functions available functions
     * @return response with potential function calls
     */
    FunctionCallResponse chatWithFunctions(String message, List<AiFunction> functions);

    /**
     * Executes function call returned by AI.
     *
     * @param functionCall the function call from AI
     * @return function result
     */
    Object executeFunction(FunctionCall functionCall);

    // ==================== RAG (Retrieval Augmented Generation) ====================

    /**
     * Stores document in vector database for RAG.
     *
     * @param documentId unique document ID
     * @param content document content
     * @param metadata document metadata
     */
    void storeDocument(String documentId, String content, Map<String, Object> metadata);

    /**
     * Stores multiple documents in batch.
     *
     * @param documents list of documents
     */
    void storeDocuments(List<Document> documents);

    /**
     * Queries documents and generates answer.
     *
     * @param question the user question
     * @param topK number of documents to retrieve
     * @return AI-generated answer with sources
     */
    RagResponse queryDocuments(String question, int topK);

    /**
     * Deletes document from vector store.
     *
     * @param documentId the document ID
     */
    void deleteDocument(String documentId);

    // ==================== Model Management ====================

    /**
     * Lists available AI models.
     *
     * @return list of model IDs
     */
    List<String> listModels();

    /**
     * Gets current model information.
     *
     * @return model details
     */
    ModelInfo getModelInfo();

    /**
     * Switches to different model.
     *
     * @param modelId the model ID
     */
    void useModel(String modelId);

    /**
     * Estimates token count for text.
     *
     * @param text the input text
     * @return estimated token count
     */
    int countTokens(String text);

    /**
     * Estimates cost for request.
     *
     * @param text the input text
     * @return estimated cost in USD
     */
    double estimateCost(String text);

    // ==================== Utilities ====================

    /**
     * Checks if AI service is available.
     *
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Gets AI provider name.
     *
     * @return provider name (OpenAI, Azure, etc.)
     */
    String getProvider();

    /**
     * Gets service health status.
     *
     * @return health information
     */
    Map<String, Object> health();

    // ==================== Supporting Classes ====================

    /**
     * Chat message in a conversation.
     */
    interface ChatMessage {
        String getRole();  // system, user, assistant
        String getContent();
        Map<String, Object> getMetadata();
    }

    /**
     * Comprehensive chat request.
     */
    interface ChatRequest {
        String getSystemPrompt();
        List<ChatMessage> getMessages();
        Double getTemperature();
        Integer getMaxTokens();
        Double getTopP();
        Double getFrequencyPenalty();
        Double getPresencePenalty();
        List<String> getStopSequences();
        Map<String, Object> getMetadata();
    }

    /**
     * Chat response with metadata.
     */
    interface ChatResponse {
        String getContent();
        String getModelId();
        int getPromptTokens();
        int getCompletionTokens();
        int getTotalTokens();
        String getFinishReason();
        Map<String, Object> getMetadata();
    }

    /**
     * Streaming chat chunk.
     */
    interface ChatChunk {
        String getContent();
        boolean isFinished();
        Map<String, Object> getMetadata();
    }

    /**
     * Image generation request.
     */
    interface ImageRequest {
        String getPrompt();
        String getSize();  // 256x256, 512x512, 1024x1024
        int getN();  // number of images
        String getQuality();  // standard, hd
        String getStyle();  // vivid, natural
    }

    /**
     * Image generation result.
     */
    interface ImageResult {
        List<String> getUrls();
        List<String> getBase64Images();
        String getRevisedPrompt();
    }

    /**
     * Similarity search result.
     */
    interface SimilarityResult {
        String getText();
        double getScore();
        Map<String, Object> getMetadata();
    }

    /**
     * AI function definition.
     */
    interface AiFunction {
        String getName();
        String getDescription();
        Map<String, Object> getParameters();
    }

    /**
     * Function call from AI.
     */
    interface FunctionCall {
        String getName();
        Map<String, Object> getArguments();
    }

    /**
     * Function calling response.
     */
    interface FunctionCallResponse {
        String getTextResponse();
        List<FunctionCall> getFunctionCalls();
        boolean hasFunctionCalls();
    }

    /**
     * Document for RAG.
     */
    interface Document {
        String getId();
        String getContent();
        Map<String, Object> getMetadata();
    }

    /**
     * RAG query response.
     */
    interface RagResponse {
        String getAnswer();
        List<Document> getSources();
        double getConfidence();
    }

    /**
     * AI model information.
     */
    interface ModelInfo {
        String getId();
        String getProvider();
        int getMaxTokens();
        double getCostPerToken();
        List<String> getCapabilities();
    }
}

