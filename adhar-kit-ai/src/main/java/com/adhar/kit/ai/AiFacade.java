package com.adhar.kit.ai;

import com.adhar.kit.ai.api.AiService;
import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.prompt.PromptTemplateRegistry;
import com.adhar.kit.ai.tool.AiTool;
import com.adhar.kit.ai.tool.ToolCallingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Universal AI Facade providing vendor-agnostic access to AI/LLM capabilities.
 *
 * <p>This facade supports multiple AI providers through a unified API:</p>
 * <ul>
 *   <li><b>OpenAI</b> - Industry-leading GPT models</li>
 *   <li><b>Azure OpenAI</b> - Enterprise OpenAI on Azure</li>
 *   <li><b>Anthropic Claude</b> - Advanced reasoning and coding</li>
 *   <li><b>Google Gemini</b> - Multimodal AI from Google</li>
 *   <li><b>AWS Bedrock</b> - Various models on AWS</li>
 *   <li><b>Ollama</b> - Run LLMs locally</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 *
 * <p><b>1. Simple Chat:</b></p>
 * <pre>{@code
 * AiFacade ai = AiFacade.getInstance();
 * String response = ai.chat("Explain quantum computing in simple terms");
 * System.out.println(response);
 * }</pre>
 *
 * <p><b>2. Chat with Context:</b></p>
 * <pre>{@code
 * List<ChatMessage> history = new ArrayList<>();
 * history.add(new ChatMessageImpl("user", "What is photosynthesis?"));
 * history.add(new ChatMessageImpl("assistant", "Photosynthesis is..."));
 *
 * String response = ai.chatWithContext(history, "How does it differ in C4 plants?");
 * }</pre>
 *
 * <p><b>3. Streaming Responses:</b></p>
 * <pre>{@code
 * ai.chatStream("Write a story about AI", chunk -> {
 *     System.out.print(chunk);  // Print each token as it arrives
 * });
 * }</pre>
 *
 * <p><b>4. Embeddings and Semantic Search:</b></p>
 * <pre>{@code
 * // Generate embedding
 * List<Float> embedding = ai.embed("Machine learning fundamentals");
 *
 * // Find similar documents
 * List<String> docs = List.of(
 *     "Introduction to neural networks",
 *     "Cooking recipes for beginners",
 *     "Deep learning applications"
 * );
 * List<SimilarityResult> similar = ai.findSimilar("AI tutorials", docs, 2);
 * }</pre>
 *
 * <p><b>5. RAG (Retrieval Augmented Generation):</b></p>
 * <pre>{@code
 * // Store documents
 * ai.storeDocument("doc1", "Company policy: Remote work is allowed 3 days/week",
 *     Map.of("category", "policy"));
 * ai.storeDocument("doc2", "Company policy: Vacation days are 20 per year",
 *     Map.of("category", "policy"));
 *
 * // Query with context
 * RagResponse answer = ai.queryDocuments("How many vacation days do I get?", 3);
 * System.out.println(answer.getAnswer());
 * System.out.println("Sources: " + answer.getSources());
 * }</pre>
 *
 * <p><b>6. Image Generation:</b></p>
 * <pre>{@code
 * ImageRequest request = ImageRequest.builder()
 *     .prompt("A serene mountain landscape at sunset")
 *     .size("1024x1024")
 *     .quality("hd")
 *     .build();
 * ImageResult result = ai.generateImage(request);
 * System.out.println("Image URL: " + result.getUrls().get(0));
 * }</pre>
 *
 * <p><b>7. Function Calling:</b></p>
 * <pre>{@code
 * // Define function
 * AiFunction getWeather = AiFunction.builder()
 *     .name("get_weather")
 *     .description("Get current weather for a location")
 *     .parameters(Map.of(
 *         "type", "object",
 *         "properties", Map.of(
 *             "location", Map.of("type", "string"),
 *             "unit", Map.of("type", "string", "enum", List.of("celsius", "fahrenheit"))
 *         ),
 *         "required", List.of("location")
 *     ))
 *     .build();
 *
 * FunctionCallResponse response = ai.chatWithFunctions(
 *     "What's the weather in Paris?",
 *     List.of(getWeather)
 * );
 *
 * if (response.hasFunctionCalls()) {
 *     FunctionCall call = response.getFunctionCalls().get(0);
 *     // Execute function and return result to AI
 * }
 * }</pre>
 *
 * <p><b>8. Token Counting and Cost Estimation:</b></p>
 * <pre>{@code
 * String longText = "..."; // Your text
 * int tokens = ai.countTokens(longText);
 * double cost = ai.estimateCost(longText);
 * System.out.printf("Tokens: %d, Estimated cost: $%.4f%n", tokens, cost);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see AiService
 */
@Slf4j
public class AiFacade implements AiService {

    /**
     * Singleton instance holder.
     * Uses volatile to ensure visibility across threads.
     */
    private static volatile AiFacade instance;

    /**
     * The underlying AI provider (OpenAI, Azure, Claude, etc.).
     *
     * <p>Starts as the no-op {@link DefaultAiProvider} and is replaced at runtime by
     * {@link #connect} once the Spring context has a real {@code ChatModel}-backed
     * service available. Declared {@code volatile} for safe publication across
     * threads.</p>
     */
    private volatile AiProvider provider;

    /**
     * Named prompt template registry, usable even before a provider is connected.
     * Replaced by the Spring-managed registry when {@link #connect} runs.
     */
    private volatile PromptTemplateRegistry promptRegistry = new PromptTemplateRegistry();

    /**
     * Indicates if the AI service is available and ready.
     * Set to true after successful initialization and connection test.
     */
    private volatile boolean available = false;

    /**
     * Private constructor for singleton pattern.
     * Initializes the AI provider and tests connectivity.
     */
    private AiFacade() {
        log.info("Initializing AI Facade v1.0.0");

        // Detect which AI provider is configured (OpenAI, Azure, Ollama, etc.)
        this.provider = detectProvider();

        try {
            // Test connection to verify provider is accessible
            testConnection();
            this.available = true;
            log.info("AI provider {} initialized successfully", provider.getName());
        } catch (Exception e) {
            // Graceful degradation - facade is created but unavailable
            log.warn("AI provider not fully available: {}", e.getMessage());
        }
    }

    /**
     * Gets the singleton instance.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * @return the AiFacade singleton instance
     */
    public static AiFacade getInstance() {
        // First check without synchronization (performance)
        if (instance == null) {
            synchronized (AiFacade.class) {
                // Second check with synchronization (safety)
                if (instance == null) {
                    instance = new AiFacade();
                }
            }
        }
        return instance;
    }

    // ==================== Provider Wiring ====================

    /**
     * Connects this facade to the real Spring-managed AI stack, replacing the no-op
     * {@link DefaultAiProvider} so facade operations delegate to the same pipeline
     * as the REST/annotation paths (guardrails, rate limiting, caching, token/cost
     * tracking). Invoked by {@code AiAutoConfiguration} at startup once a
     * {@code ChatModel}-backed {@link com.adhar.kit.ai.service.AiService} exists.
     *
     * @param springService  the Spring AI-backed chat/RAG/embedding service
     * @param embeddingModel  embedding model (nullable) used for facade-side
     *                        {@code findSimilar}
     * @param toolCallingService tool-calling service backing {@code chatWithFunctions}
     * @param promptRegistry  the Spring-managed prompt template registry
     */
    public void connect(com.adhar.kit.ai.service.AiService springService,
                        EmbeddingModel embeddingModel,
                        ToolCallingService toolCallingService,
                        PromptTemplateRegistry promptRegistry) {
        this.provider = new SpringServiceProvider(springService, embeddingModel, toolCallingService);
        if (promptRegistry != null) {
            this.promptRegistry = promptRegistry;
        }
        this.available = true;
        log.info("AiFacade connected to Spring-managed AI provider");
    }

    /**
     * Restores the facade to its freshly-constructed state (no-op provider, empty
     * prompt registry). Package-private, intended only for tests that exercise
     * {@link #connect} against the shared singleton so they can avoid leaking a
     * connected provider into unrelated tests.
     */
    void resetToDefaultForTesting() {
        this.provider = new DefaultAiProvider();
        this.promptRegistry = new PromptTemplateRegistry(null);
        this.available = true;
    }

    // ==================== Prompt Templates ====================

    /**
     * Registers a named prompt template on the facade's registry.
     */
    public void registerPromptTemplate(String name, String template) {
        promptRegistry.register(name, template);
    }

    /**
     * Renders a named prompt template, substituting {@code {param}} placeholders.
     */
    public String renderPrompt(String name, Map<String, Object> params) {
        return promptRegistry.render(name, params);
    }

    /**
     * @return the facade's prompt template registry
     */
    public PromptTemplateRegistry getPromptRegistry() {
        return promptRegistry;
    }

    // ==================== Chat Completions ====================

    @Override
    public String chat(String message) {
        try {
            log.debug("Chat request: {}", truncate(message, 100));
            String response = provider.chat(message);
            log.debug("Chat response: {}", truncate(response, 100));
            return response;
        } catch (Exception e) {
            log.error("Chat failed", e);
            throw new AiException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String message) {
        try {
            log.debug("Chat with system prompt: {}", truncate(systemPrompt, 50));
            return provider.chat(systemPrompt, message);
        } catch (Exception e) {
            log.error("Chat with system prompt failed", e);
            throw new AiException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chatWithContext(List<ChatMessage> conversationHistory, String message) {
        try {
            log.debug("Chat with context: {} previous messages", conversationHistory.size());
            return provider.chatWithContext(conversationHistory, message);
        } catch (Exception e) {
            log.error("Chat with context failed", e);
            throw new AiException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            log.debug("Advanced chat request with {} messages",
                request.getMessages() != null ? request.getMessages().size() : 0);
            return provider.chat(request);
        } catch (Exception e) {
            log.error("Advanced chat failed", e);
            throw new AiException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(String message, Consumer<String> callback) {
        try {
            log.debug("Streaming chat: {}", truncate(message, 100));
            provider.chatStream(message, callback);
        } catch (Exception e) {
            log.error("Streaming chat failed", e);
            throw new AiException("Streaming chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(ChatRequest request, Consumer<ChatChunk> callback) {
        try {
            log.debug("Advanced streaming chat");
            provider.chatStream(request, callback);
        } catch (Exception e) {
            log.error("Advanced streaming chat failed", e);
            throw new AiException("Streaming chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<String> chatAsync(String message) {
        return CompletableFuture.supplyAsync(() -> chat(message));
    }

    // ==================== Embeddings ====================

    @Override
    public List<Float> embed(String text) {
        try {
            log.debug("Generating embedding for text: {}", truncate(text, 50));
            return provider.embed(text);
        } catch (Exception e) {
            log.error("Embedding generation failed", e);
            throw new AiException("Embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        try {
            log.debug("Generating {} embeddings in batch", texts.size());
            return provider.embedBatch(texts);
        } catch (Exception e) {
            log.error("Batch embedding failed", e);
            throw new AiException("Batch embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public double similarity(String text1, String text2) {
        List<Float> emb1 = embed(text1);
        List<Float> emb2 = embed(text2);
        return cosineSimilarity(emb1, emb2);
    }

    @Override
    public List<SimilarityResult> findSimilar(String query, List<String> candidates, int topK) {
        try {
            log.debug("Finding {} similar items from {} candidates", topK, candidates.size());
            return provider.findSimilar(query, candidates, topK);
        } catch (Exception e) {
            log.error("Similarity search failed", e);
            throw new AiException("Similarity search failed: " + e.getMessage(), e);
        }
    }

    // ==================== Image Generation ====================

    @Override
    public ImageResult generateImage(String prompt) {
        try {
            log.debug("Generating image: {}", truncate(prompt, 50));
            return provider.generateImage(new ImageRequestImpl(prompt, "1024x1024", 1, "standard", "natural"));
        } catch (Exception e) {
            log.error("Image generation failed", e);
            throw new AiException("Image generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ImageResult generateImage(ImageRequest request) {
        try {
            log.debug("Generating image: {}", truncate(request.getPrompt(), 50));
            return provider.generateImage(request);
        } catch (Exception e) {
            log.error("Image generation failed", e);
            throw new AiException("Image generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String analyzeImage(String imageUrl, String question) {
        try {
            log.debug("Analyzing image: {} with question: {}", imageUrl, truncate(question, 50));
            return provider.analyzeImage(imageUrl, question);
        } catch (Exception e) {
            log.error("Image analysis failed", e);
            throw new AiException("Image analysis failed: " + e.getMessage(), e);
        }
    }

    // ==================== Function Calling ====================

    @Override
    public FunctionCallResponse chatWithFunctions(String message, List<AiFunction> functions) {
        try {
            log.debug("Chat with {} functions available", functions.size());
            return provider.chatWithFunctions(message, functions);
        } catch (Exception e) {
            log.error("Function calling chat failed", e);
            throw new AiException("Function calling failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Object executeFunction(FunctionCall functionCall) {
        try {
            log.debug("Executing function: {}", functionCall.getName());
            return provider.executeFunction(functionCall);
        } catch (Exception e) {
            log.error("Function execution failed", e);
            throw new AiException("Function execution failed: " + e.getMessage(), e);
        }
    }

    // ==================== RAG ====================

    @Override
    public void storeDocument(String documentId, String content, Map<String, Object> metadata) {
        try {
            log.debug("Storing document: {}", documentId);
            provider.storeDocument(documentId, content, metadata);
        } catch (Exception e) {
            log.error("Document storage failed", e);
            throw new AiException("Document storage failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeDocuments(List<Document> documents) {
        try {
            log.debug("Storing {} documents in batch", documents.size());
            provider.storeDocuments(documents);
        } catch (Exception e) {
            log.error("Batch document storage failed", e);
            throw new AiException("Batch storage failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RagResponse queryDocuments(String question, int topK) {
        try {
            log.debug("RAG query: {} (top {})", truncate(question, 50), topK);
            return provider.queryDocuments(question, topK);
        } catch (Exception e) {
            log.error("RAG query failed", e);
            throw new AiException("RAG query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocument(String documentId) {
        try {
            log.debug("Deleting document: {}", documentId);
            provider.deleteDocument(documentId);
        } catch (Exception e) {
            log.error("Document deletion failed", e);
            throw new AiException("Document deletion failed: " + e.getMessage(), e);
        }
    }

    // ==================== Model Management ====================

    @Override
    public List<String> listModels() {
        try {
            return provider.listModels();
        } catch (Exception e) {
            log.error("Listing models failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ModelInfo getModelInfo() {
        try {
            return provider.getModelInfo();
        } catch (Exception e) {
            log.error("Getting model info failed", e);
            throw new AiException("Get model info failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void useModel(String modelId) {
        try {
            log.info("Switching to model: {}", modelId);
            provider.useModel(modelId);
        } catch (Exception e) {
            log.error("Model switch failed", e);
            throw new AiException("Model switch failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int countTokens(String text) {
        try {
            return provider.countTokens(text);
        } catch (Exception e) {
            log.error("Token counting failed", e);
            // Fallback: rough estimate
            return text.length() / 4;
        }
    }

    @Override
    public double estimateCost(String text) {
        try {
            return provider.estimateCost(text);
        } catch (Exception e) {
            log.error("Cost estimation failed", e);
            return 0.0;
        }
    }

    // ==================== Utilities ====================

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProvider() {
        return provider != null ? provider.getName() : "none";
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("available", available);
        health.put("provider", getProvider());

        if (provider != null) {
            health.putAll(provider.health());
        }

        return health;
    }

    // ==================== Private Helpers ====================

    /**
     * Detects which AI provider is configured.
     *
     * <p>Detection strategy:</p>
     * <ol>
     *   <li>Check for Spring AI beans (OpenAI, Azure, Ollama)</li>
     *   <li>Check configuration properties</li>
     *   <li>Check environment variables</li>
     *   <li>Fall back to default provider</li>
     * </ol>
     *
     * @return the detected AI provider
     */
    private AiProvider detectProvider() {
        log.info("Detecting AI provider...");

        // TODO: In production, this would:
        // 1. Check for @Autowired Spring AI ChatClient beans
        // 2. Check spring.ai.openai.api-key property
        // 3. Check OPENAI_API_KEY environment variable
        // 4. Try Ollama at localhost:11434
        // 5. Check for Azure OpenAI configuration
        // 6. Return appropriate provider implementation

        return new DefaultAiProvider();
    }

    /**
     * Tests connection to the AI provider.
     * Throws exception if provider is not accessible.
     *
     * @throws Exception if connection test fails
     */
    private void testConnection() {
        if (provider != null) {
            provider.test();
        }
    }

    /**
     * Truncates text for logging to avoid log spam.
     *
     * @param text the text to truncate
     * @param maxLength maximum length before truncation
     * @return truncated text with ellipsis if needed
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * Calculates cosine similarity between two embedding vectors.
     *
     * <p>Cosine similarity ranges from -1 to 1:</p>
     * <ul>
     *   <li>1 = identical vectors</li>
     *   <li>0 = orthogonal (unrelated)</li>
     *   <li>-1 = opposite vectors</li>
     * </ul>
     *
     * @param v1 first embedding vector
     * @param v2 second embedding vector
     * @return cosine similarity score (0-1 for normalized vectors)
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // ==================== Provider Interface ====================

    /**
     * Internal provider interface (implementation detail).
     *
     * <p>This interface defines the contract that all AI provider implementations
     * must fulfill. Each provider (OpenAI, Azure, Claude, etc.) will implement
     * these methods using their specific SDKs and APIs.</p>
     *
     * <p><b>Implementation Guidelines:</b></p>
     * <ul>
     *   <li>Use provider-specific SDK when available (e.g., Spring AI, LangChain4j)</li>
     *   <li>Handle rate limiting and retries internally</li>
     *   <li>Convert provider-specific exceptions to AiException</li>
     *   <li>Log all requests and responses for debugging</li>
     *   <li>Track usage metrics (tokens, cost, latency)</li>
     * </ul>
     */
    private interface AiProvider {
        /** @return provider name (e.g., "openai", "azure", "claude") */
        String getName();

        /** Test connection to provider. Throws exception if unavailable. */
        void test();

        /** Simple chat completion */
        String chat(String message);

        /** Chat with system prompt */
        String chat(String systemPrompt, String message);

        /** Chat with conversation history */
        String chatWithContext(List<ChatMessage> history, String message);

        /** Advanced chat with full options */
        ChatResponse chat(ChatRequest request);

        /** Stream chat response token by token */
        void chatStream(String message, Consumer<String> callback);

        /** Stream advanced chat response */
        void chatStream(ChatRequest request, Consumer<ChatChunk> callback);

        /** Generate embedding vector for text */
        List<Float> embed(String text);

        /** Generate embeddings in batch (more efficient) */
        List<List<Float>> embedBatch(List<String> texts);

        /** Find semantically similar texts */
        List<SimilarityResult> findSimilar(String query, List<String> candidates, int topK);

        /** Generate image from text prompt */
        ImageResult generateImage(ImageRequest request);

        /** Analyze image and answer question (vision) */
        String analyzeImage(String imageUrl, String question);

        /** Chat with function calling capability */
        FunctionCallResponse chatWithFunctions(String message, List<AiFunction> functions);

        /** Execute function called by AI */
        Object executeFunction(FunctionCall functionCall);

        /** Store document in vector database */
        void storeDocument(String id, String content, Map<String, Object> metadata);

        /** Store multiple documents in batch */
        void storeDocuments(List<Document> documents);

        /** Query documents and generate answer (RAG) */
        RagResponse queryDocuments(String question, int topK);

        /** Delete document from vector store */
        void deleteDocument(String documentId);

        /** List available models */
        List<String> listModels();

        /** Get current model information */
        ModelInfo getModelInfo();

        /** Switch to different model */
        void useModel(String modelId);

        /** Count tokens in text */
        int countTokens(String text);

        /** Estimate cost for text */
        double estimateCost(String text);

        /** Get health status */
        Map<String, Object> health();
    }

    /**
     * Default AI provider implementation (placeholder).
     *
     * <p>This is a no-op provider used when no actual AI provider is configured.
     * It allows the facade to be created without errors, but all AI operations
     * will throw UnsupportedOperationException.</p>
     *
     * <p>In production, this will be replaced by actual provider implementations:</p>
     * <ul>
     *   <li><b>OpenAiProvider</b> - Uses Spring AI OpenAI integration</li>
     *   <li><b>AzureOpenAiProvider</b> - Uses Azure OpenAI Service</li>
     *   <li><b>ClaudeProvider</b> - Uses Anthropic Claude API</li>
     *   <li><b>OllamaProvider</b> - Uses local Ollama for development</li>
     * </ul>
     *
     * <p>Provider selection is automatic based on:</p>
     * <ol>
     *   <li>Available Spring beans (@Autowired ChatClient)</li>
     *   <li>Configuration properties (spring.ai.openai.api-key)</li>
     *   <li>Environment variables (OPENAI_API_KEY, AZURE_OPENAI_ENDPOINT)</li>
     * </ol>
     */
    private static class DefaultAiProvider implements AiProvider {
        @Override
        public String getName() {
            return "default";
        }

        @Override
        public void test() {
            // Test connection
        }

        @Override
        public String chat(String message) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public String chat(String systemPrompt, String message) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public String chatWithContext(List<ChatMessage> history, String message) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void chatStream(String message, Consumer<String> callback) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void chatStream(ChatRequest request, Consumer<ChatChunk> callback) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public List<Float> embed(String text) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public List<SimilarityResult> findSimilar(String query, List<String> candidates, int topK) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public ImageResult generateImage(ImageRequest request) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public String analyzeImage(String imageUrl, String question) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public FunctionCallResponse chatWithFunctions(String message, List<AiFunction> functions) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public Object executeFunction(FunctionCall functionCall) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void storeDocument(String id, String content, Map<String, Object> metadata) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void storeDocuments(List<Document> documents) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public RagResponse queryDocuments(String question, int topK) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void deleteDocument(String documentId) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public List<String> listModels() {
            return Collections.emptyList();
        }

        @Override
        public ModelInfo getModelInfo() {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public void useModel(String modelId) {
            throw new UnsupportedOperationException("AI provider not configured");
        }

        @Override
        public int countTokens(String text) {
            return text.length() / 4;  // Rough estimate
        }

        @Override
        public double estimateCost(String text) {
            return 0.0;
        }

        @Override
        public Map<String, Object> health() {
            return Map.of("status", "not_configured");
        }
    }

    /**
     * AI exception.
     */
    public static class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ==================== Implementation Classes ====================

    /**
     * Simple implementation of ImageRequest.
     */
    private static class ImageRequestImpl implements ImageRequest {
        private final String prompt;
        private final String size;
        private final int n;
        private final String quality;
        private final String style;

        public ImageRequestImpl(String prompt, String size, int n, String quality, String style) {
            this.prompt = prompt;
            this.size = size;
            this.n = n;
            this.quality = quality;
            this.style = style;
        }

        @Override
        public String getPrompt() { return prompt; }
        @Override
        public String getSize() { return size; }
        @Override
        public int getN() { return n; }
        @Override
        public String getQuality() { return quality; }
        @Override
        public String getStyle() { return style; }
    }

    // ==================== Spring-backed Provider Adapter ====================

    /**
     * {@link AiProvider} adapter delegating facade operations to the Spring-managed
     * {@link com.adhar.kit.ai.service.AiService} (and, for facade-only concerns such
     * as candidate similarity and tool detection, to the {@link EmbeddingModel} and
     * {@link ToolCallingService}). Operations the Spring service does not implement
     * (image generation/vision, document deletion, dynamic model switching) continue
     * to throw {@link UnsupportedOperationException}.
     */
    private static class SpringServiceProvider implements AiProvider {

        private final com.adhar.kit.ai.service.AiService service;
        private final EmbeddingModel embeddingModel;
        private final ToolCallingService toolCallingService;

        SpringServiceProvider(com.adhar.kit.ai.service.AiService service,
                              EmbeddingModel embeddingModel,
                              ToolCallingService toolCallingService) {
            this.service = service;
            this.embeddingModel = embeddingModel;
            this.toolCallingService = toolCallingService;
        }

        @Override
        public String getName() {
            return "spring-ai";
        }

        @Override
        public void test() {
            // Connectivity is validated lazily on first real call to avoid a live
            // model round-trip during facade wiring.
        }

        @Override
        public String chat(String message) {
            return service.chat(AiChatRequest.builder().message(message).build()).getContent();
        }

        @Override
        public String chat(String systemPrompt, String message) {
            AiChatRequest request = AiChatRequest.builder()
                    .message(message)
                    .history(List.of(AiChatRequest.ChatMessage.builder()
                            .role(AiChatRequest.MessageRole.SYSTEM)
                            .content(systemPrompt)
                            .build()))
                    .build();
            return service.chat(request).getContent();
        }

        @Override
        public String chatWithContext(List<ChatMessage> history, String message) {
            List<AiChatRequest.ChatMessage> mapped = new ArrayList<>();
            for (ChatMessage m : history) {
                mapped.add(AiChatRequest.ChatMessage.builder()
                        .role(toRole(m.getRole()))
                        .content(m.getContent())
                        .build());
            }
            AiChatRequest request = AiChatRequest.builder()
                    .message(message)
                    .history(mapped)
                    .build();
            return service.chat(request).getContent();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            AiChatResponse response = service.chat(toModelRequest(request));
            return new ChatResponseImpl(response);
        }

        @Override
        public void chatStream(String message, Consumer<String> callback) {
            AiChatRequest request = AiChatRequest.builder().message(message).build();
            service.chatStream(request).toStream().forEach(chunk -> callback.accept(chunk.getContent()));
        }

        @Override
        public void chatStream(ChatRequest request, Consumer<ChatChunk> callback) {
            service.chatStream(toModelRequest(request)).toStream()
                    .forEach(chunk -> callback.accept(new ChatChunkImpl(chunk.getContent(), false)));
        }

        @Override
        public List<Float> embed(String text) {
            return service.embed(text);
        }

        @Override
        public List<List<Float>> embedBatch(List<String> texts) {
            List<List<Float>> results = new ArrayList<>();
            for (String text : texts) {
                results.add(service.embed(text));
            }
            return results;
        }

        @Override
        public List<SimilarityResult> findSimilar(String query, List<String> candidates, int topK) {
            if (embeddingModel == null) {
                throw new UnsupportedOperationException("No EmbeddingModel available for findSimilar");
            }
            float[] queryEmbedding = embeddingModel.embed(query);
            List<SimilarityResult> scored = new ArrayList<>();
            for (String candidate : candidates) {
                double score = cosine(queryEmbedding, embeddingModel.embed(candidate));
                scored.add(new SimilarityResultImpl(candidate, score, Map.of()));
            }
            scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return scored.subList(0, Math.min(topK, scored.size()));
        }

        @Override
        public ImageResult generateImage(ImageRequest request) {
            throw new UnsupportedOperationException("Image generation not supported by the Spring AI service adapter");
        }

        @Override
        public String analyzeImage(String imageUrl, String question) {
            throw new UnsupportedOperationException("Image analysis not supported by the Spring AI service adapter");
        }

        @Override
        public FunctionCallResponse chatWithFunctions(String message, List<AiFunction> functions) {
            List<AiTool> tools = new ArrayList<>();
            for (AiFunction fn : functions) {
                tools.add(AiTool.builder()
                        .name(fn.getName())
                        .description(fn.getDescription())
                        .handler(noHandler(fn.getName()))
                        .build());
            }
            ToolCallingService.DetectionResult detection = toolCallingService.detectToolCalls(message, tools);
            List<FunctionCall> calls = new ArrayList<>();
            for (ToolCallingService.RequestedCall call : detection.requestedCalls()) {
                calls.add(new FunctionCallImpl(call.name(), call.arguments()));
            }
            return new FunctionCallResponseImpl(detection.text(), calls);
        }

        @Override
        public Object executeFunction(FunctionCall functionCall) {
            return toolCallingService.execute(functionCall.getName(), functionCall.getArguments());
        }

        @Override
        public void storeDocument(String id, String content, Map<String, Object> metadata) {
            service.addDocuments(List.of(new com.adhar.kit.ai.service.AiService.DocumentChunk(
                    id, content, "facade", metadata != null ? metadata : Map.of())), "default");
        }

        @Override
        public void storeDocuments(List<Document> documents) {
            List<com.adhar.kit.ai.service.AiService.DocumentChunk> chunks = new ArrayList<>();
            for (Document doc : documents) {
                chunks.add(new com.adhar.kit.ai.service.AiService.DocumentChunk(
                        doc.getId(), doc.getContent(), "facade",
                        doc.getMetadata() != null ? doc.getMetadata() : Map.of()));
            }
            service.addDocuments(chunks, "default");
        }

        @Override
        public RagResponse queryDocuments(String question, int topK) {
            AiChatResponse response = service.ragChat(AiChatRequest.builder().message(question).build(), "default");
            List<Document> sources = new ArrayList<>();
            for (com.adhar.kit.ai.service.AiService.SimilarityResult result : service.search(question, topK)) {
                sources.add(new DocumentImpl(result.id(), result.content(), result.metadata()));
            }
            return new RagResponseImpl(response.getContent(), sources, 1.0);
        }

        @Override
        public void deleteDocument(String documentId) {
            throw new UnsupportedOperationException("Document deletion not supported by the Spring AI service adapter");
        }

        @Override
        public List<String> listModels() {
            return service.getAvailableModels();
        }

        @Override
        public ModelInfo getModelInfo() {
            List<String> models = service.getAvailableModels();
            String id = models.isEmpty() ? "unknown" : models.get(0);
            return new ModelInfoImpl(id, "spring-ai", 0, 0.0, List.of("chat", "embedding"));
        }

        @Override
        public void useModel(String modelId) {
            throw new UnsupportedOperationException(
                    "Dynamic model switching is not supported; set the model per request instead");
        }

        @Override
        public int countTokens(String text) {
            return text.length() / 4;
        }

        @Override
        public double estimateCost(String text) {
            return 0.0;
        }

        @Override
        public Map<String, Object> health() {
            return Map.of("adapter", "spring-ai");
        }

        private AiChatRequest toModelRequest(ChatRequest request) {
            List<AiChatRequest.ChatMessage> history = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                history.add(AiChatRequest.ChatMessage.builder()
                        .role(AiChatRequest.MessageRole.SYSTEM)
                        .content(request.getSystemPrompt())
                        .build());
            }
            String message = "";
            List<ChatMessage> messages = request.getMessages();
            if (messages != null && !messages.isEmpty()) {
                for (int i = 0; i < messages.size() - 1; i++) {
                    ChatMessage m = messages.get(i);
                    history.add(AiChatRequest.ChatMessage.builder()
                            .role(toRole(m.getRole()))
                            .content(m.getContent())
                            .build());
                }
                message = messages.get(messages.size() - 1).getContent();
            }
            return AiChatRequest.builder()
                    .message(message)
                    .history(history.isEmpty() ? null : history)
                    .parameters(AiChatRequest.AiParameters.builder()
                            .temperature(request.getTemperature())
                            .maxTokens(request.getMaxTokens())
                            .topP(request.getTopP())
                            .frequencyPenalty(request.getFrequencyPenalty())
                            .presencePenalty(request.getPresencePenalty())
                            .stopSequences(request.getStopSequences())
                            .build())
                    .build();
        }

        private static AiChatRequest.MessageRole toRole(String role) {
            if (role == null) {
                return AiChatRequest.MessageRole.USER;
            }
            return switch (role.toLowerCase(Locale.ROOT)) {
                case "system" -> AiChatRequest.MessageRole.SYSTEM;
                case "assistant" -> AiChatRequest.MessageRole.ASSISTANT;
                case "function" -> AiChatRequest.MessageRole.FUNCTION;
                default -> AiChatRequest.MessageRole.USER;
            };
        }

        private static Function<Map<String, Object>, Object> noHandler(String name) {
            return args -> {
                throw new UnsupportedOperationException(
                        "Function '" + name + "' has no registered handler; register an executable tool "
                                + "with the ToolCallingService to execute it");
            };
        }

        private static double cosine(float[] a, float[] b) {
            if (a == null || b == null || a.length != b.length || a.length == 0) {
                return 0.0;
            }
            double dot = 0.0;
            double normA = 0.0;
            double normB = 0.0;
            for (int i = 0; i < a.length; i++) {
                dot += (double) a[i] * b[i];
                normA += (double) a[i] * a[i];
                normB += (double) b[i] * b[i];
            }
            if (normA == 0.0 || normB == 0.0) {
                return 0.0;
            }
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }
    }

    // ==================== api.AiService type implementations ====================

    private record ChatChunkImpl(String content, boolean finished) implements ChatChunk {
        @Override
        public String getContent() { return content; }
        @Override
        public boolean isFinished() { return finished; }
        @Override
        public Map<String, Object> getMetadata() { return Map.of(); }
    }

    private static class ChatResponseImpl implements ChatResponse {
        private final AiChatResponse response;

        ChatResponseImpl(AiChatResponse response) {
            this.response = response;
        }

        @Override
        public String getContent() { return response.getContent(); }
        @Override
        public String getModelId() { return response.getModel(); }
        @Override
        public int getPromptTokens() { return tokens(usage() != null ? usage().getPromptTokens() : null); }
        @Override
        public int getCompletionTokens() { return tokens(usage() != null ? usage().getCompletionTokens() : null); }
        @Override
        public int getTotalTokens() { return tokens(usage() != null ? usage().getTotalTokens() : null); }
        @Override
        public String getFinishReason() {
            return response.getFinishReason() != null ? response.getFinishReason().name() : null;
        }
        @Override
        public Map<String, Object> getMetadata() { return Map.of(); }

        private AiChatResponse.UsageMetrics usage() { return response.getUsage(); }

        private static int tokens(Integer value) { return value != null ? value : 0; }
    }

    private record SimilarityResultImpl(String text, double score, Map<String, Object> metadata)
            implements SimilarityResult {
        @Override
        public String getText() { return text; }
        @Override
        public double getScore() { return score; }
        @Override
        public Map<String, Object> getMetadata() { return metadata; }
    }

    private record FunctionCallImpl(String name, Map<String, Object> arguments) implements FunctionCall {
        @Override
        public String getName() { return name; }
        @Override
        public Map<String, Object> getArguments() { return arguments; }
    }

    private record FunctionCallResponseImpl(String textResponse, List<FunctionCall> functionCalls)
            implements FunctionCallResponse {
        @Override
        public String getTextResponse() { return textResponse; }
        @Override
        public List<FunctionCall> getFunctionCalls() { return functionCalls; }
        @Override
        public boolean hasFunctionCalls() { return functionCalls != null && !functionCalls.isEmpty(); }
    }

    private record DocumentImpl(String id, String content, Map<String, Object> metadata) implements Document {
        @Override
        public String getId() { return id; }
        @Override
        public String getContent() { return content; }
        @Override
        public Map<String, Object> getMetadata() { return metadata; }
    }

    private record RagResponseImpl(String answer, List<Document> sources, double confidence)
            implements RagResponse {
        @Override
        public String getAnswer() { return answer; }
        @Override
        public List<Document> getSources() { return sources; }
        @Override
        public double getConfidence() { return confidence; }
    }

    private record ModelInfoImpl(String id, String provider, int maxTokens, double costPerToken,
                                 List<String> capabilities) implements ModelInfo {
        @Override
        public String getId() { return id; }
        @Override
        public String getProvider() { return provider; }
        @Override
        public int getMaxTokens() { return maxTokens; }
        @Override
        public double getCostPerToken() { return costPerToken; }
        @Override
        public List<String> getCapabilities() { return capabilities; }
    }
}

