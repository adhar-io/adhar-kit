package com.adhar.kit.docs.integration;

import com.adhar.kit.docs.annotation.EnableOpenApiDocs;
import com.adhar.kit.docs.config.AdharOpenApiConfig;
import com.adhar.kit.docs.processor.OpenApiAnnotationProcessor;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Boot integration for Adhar OpenAPI documentation.
 *
 * <p>Provides automatic OpenAPI configuration for Spring Boot applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableOpenApiDocs(
 *     title = "Order Service API",
 *     version = "1.0.0",
 *     enableJwtSecurity = true
 * )
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>Or use programmatic configuration:</p>
 * <pre>{@code
 * @Configuration
 * public class OpenApiConfig {
 *
 *     @Bean
 *     public OpenAPI customOpenAPI() {
 *         return SpringBootOpenApiIntegration.createOpenAPI()
 *             .title("Order Service API")
 *             .version("1.0.0")
 *             .withJwtSecurity()
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SpringBootOpenApiIntegration {

    /**
     * Creates OpenAPI configuration from @EnableOpenApiDocs annotation.
     *
     * @param applicationClass the Spring Boot application class
     * @return configured OpenAPI instance
     */
    public static OpenAPI createFromAnnotation(Class<?> applicationClass) {
        if (!applicationClass.isAnnotationPresent(EnableOpenApiDocs.class)) {
            log.warn("@EnableOpenApiDocs not found on {}, using defaults",
                    applicationClass.getName());
            return createDefault();
        }

        EnableOpenApiDocs annotation = applicationClass.getAnnotation(EnableOpenApiDocs.class);
        return OpenApiAnnotationProcessor.processAnnotation(annotation, applicationClass);
    }

    /**
     * Creates default OpenAPI configuration.
     *
     * @return default OpenAPI instance
     */
    public static OpenAPI createDefault() {
        return AdharOpenApiConfig.builder()
            .title("API Documentation")
            .version("1.0.0")
            .description("Auto-generated API documentation")
            .build();
    }

    /**
     * Creates OpenAPI builder for programmatic configuration.
     *
     * @return OpenAPI builder
     */
    public static AdharOpenApiConfig.Builder createOpenAPI() {
        return AdharOpenApiConfig.builder();
    }

    /**
     * Checks if Spring Boot is available.
     *
     * @return true if Spring Boot is on classpath
     */
    public static boolean isSpringBootAvailable() {
        try {
            Class.forName("org.springframework.boot.SpringApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

