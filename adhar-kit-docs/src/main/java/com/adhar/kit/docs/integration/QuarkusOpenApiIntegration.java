package com.adhar.kit.docs.integration;

import com.adhar.kit.docs.annotation.EnableOpenApiDocs;
import com.adhar.kit.docs.config.AdharOpenApiConfig;
import com.adhar.kit.docs.processor.OpenApiAnnotationProcessor;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus integration for Adhar OpenAPI documentation.
 *
 * <p>Provides automatic OpenAPI configuration for Quarkus applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @ApplicationPath("/api")
 * @EnableOpenApiDocs(
 *     title = "Order Service API",
 *     version = "1.0.0",
 *     enableJwtSecurity = true
 * )
 * public class ApiApplication extends Application {
 * }
 * }</pre>
 *
 * <p>Or use application.properties:</p>
 * <pre>
 * quarkus.smallrye-openapi.info-title=Order Service API
 * quarkus.smallrye-openapi.info-version=1.0.0
 * adhar.docs.enabled=true
 * adhar.docs.security.jwt-enabled=true
 * </pre>
 *
 * <p>Or use CDI producer:</p>
 * <pre>{@code
 * @ApplicationScoped
 * public class OpenApiConfig {
 *
 *     @Produces
 *     public OpenAPI customOpenAPI() {
 *         return QuarkusOpenApiIntegration.createOpenAPI()
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
public class QuarkusOpenApiIntegration {

    /**
     * Creates OpenAPI configuration from @EnableOpenApiDocs annotation.
     *
     * @param applicationClass the Quarkus application class
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
     * Creates default OpenAPI configuration for Quarkus.
     *
     * @return default OpenAPI instance
     */
    public static OpenAPI createDefault() {
        return AdharOpenApiConfig.builder()
            .title("Quarkus API Documentation")
            .version("1.0.0")
            .description("Auto-generated API documentation for Quarkus")
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
     * Checks if Quarkus is available.
     *
     * @return true if Quarkus is on classpath
     */
    public static boolean isQuarkusAvailable() {
        try {
            Class.forName("io.quarkus.runtime.Quarkus");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

