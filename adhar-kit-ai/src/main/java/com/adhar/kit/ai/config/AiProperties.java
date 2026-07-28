package com.adhar.kit.ai.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for AI services.
 * Centralizes all AI-related configuration with environment-specific overrides.
 */
@Data
@Component
@ConfigurationProperties(prefix = "adhar.ai")
public class AiProperties {

    /**
     * Enable AI module.
     */
    private boolean enabled = true;

    /**
     * Default AI model to use.
     */
    private String defaultModel = "gpt-3.5-turbo";

    /**
     * Maximum tokens for AI responses.
     */
    private int maxTokens = 1000;

    /**
     * Temperature for AI responses (0.0 to 1.0).
     */
    private double temperature = 0.7;

    /**
     * Default AI provider.
     */
    private String provider = "openai";

    /**
     * Request timeout in seconds.
     */
    private int timeout = 30;

    /**
     * Maximum number of retries.
     */
    private int maxRetries = 3;

    /**
     * Annotation-based configuration.
     */
    private Annotations annotations = new Annotations();

    private OpenAi openAi = new OpenAi();
    private Azure azure = new Azure();
    private Ollama ollama = new Ollama();
    private VectorStore vectorStore = new VectorStore();
    private RateLimiting rateLimiting = new RateLimiting();
    private Caching caching = new Caching();
    private Security security = new Security();
    private Metrics metrics = new Metrics();
    private Costs costs = new Costs();
    private Tools tools = new Tools();
    private Guardrails guardrails = new Guardrails();

    @Data
    public static class Annotations {
        /**
         * Enable annotation processing.
         */
        private boolean enabled = true;

        /**
         * Enable @AiChat annotation.
         */
        private boolean aiChatEnabled = true;

        /**
         * Enable @AiEmbedding annotation.
         */
        private boolean aiEmbeddingEnabled = true;

        /**
         * Enable @AiRag annotation.
         */
        private boolean aiRagEnabled = true;

        /**
         * Enable @AiImageGeneration annotation.
         */
        private boolean aiImageGenerationEnabled = true;

        /**
         * Enable @AiVision annotation.
         */
        private boolean aiVisionEnabled = true;

        /**
         * Enable @AiFunction annotation.
         */
        private boolean aiFunctionEnabled = true;

        /**
         * Enable @AiCache annotation.
         */
        private boolean aiCacheEnabled = true;

        /**
         * Enable @AiMetrics annotation.
         */
        private boolean aiMetricsEnabled = true;
    }

    @Data
    public static class Metrics {
        /**
         * Enable metrics collection.
         */
        private boolean enabled = true;

        /**
         * Track token usage.
         */
        private boolean trackTokens = true;

        /**
         * Track costs.
         */
        private boolean trackCost = true;

        /**
         * Track latency.
         */
        private boolean trackLatency = true;

        /**
         * Export to Prometheus.
         */
        private boolean exportPrometheus = true;
    }

    @Data
    public static class OpenAi {
        private String apiKey;
        private String model = "gpt-3.5-turbo";
        private String baseUrl = "https://api.openai.com";
        private Duration timeout = Duration.ofSeconds(60);
        private Integer maxTokens = 1000;
        private Double temperature = 0.7;
        private Integer maxRetries = 3;
    }

    @Data
    public static class Azure {
        private String apiKey;
        private String endpoint;
        private String deploymentName;
        private String model = "gpt-35-turbo";
        private Duration timeout = Duration.ofSeconds(60);
        private Integer maxTokens = 1000;
        private Double temperature = 0.7;
    }

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama2";
        private Duration timeout = Duration.ofSeconds(120);
        private Integer maxTokens = 2000;
        private Double temperature = 0.7;
    }

    @Data
    public static class VectorStore {
        private String type = "redis"; // redis, chroma, pinecone
        private String connectionString;
        private String indexName = "adhar-vectors";
        private Integer dimensions = 1536;
        private String similarityFunction = "cosine";
        private Map<String, Object> metadata;
    }

    @Data
    public static class RateLimiting {
        private Boolean enabled = true;
        private Integer requestsPerMinute = 60;
        private Integer requestsPerHour = 1000;
        private Integer requestsPerDay = 10000;
        private Duration windowSize = Duration.ofMinutes(1);
    }

    @Data
    public static class Caching {
        private Boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(30);
        private Integer maxSize = 1000;
        private String provider = "caffeine"; // caffeine, redis

        /** Semantic (embedding-similarity) response cache settings. */
        private Semantic semantic = new Semantic();

        /**
         * Configuration for the optional semantic response cache layered on top of
         * the exact-hash {@code @AiCache}. When enabled <b>and</b> an
         * {@code EmbeddingModel} bean is available, cache misses are additionally
         * matched by embedding cosine similarity against recent entries.
         */
        @Data
        public static class Semantic {
            /** Enable the embedding-similarity cache path. */
            private boolean enabled = false;

            /**
             * Minimum cosine similarity (0..1) for a cached embedding to be treated
             * as a hit for a new prompt.
             */
            private double similarityThreshold = 0.95;

            /** Maximum number of embeddings retained per cache (bounded LRU). */
            private int maxEntries = 1000;
        }
    }

    /**
     * Function/tool-calling loop settings.
     */
    @Data
    public static class Tools {
        /** Enable auto-configuration of the tool-calling service. */
        private boolean enabled = true;

        /**
         * Maximum number of tool-execution rounds before the loop returns the
         * model's latest (possibly partial) answer.
         */
        private int maxIterations = 5;
    }

    /**
     * Guardrail chain settings.
     */
    @Data
    public static class Guardrails {
        /**
         * Enable auto-configuration of the default guardrail chain (content-safety,
         * PII and sensitive-data guardrails adapting {@code AiSecurityValidator}).
         */
        private boolean enabled = true;
    }

    @Data
    public static class Security {
        private Boolean enabled = true;
        private Boolean validateApiKeys = true;
        private Boolean logRequests = false; // Be careful with PII
        private Boolean auditEnabled = true;
        private String[] allowedModels = {"gpt-3.5-turbo", "gpt-4", "llama2"};
    }

    /**
     * Simple per-model cost table (USD per 1,000 tokens) used to estimate the cost
     * of a chat request from the prompt/completion token usage reported by Spring AI.
     * Real-world price lists change frequently and vary per provider, so this is
     * intentionally a coarse, configurable approximation rather than a byte-for-byte
     * mirror of any single vendor's pricing page.
     */
    @Data
    public static class Costs {
        /** Cost entries keyed by model id (e.g. "gpt-4", "claude-3-opus"). */
        private Map<String, ModelCost> models = new HashMap<>(Map.of(
            "gpt-3.5-turbo", new ModelCost(0.0005, 0.0015),
            "gpt-4", new ModelCost(0.03, 0.06)
        ));

        /** Fallback used when the response model isn't present in {@link #models}. */
        private ModelCost defaultCost = new ModelCost(0.0, 0.0);

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ModelCost {
            /** USD cost per 1,000 prompt tokens. */
            private double promptCostPer1k;
            /** USD cost per 1,000 completion tokens. */
            private double completionCostPer1k;
        }
    }
}
