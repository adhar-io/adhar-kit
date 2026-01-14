package com.adhar.kit.docs.integration;

import com.adhar.kit.docs.annotation.EnableOpenApiDocs;
import com.adhar.kit.docs.config.AdharOpenApiConfig;
import com.adhar.kit.docs.processor.OpenApiAnnotationProcessor;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

/**
 * Micronaut integration for Adhar OpenAPI documentation.
 *
 * <p>Provides automatic OpenAPI configuration for Micronaut applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @EnableOpenApiDocs(
 *     title = "Order Service API",
 *     version = "1.0.0",
 *     enableJwtSecurity = true
 * )
 * public class Application {
 *     public static void main(String[] args) {
 *         Micronaut.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>Or use application.yml:</p>
 * <pre>
 * micronaut:
 *   router:
 *     static-resources:
 *       swagger:
 *         paths: classpath:META-INF/swagger
 *         mapping: /swagger/**
 * adhar:
 *   docs:
 *     enabled: true
 *     info:
 *       title: Order Service API
 *       version: 1.0.0
 *     security:
 *       jwt-enabled: true
 * </pre>
 *
 * <p>Or use factory bean:</p>
 * <pre>{@code
 * @Factory
 * public class OpenApiConfig {
 *
 *     @Bean
 *     @Singleton
 *     public OpenAPI customOpenAPI() {
 *         return MicronautOpenApiIntegration.createOpenAPI()
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
public class MicronautOpenApiIntegration {

    /**
     * Creates OpenAPI configuration from @EnableOpenApiDocs annotation.
     *
     * @param applicationClass the Micronaut application class
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
     * Creates default OpenAPI configuration for Micronaut.
     *
     * @return default OpenAPI instance
     */
    public static OpenAPI createDefault() {
        return AdharOpenApiConfig.builder()
            .title("Micronaut API Documentation")
            .version("1.0.0")
            .description("Auto-generated API documentation for Micronaut")
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
     * Checks if Micronaut is available.
     *
     * @return true if Micronaut is on classpath
     */
    public static boolean isMicronautAvailable() {
        try {
            Class.forName("io.micronaut.runtime.Micronaut");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

