package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Generates images using AI image generation models.
 *
 * <p>Automatically generates images from text descriptions using
 * DALL-E, Stable Diffusion, or other image generation models.</p>
 *
 * <p><b>Example - Simple Image Generation:</b></p>
 * <pre>{@code
 * @Service
 * public class ImageService {
 *
 *     @AiImageGeneration(
 *         prompt = "A {adjective} {subject} in {style} style",
 *         size = "1024x1024"
 *     )
 *     public String generateImage(
 *         String adjective,
 *         String subject,
 *         String style
 *     ) {
 *         return null;  // Returns image URL
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Product Images:</b></p>
 * <pre>{@code
 * @Service
 * public class ProductService {
 *
 *     @AiImageGeneration(
 *         prompt = "Professional product photo of {product.name}, {product.description}",
 *         size = "1024x1024",
 *         quality = "hd",
 *         style = "natural",
 *         n = 3
 *     )
 *     public List<String> generateProductImages(Product product) {
 *         return null;  // Returns 3 image URLs
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Marketing Content:</b></p>
 * <pre>{@code
 * @Service
 * public class MarketingService {
 *
 *     @AiImageGeneration(
 *         prompt = "{campaign.description}, vivid colors, professional photography",
 *         size = "1024x1024",
 *         quality = "hd",
 *         saveToStorage = true,
 *         storagePath = "/marketing/{campaign.id}"
 *     )
 *     public ImageResult generateCampaignImage(Campaign campaign) {
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
public @interface AiImageGeneration {

    /**
     * Image generation prompt template.
     * Use {paramName} or {object.field} for substitution.
     */
    String prompt();

    /**
     * Image size (256x256, 512x512, 1024x1024).
     */
    String size() default "1024x1024";

    /**
     * Number of images to generate.
     */
    int n() default 1;

    /**
     * Quality: standard or hd.
     */
    String quality() default "standard";

    /**
     * Style: vivid or natural.
     */
    String style() default "vivid";

    /**
     * Model to use (dall-e-3, dall-e-2, stable-diffusion).
     */
    String model() default "dall-e-3";

    /**
     * Save images to storage automatically.
     */
    boolean saveToStorage() default false;

    /**
     * Storage path template when saveToStorage is true.
     */
    String storagePath() default "/generated";

    /**
     * Return base64 encoded images instead of URLs.
     */
    boolean returnBase64() default false;
}

