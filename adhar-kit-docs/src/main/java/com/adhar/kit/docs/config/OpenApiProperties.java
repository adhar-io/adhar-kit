package com.adhar.kit.docs.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for OpenAPI/Swagger documentation.
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   docs:
 *     enabled: true
 *     info:
 *       title: "Order Service API"
 *       version: "1.0.0"
 *       description: "Microservice for order management"
 *     security:
 *       enabled: true
 *       jwt-enabled: true
 *     swagger-ui:
 *       enabled: true
 *       path: /swagger-ui.html
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class OpenApiProperties {

    /**
     * Enable OpenAPI documentation.
     */
    private boolean enabled = true;

    /**
     * API information.
     */
    private ApiInfo info = new ApiInfo();

    /**
     * Server configurations.
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * Security configuration.
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * Swagger UI configuration.
     */
    private SwaggerUiConfig swaggerUi = new SwaggerUiConfig();

    /**
     * Documentation paths configuration.
     */
    private PathsConfig paths = new PathsConfig();

    /**
     * API information.
     */
    @Data
    public static class ApiInfo {
        private String title = "API Documentation";
        private String version = "1.0.0";
        private String description = "API Documentation";
        private String termsOfService;
        private ContactInfo contact;
        private LicenseInfo license;
    }

    /**
     * Contact information.
     */
    @Data
    public static class ContactInfo {
        private String name;
        private String email;
        private String url;
    }

    /**
     * License information.
     */
    @Data
    public static class LicenseInfo {
        private String name;
        private String url;
    }

    /**
     * Server configuration.
     */
    @Data
    public static class ServerConfig {
        private String url;
        private String description;
    }

    /**
     * Security configuration.
     */
    @Data
    public static class SecurityConfig {
        private boolean enabled = true;
        private boolean jwtEnabled = false;
        private boolean apiKeyEnabled = false;
        private boolean oauth2Enabled = false;
        private boolean basicAuthEnabled = false;
        private String apiKeyHeader = "X-API-KEY";
    }

    /**
     * Swagger UI configuration.
     */
    @Data
    public static class SwaggerUiConfig {
        private boolean enabled = true;
        private String path = "/swagger-ui.html";
        private String configUrl = "/v3/api-docs/swagger-config";
        private boolean displayRequestDuration = true;
        private boolean showExtensions = true;
        private boolean showCommonExtensions = true;
        private String defaultModelsExpandDepth = "1";
        private String defaultModelExpandDepth = "1";
        private String docExpansion = "none";
        private boolean filter = false;
        private int maxDisplayedTags;
        private String operationsSorter;
        private boolean showRequestHeaders = false;
        private String tagsSorter;
        private String validatorUrl;
    }

    /**
     * Documentation paths configuration.
     */
    @Data
    public static class PathsConfig {
        private String apiDocs = "/v3/api-docs";
        private String apiDocsYaml = "/v3/api-docs.yaml";
        private List<String> packagesToScan = new ArrayList<>();
        private List<String> pathsToMatch = new ArrayList<>();
        private List<String> pathsToExclude = new ArrayList<>();
    }
}

