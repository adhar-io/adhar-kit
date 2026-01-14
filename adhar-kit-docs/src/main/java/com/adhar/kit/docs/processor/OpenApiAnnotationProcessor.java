package com.adhar.kit.docs.processor;

import com.adhar.kit.docs.annotation.EnableOpenApiDocs;
import com.adhar.kit.docs.config.AdharOpenApiConfig;
import com.adhar.kit.docs.config.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

/**
 * Annotation processor for @EnableOpenApiDocs.
 *
 * <p>Processes the annotation and creates OpenAPI configuration automatically.</p>
 *
 * <p><b>Framework Detection:</b></p>
 * <ul>
 *   <li>Spring Boot - Detects @SpringBootApplication</li>
 *   <li>Quarkus - Detects @ApplicationPath</li>
 *   <li>Micronaut - Detects Micronaut context</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableOpenApiDocs(title = "My API", version = "1.0.0")
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class OpenApiAnnotationProcessor {

    /**
     * Processes @EnableOpenApiDocs annotation.
     *
     * @param annotation the annotation instance
     * @param targetClass the annotated class
     * @return configured OpenAPI instance
     */
    public static OpenAPI processAnnotation(EnableOpenApiDocs annotation, Class<?> targetClass) {
        log.info("Processing @EnableOpenApiDocs on class: {}", targetClass.getName());

        // Create properties from annotation
        OpenApiProperties properties = createPropertiesFromAnnotation(annotation, targetClass);

        // Build OpenAPI configuration
        AdharOpenApiConfig.Builder builder = AdharOpenApiConfig.builder()
            .title(annotation.title())
            .version(annotation.version());

        if (!annotation.description().isEmpty()) {
            builder.description(annotation.description());
        }

        // Add security if enabled
        if (annotation.enableJwtSecurity()) {
            builder.withJwtSecurity();
        }

        if (annotation.enableApiKeySecurity()) {
            builder.withApiKeySecurity("X-API-KEY");
        }

        OpenAPI openAPI = builder.build();

        log.info("OpenAPI configuration created: {} v{}", annotation.title(), annotation.version());

        return openAPI;
    }

    /**
     * Creates properties from annotation.
     */
    private static OpenApiProperties createPropertiesFromAnnotation(
            EnableOpenApiDocs annotation,
            Class<?> targetClass) {

        OpenApiProperties properties = new OpenApiProperties();
        properties.setEnabled(true);

        // API info
        properties.getInfo().setTitle(annotation.title());
        properties.getInfo().setVersion(annotation.version());
        properties.getInfo().setDescription(annotation.description());

        // Security
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setJwtEnabled(annotation.enableJwtSecurity());
        properties.getSecurity().setApiKeyEnabled(annotation.enableApiKeySecurity());

        // Packages to scan
        if (annotation.packagesToScan().length > 0) {
            for (String pkg : annotation.packagesToScan()) {
                properties.getPaths().getPackagesToScan().add(pkg);
            }
        } else {
            // Auto-detect package from target class
            String basePackage = targetClass.getPackage().getName();
            properties.getPaths().getPackagesToScan().add(basePackage);
        }

        return properties;
    }

    /**
     * Detects the framework being used.
     *
     * @return framework name (SPRING_BOOT, QUARKUS, MICRONAUT, or UNKNOWN)
     */
    public static Framework detectFramework() {
        try {
            // Check for Spring Boot
            Class.forName("org.springframework.boot.SpringApplication");
            return Framework.SPRING_BOOT;
        } catch (ClassNotFoundException e) {
            // Not Spring Boot
        }

        try {
            // Check for Quarkus
            Class.forName("io.quarkus.runtime.Quarkus");
            return Framework.QUARKUS;
        } catch (ClassNotFoundException e) {
            // Not Quarkus
        }

        try {
            // Check for Micronaut
            Class.forName("io.micronaut.runtime.Micronaut");
            return Framework.MICRONAUT;
        } catch (ClassNotFoundException e) {
            // Not Micronaut
        }

        return Framework.UNKNOWN;
    }

    /**
     * Framework enumeration.
     */
    public enum Framework {
        SPRING_BOOT,
        QUARKUS,
        MICRONAUT,
        UNKNOWN
    }
}

