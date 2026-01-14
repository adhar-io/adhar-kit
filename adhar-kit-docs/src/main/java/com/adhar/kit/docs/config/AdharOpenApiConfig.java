package com.adhar.kit.docs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI/Swagger auto-configuration for enterprise microservices.
 *
 * <p>Provides automatic API documentation with comprehensive customization options.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Auto-generated API documentation</li>
 *   <li>Security scheme configuration (JWT, OAuth2, API Key)</li>
 *   <li>Custom headers and parameters</li>
 *   <li>Multiple server configurations</li>
 *   <li>API versioning support</li>
 *   <li>Custom examples and schemas</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Spring Boot
 * @Configuration
 * public class ApiDocConfig {
 *     @Bean
 *     public OpenAPI customOpenAPI() {
 *         return AdharOpenApiConfig.builder()
 *             .title("Order Service API")
 *             .version("1.0.0")
 *             .description("Microservice for order management")
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
public class AdharOpenApiConfig {

    private final OpenApiProperties properties;

    public AdharOpenApiConfig(OpenApiProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates OpenAPI configuration.
     *
     * @return configured OpenAPI instance
     */
    public OpenAPI createOpenApi() {
        log.info("Configuring OpenAPI documentation: {}", properties.getInfo().getTitle());

        OpenAPI openAPI = new OpenAPI()
            .info(createInfo())
            .servers(createServers());

        // Add security schemes if enabled
        if (properties.getSecurity().isEnabled()) {
            openAPI.components(createComponents())
                   .security(createSecurityRequirements());
        }

        return openAPI;
    }

    /**
     * Creates API information.
     */
    private Info createInfo() {
        Info info = new Info()
            .title(properties.getInfo().getTitle())
            .version(properties.getInfo().getVersion())
            .description(properties.getInfo().getDescription());

        // Add contact information
        if (properties.getInfo().getContact() != null) {
            Contact contact = new Contact()
                .name(properties.getInfo().getContact().getName())
                .email(properties.getInfo().getContact().getEmail())
                .url(properties.getInfo().getContact().getUrl());
            info.contact(contact);
        }

        // Add license information
        if (properties.getInfo().getLicense() != null) {
            License license = new License()
                .name(properties.getInfo().getLicense().getName())
                .url(properties.getInfo().getLicense().getUrl());
            info.license(license);
        }

        return info;
    }

    /**
     * Creates server configurations.
     */
    private List<Server> createServers() {
        List<Server> servers = new ArrayList<>();

        for (OpenApiProperties.ServerConfig serverConfig : properties.getServers()) {
            Server server = new Server()
                .url(serverConfig.getUrl())
                .description(serverConfig.getDescription());
            servers.add(server);
        }

        return servers;
    }

    /**
     * Creates security components.
     */
    private Components createComponents() {
        Components components = new Components();

        // JWT Bearer Authentication
        if (properties.getSecurity().isJwtEnabled()) {
            components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer token authentication"));
        }

        // API Key Authentication
        if (properties.getSecurity().isApiKeyEnabled()) {
            components.addSecuritySchemes("apiKey", new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(properties.getSecurity().getApiKeyHeader())
                .description("API Key authentication"));
        }

        // OAuth2 Authentication
        if (properties.getSecurity().isOauth2Enabled()) {
            components.addSecuritySchemes("oauth2", new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 authentication"));
        }

        // Basic Authentication
        if (properties.getSecurity().isBasicAuthEnabled()) {
            components.addSecuritySchemes("basicAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .description("Basic authentication"));
        }

        return components;
    }

    /**
     * Creates security requirements.
     */
    private List<SecurityRequirement> createSecurityRequirements() {
        List<SecurityRequirement> requirements = new ArrayList<>();

        if (properties.getSecurity().isJwtEnabled()) {
            requirements.add(new SecurityRequirement().addList("bearerAuth"));
        }

        if (properties.getSecurity().isApiKeyEnabled()) {
            requirements.add(new SecurityRequirement().addList("apiKey"));
        }

        if (properties.getSecurity().isOauth2Enabled()) {
            requirements.add(new SecurityRequirement().addList("oauth2"));
        }

        if (properties.getSecurity().isBasicAuthEnabled()) {
            requirements.add(new SecurityRequirement().addList("basicAuth"));
        }

        return requirements;
    }

    /**
     * Builder for creating OpenAPI configuration.
     */
    public static class Builder {
        private final OpenApiProperties properties = new OpenApiProperties();

        public Builder title(String title) {
            properties.getInfo().setTitle(title);
            return this;
        }

        public Builder version(String version) {
            properties.getInfo().setVersion(version);
            return this;
        }

        public Builder description(String description) {
            properties.getInfo().setDescription(description);
            return this;
        }

        public Builder contact(String name, String email, String url) {
            OpenApiProperties.ContactInfo contact = new OpenApiProperties.ContactInfo();
            contact.setName(name);
            contact.setEmail(email);
            contact.setUrl(url);
            properties.getInfo().setContact(contact);
            return this;
        }

        public Builder license(String name, String url) {
            OpenApiProperties.LicenseInfo license = new OpenApiProperties.LicenseInfo();
            license.setName(name);
            license.setUrl(url);
            properties.getInfo().setLicense(license);
            return this;
        }

        public Builder server(String url, String description) {
            OpenApiProperties.ServerConfig server = new OpenApiProperties.ServerConfig();
            server.setUrl(url);
            server.setDescription(description);
            properties.getServers().add(server);
            return this;
        }

        public Builder withJwtSecurity() {
            properties.getSecurity().setEnabled(true);
            properties.getSecurity().setJwtEnabled(true);
            return this;
        }

        public Builder withApiKeySecurity(String headerName) {
            properties.getSecurity().setEnabled(true);
            properties.getSecurity().setApiKeyEnabled(true);
            properties.getSecurity().setApiKeyHeader(headerName);
            return this;
        }

        public Builder withOAuth2Security() {
            properties.getSecurity().setEnabled(true);
            properties.getSecurity().setOauth2Enabled(true);
            return this;
        }

        public Builder withBasicAuth() {
            properties.getSecurity().setEnabled(true);
            properties.getSecurity().setBasicAuthEnabled(true);
            return this;
        }

        public OpenAPI build() {
            AdharOpenApiConfig config = new AdharOpenApiConfig(properties);
            return config.createOpenApi();
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

