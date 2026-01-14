package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to automatically invoke AI chat completion.
 *
 * <p>The method's return value or parameters can be used to construct
 * the AI prompt, and the response is returned or processed automatically.</p>
 *
 * <p><b>Example - Simple Chat:</b></p>
 * <pre>{@code
 * @Service
 * public class TranslationService {
 *
 *     @AiChat(prompt = "Translate to French: {text}")
 *     public String translateToFrench(String text) {
 *         return null;  // AI fills this automatically
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With System Prompt:</b></p>
 * <pre>{@code
 * @Service
 * public class CodeReviewService {
 *
 *     @AiChat(
 *         systemPrompt = "You are an expert code reviewer",
 *         prompt = "Review this code:\n{code}",
 *         temperature = 0.3
 *     )
 *     public String reviewCode(String code) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Async Response:</b></p>
 * <pre>{@code
 * @Service
 * public class ContentGenerator {
 *
 *     @AiChat(
 *         prompt = "Write a blog post about: {topic}",
 *         async = true,
 *         maxTokens = 2000
 *     )
 *     public CompletableFuture<String> generateBlogPost(String topic) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiChat {

    /**
     * The prompt template. Use {paramName} for parameter substitution.
     */
    String prompt();

    /**
     * System prompt to set AI behavior.
     */
    String systemPrompt() default "";

    /**
     * Temperature for response creativity (0.0-1.0).
     * Lower = more focused, Higher = more creative.
     */
    double temperature() default 0.7;

    /**
     * Maximum tokens in response.
     */
    int maxTokens() default 1000;

    /**
     * Execute asynchronously.
     */
    boolean async() default false;

    /**
     * Cache responses for identical inputs.
     */
    boolean cache() default true;

    /**
     * Cache TTL in seconds.
     */
    int cacheTtl() default 3600;

    /**
     * Model to use (overrides default).
     */
    String model() default "";

    /**
     * Stop sequences for completion.
     */
    String[] stopSequences() default {};
}

