package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Analyzes images using AI vision models.
 *
 * <p>Automatically analyzes images and answers questions about them using
 * GPT-4 Vision, Gemini Pro Vision, or other vision models.</p>
 *
 * <p><b>Example - Image Description:</b></p>
 * <pre>{@code
 * @Service
 * public class ImageAnalysisService {
 *
 *     @AiVision(
 *         question = "Describe this image in detail"
 *     )
 *     public String describeImage(String imageUrl) {
 *         return null;  // AI description
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Product Detection:</b></p>
 * <pre>{@code
 * @Service
 * public class ProductDetectionService {
 *
 *     @AiVision(
 *         question = "List all products visible in this image with their quantities",
 *         model = "gpt-4-vision-preview"
 *     )
 *     public String detectProducts(String imageUrl) {
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Quality Control:</b></p>
 * <pre>{@code
 * @Service
 * public class QualityControlService {
 *
 *     @AiVision(
 *         question = "Identify any defects or quality issues in this product",
 *         temperature = 0.2,
 *         structured = true
 *     )
 *     public QualityReport analyzeProductQuality(String imageUrl) {
 *         return null;  // Automatically parsed to QualityReport
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - OCR:</b></p>
 * <pre>{@code
 * @Service
 * public class OcrService {
 *
 *     @AiVision(
 *         question = "Extract all text from this image, maintaining structure",
 *         maxTokens = 2000
 *     )
 *     public String extractText(String imageUrl) {
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
public @interface AiVision {

    /**
     * Question to ask about the image.
     * Use {paramName} for parameter substitution.
     */
    String question();

    /**
     * Vision model to use.
     */
    String model() default "gpt-4-vision-preview";

    /**
     * Temperature for response (0.0-1.0).
     */
    double temperature() default 0.7;

    /**
     * Maximum tokens in response.
     */
    int maxTokens() default 1000;

    /**
     * Return structured data (JSON).
     */
    boolean structured() default false;

    /**
     * Detail level: low, high, auto.
     */
    String detail() default "auto";

    /**
     * Cache responses for same image+question.
     */
    boolean cache() default true;

    /**
     * Cache TTL in seconds.
     */
    int cacheTtl() default 3600;
}

