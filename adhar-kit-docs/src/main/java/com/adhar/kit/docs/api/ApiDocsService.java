package com.adhar.kit.docs.api;

import java.util.Map;

/**
 * Framework-agnostic API Documentation Service.
 *
 * <p>This interface provides a unified abstraction for API documentation
 * generation across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Documentation Standards:</b></p>
 * <ul>
 *   <li>OpenAPI 3.0/3.1 (Swagger)</li>
 *   <li>JSON Schema</li>
 *   <li>API Blueprint</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ApiDocsService docs = ApiDocsFacade.getInstance();
 *
 * // Configure API info
 * docs.setTitle("Order Service API");
 * docs.setVersion("1.0.0");
 * docs.setDescription("Manages customer orders");
 *
 * // Add security scheme
 * docs.addSecurityScheme("bearer", SecurityScheme.Type.HTTP, "bearer", "JWT");
 *
 * // Documentation auto-generated from annotations
 * // Access at: /swagger-ui.html (Spring) or /q/swagger-ui (Quarkus)
 * }</pre>
 *
 * <p><b>Framework Endpoints:</b></p>
 * <ul>
 *   <li>Spring Boot: /v3/api-docs (JSON), /swagger-ui.html (UI)</li>
 *   <li>Quarkus: /q/openapi (JSON), /q/swagger-ui (UI)</li>
 *   <li>Micronaut: /swagger/views/swagger-ui (UI)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.docs.ApiDocsFacade
 */
public interface ApiDocsService {

    /**
     * Security scheme types for API authentication.
     */
    enum SecuritySchemeType {
        /** HTTP authentication (Basic, Bearer) */
        HTTP,
        /** API Key authentication */
        API_KEY,
        /** OAuth2 authentication */
        OAUTH2,
        /** OpenID Connect */
        OPENID_CONNECT
    }

    /**
     * Sets the API title.
     *
     * @param title the API title
     */
    void setTitle(String title);

    /**
     * Sets the API version.
     *
     * @param version the API version (e.g., "1.0.0")
     */
    void setVersion(String version);

    /**
     * Sets the API description.
     *
     * @param description the API description
     */
    void setDescription(String description);

    /**
     * Sets the terms of service URL.
     *
     * @param termsOfService the terms of service URL
     */
    void setTermsOfService(String termsOfService);

    /**
     * Sets contact information.
     *
     * @param name contact name
     * @param email contact email
     * @param url contact URL
     */
    void setContact(String name, String email, String url);

    /**
     * Sets license information.
     *
     * @param name license name (e.g., "Apache 2.0")
     * @param url license URL
     */
    void setLicense(String name, String url);

    /**
     * Adds a server configuration.
     *
     * @param url server URL
     * @param description server description
     */
    void addServer(String url, String description);

    /**
     * Adds a security scheme for API authentication.
     *
     * @param name security scheme name
     * @param type security scheme type
     * @param scheme authentication scheme (e.g., "bearer", "basic")
     * @param bearerFormat token format (e.g., "JWT")
     */
    void addSecurityScheme(String name, SecuritySchemeType type,
                          String scheme, String bearerFormat);

    /**
     * Adds a tag for grouping operations.
     *
     * @param name tag name
     * @param description tag description
     */
    void addTag(String name, String description);

    /**
     * Gets the OpenAPI specification as JSON.
     *
     * @return OpenAPI JSON specification
     */
    String getOpenApiJson();

    /**
     * Gets the OpenAPI specification as YAML.
     *
     * @return OpenAPI YAML specification
     */
    String getOpenApiYaml();

    /**
     * Checks if API documentation is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Gets all registered tags.
     *
     * @return map of tag names to descriptions
     */
    Map<String, String> getTags();
}

