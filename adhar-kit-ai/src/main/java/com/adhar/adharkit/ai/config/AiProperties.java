package com.adhar.adharkit.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for AI services.
 * Centralizes all AI-related configuration with environment-specific overrides.
 */
@Data
@Component
@ConfigurationProperties(prefix = "adhar.ai")
public class AiProperties {

    private OpenAi openAi = new OpenAi();
    private Azure azure = new Azure();
    private Ollama ollama = new Ollama();
    private VectorStore vectorStore = new VectorStore();
    private RateLimiting rateLimiting = new RateLimiting();
    private Caching caching = new Caching();
    private Security security = new Security();

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
    }

    @Data
    public static class Security {
        private Boolean enabled = true;
        private Boolean validateApiKeys = true;
        private Boolean logRequests = false; // Be careful with PII
        private Boolean auditEnabled = true;
        private String[] allowedModels = {"gpt-3.5-turbo", "gpt-4", "llama2"};
    }
}
