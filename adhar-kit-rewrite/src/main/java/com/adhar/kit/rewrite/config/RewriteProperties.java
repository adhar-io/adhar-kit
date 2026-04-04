package com.adhar.kit.rewrite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Kit Rewrite module.
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   rewrite:
 *     enabled: true
 *     default-recipe-set: adhar-full-modernization
 *     output-dir: target/rewrite
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.rewrite")
public class RewriteProperties {

    /**
     * Enable the rewrite module.
     */
    private boolean enabled = true;

    /**
     * Default recipe set key to use when none is specified.
     */
    private String defaultRecipeSet = "adhar-full-modernization";

    /**
     * Output directory for generated rewrite configuration files.
     */
    private String outputDir = "target/rewrite";
}
