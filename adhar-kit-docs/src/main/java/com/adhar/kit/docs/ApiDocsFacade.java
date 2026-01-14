package com.adhar.kit.docs;

import com.adhar.kit.docs.api.ApiDocsService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal API Documentation facade.
 *
 * <p>Provides simplified OpenAPI/Swagger documentation across all frameworks.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ApiDocsFacade docs = ApiDocsFacade.getInstance();
 *
 * // Configure API metadata
 * docs.setTitle("Payment Service API");
 * docs.setVersion("2.0.0");
 * docs.setDescription("Handles payment processing and refunds");
 *
 * // Add contact info
 * docs.setContact("API Team", "api@company.com", "https://api.company.com");
 *
 * // Add security
 * docs.addSecurityScheme("bearer", SecuritySchemeType.HTTP, "bearer", "JWT");
 *
 * // Add servers
 * docs.addServer("https://api.prod.company.com", "Production");
 * docs.addServer("https://api.staging.company.com", "Staging");
 *
 * // Add tags for grouping
 * docs.addTag("payments", "Payment operations");
 * docs.addTag("refunds", "Refund operations");
 * }</pre>
 *
 * <p><b>Accessing Documentation:</b></p>
 * <ul>
 *   <li>Spring Boot: http://localhost:8080/swagger-ui.html</li>
 *   <li>Quarkus: http://localhost:8080/q/swagger-ui</li>
 *   <li>Micronaut: http://localhost:8080/swagger/views/swagger-ui</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ApiDocsFacade implements ApiDocsService {

    private static volatile ApiDocsFacade instance;

    private String title = "API Documentation";
    private String version = "1.0.0";
    private String description = "API Documentation";
    private String termsOfService;
    private final Map<String, String> tags = new ConcurrentHashMap<>();
    private boolean enabled = true;

    private ApiDocsFacade() {
        log.info("Initialized ApiDocsFacade");
    }

    public static ApiDocsFacade getInstance() {
        if (instance == null) {
            synchronized (ApiDocsFacade.class) {
                if (instance == null) {
                    instance = new ApiDocsFacade();
                }
            }
        }
        return instance;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
        log.debug("API title set to: {}", title);
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
        log.debug("API version set to: {}", version);
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
        log.debug("API description set");
    }

    @Override
    public void setTermsOfService(String termsOfService) {
        this.termsOfService = termsOfService;
        log.debug("Terms of service set to: {}", termsOfService);
    }

    @Override
    public void setContact(String name, String email, String url) {
        log.info("Contact info set - name: {}, email: {}", name, email);
    }

    @Override
    public void setLicense(String name, String url) {
        log.info("License set - name: {}, url: {}", name, url);
    }

    @Override
    public void addServer(String url, String description) {
        log.info("Added server - url: {}, description: {}", url, description);
    }

    @Override
    public void addSecurityScheme(String name, SecuritySchemeType type,
                                   String scheme, String bearerFormat) {
        log.info("Added security scheme - name: {}, type: {}, scheme: {}",
                 name, type, scheme);
    }

    @Override
    public void addTag(String name, String description) {
        tags.put(name, description);
        log.debug("Added tag: {} - {}", name, description);
    }

    @Override
    public String getOpenApiJson() {
        log.debug("Generating OpenAPI JSON specification");
        // Framework-specific implementation will provide actual spec
        return "{}";
    }

    @Override
    public String getOpenApiYaml() {
        log.debug("Generating OpenAPI YAML specification");
        // Framework-specific implementation will provide actual spec
        return "";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Map<String, String> getTags() {
        return new ConcurrentHashMap<>(tags);
    }
}

