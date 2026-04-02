package com.adhar.kit.docs;

import com.adhar.kit.docs.api.ApiDocsService;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal API Documentation facade.
 *
 * <p>Provides simplified OpenAPI/Swagger documentation across all frameworks.
 * Integrates with SpringDoc to read live OpenAPI specs, manage global headers,
 * security schemes, and provide endpoint discovery.</p>
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
 * // Add global headers
 * docs.addGlobalHeader("X-Tenant-ID", "Tenant identifier for multi-tenancy");
 *
 * // Add security schemes
 * docs.addSecurityScheme("bearer", "http", "bearer");
 *
 * // Query endpoints
 * List<Map<String, String>> endpoints = docs.getEndpoints();
 * String spec = docs.getOpenApiSpec();
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
    private String contactName;
    private String contactEmail;
    private String contactUrl;
    private String licenseName;
    private String licenseUrl;
    private final Map<String, String> tags = new ConcurrentHashMap<>();
    private boolean enabled = true;

    private String baseUrl = "http://localhost:8080";
    private String apiDocsPath = "/v3/api-docs";
    private String apiDocsYamlPath = "/v3/api-docs.yaml";
    private String swaggerUiPath = "/swagger-ui/index.html";

    private final List<GlobalHeader> globalHeaders = new CopyOnWriteArrayList<>();
    private final List<SecuritySchemeEntry> securitySchemes = new CopyOnWriteArrayList<>();
    private final List<ServerEntry> servers = new CopyOnWriteArrayList<>();

    // Cached references for SpringDoc integration (set via Spring wiring)
    private volatile Object openApiCustomizerBean;
    private volatile Object operationCustomizerBean;
    private volatile Object openApiResource;

    private final HttpClient httpClient;

    private ApiDocsFacade() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        log.info("Initialized ApiDocsFacade");
    }

    /**
     * Returns the singleton instance of ApiDocsFacade.
     *
     * @return the singleton ApiDocsFacade instance
     */
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

    // ---- Configuration Methods (builder-style, return this) ----

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
        this.contactName = name;
        this.contactEmail = email;
        this.contactUrl = url;
        log.info("Contact info set - name: {}, email: {}", name, email);
    }

    @Override
    public void setLicense(String name, String url) {
        this.licenseName = name;
        this.licenseUrl = url;
        log.info("License set - name: {}, url: {}", name, url);
    }

    @Override
    public void addServer(String url, String description) {
        servers.add(new ServerEntry(url, description));
        log.info("Added server - url: {}, description: {}", url, description);
    }

    @Override
    public void addSecurityScheme(String name, SecuritySchemeType type,
                                   String scheme, String bearerFormat) {
        securitySchemes.add(new SecuritySchemeEntry(name, type.name(), scheme, bearerFormat));
        log.info("Added security scheme - name: {}, type: {}, scheme: {}",
                 name, type, scheme);
    }

    @Override
    public void addTag(String name, String description) {
        tags.put(name, description);
        log.debug("Added tag: {} - {}", name, description);
    }

    // ---- Builder-style Configuration ----

    /**
     * Sets the base URL for the running application.
     * Used for fetching specs and checking Swagger UI availability.
     *
     * @param baseUrl the base URL (e.g., "http://localhost:8080")
     * @return this facade for chaining
     */
    public ApiDocsFacade withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Sets the OpenAPI docs JSON path.
     *
     * @param path the JSON docs path (default: /v3/api-docs)
     * @return this facade for chaining
     */
    public ApiDocsFacade withApiDocsPath(String path) {
        this.apiDocsPath = path;
        return this;
    }

    /**
     * Sets the OpenAPI docs YAML path.
     *
     * @param path the YAML docs path (default: /v3/api-docs.yaml)
     * @return this facade for chaining
     */
    public ApiDocsFacade withApiDocsYamlPath(String path) {
        this.apiDocsYamlPath = path;
        return this;
    }

    /**
     * Sets the Swagger UI path.
     *
     * @param path the Swagger UI path (default: /swagger-ui/index.html)
     * @return this facade for chaining
     */
    public ApiDocsFacade withSwaggerUiPath(String path) {
        this.swaggerUiPath = path;
        return this;
    }

    /**
     * Builder-style title configuration.
     *
     * @param title the API title
     * @return this facade for chaining
     */
    public ApiDocsFacade withTitle(String title) {
        setTitle(title);
        return this;
    }

    /**
     * Builder-style version configuration.
     *
     * @param version the API version
     * @return this facade for chaining
     */
    public ApiDocsFacade withVersion(String version) {
        setVersion(version);
        return this;
    }

    /**
     * Builder-style description configuration.
     *
     * @param description the API description
     * @return this facade for chaining
     */
    public ApiDocsFacade withDescription(String description) {
        setDescription(description);
        return this;
    }

    // ---- New Functional Methods ----

    /**
     * Returns the OpenAPI 3.0 specification as a JSON string by reading
     * from SpringDoc's endpoint.
     *
     * <p>First attempts to use the injected OpenAPI resource bean directly.
     * Falls back to an HTTP request to the configured api-docs endpoint.</p>
     *
     * @return OpenAPI JSON specification, or a fallback minimal spec if unavailable
     */
    public String getOpenApiSpec() {
        log.debug("Fetching OpenAPI JSON specification");

        // Attempt direct bean access via SpringDoc's OpenApiResource
        String spec = fetchSpecFromBean();
        if (spec != null) {
            return spec;
        }

        // Fall back to HTTP fetch
        spec = fetchFromEndpoint(baseUrl + apiDocsPath, "application/json");
        if (spec != null) {
            return spec;
        }

        // Return a minimal fallback spec built from local state
        log.warn("Could not fetch live OpenAPI spec; returning locally-built fallback");
        return buildFallbackJsonSpec();
    }

    @Override
    public String getOpenApiJson() {
        return getOpenApiSpec();
    }

    /**
     * Returns the OpenAPI 3.0 specification as a YAML string by reading
     * from SpringDoc's YAML endpoint.
     *
     * @return OpenAPI YAML specification, or a fallback minimal spec if unavailable
     */
    public String getOpenApiSpecYaml() {
        log.debug("Fetching OpenAPI YAML specification");

        String spec = fetchFromEndpoint(baseUrl + apiDocsYamlPath, "application/x-yaml");
        if (spec != null) {
            return spec;
        }

        log.warn("Could not fetch live OpenAPI YAML spec; returning locally-built fallback");
        return buildFallbackYamlSpec();
    }

    @Override
    public String getOpenApiYaml() {
        return getOpenApiSpecYaml();
    }

    /**
     * Returns API metadata: title, version, description, and contact information.
     *
     * @return a map containing api info keys (title, version, description,
     *         contactName, contactEmail, contactUrl, licenseName, licenseUrl, termsOfService)
     */
    public Map<String, String> getApiInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("title", title);
        info.put("version", version);
        info.put("description", description);
        if (contactName != null) {
            info.put("contactName", contactName);
        }
        if (contactEmail != null) {
            info.put("contactEmail", contactEmail);
        }
        if (contactUrl != null) {
            info.put("contactUrl", contactUrl);
        }
        if (licenseName != null) {
            info.put("licenseName", licenseName);
        }
        if (licenseUrl != null) {
            info.put("licenseUrl", licenseUrl);
        }
        if (termsOfService != null) {
            info.put("termsOfService", termsOfService);
        }
        return Collections.unmodifiableMap(info);
    }

    /**
     * Returns a list of all registered REST endpoints with method, path, and summary.
     *
     * <p>Parses the live OpenAPI JSON spec to extract path information. Each entry
     * in the returned list is a map with keys: "method", "path", "summary".</p>
     *
     * @return list of endpoint descriptors, empty list if spec is unavailable
     */
    public List<Map<String, String>> getEndpoints() {
        log.debug("Retrieving registered REST endpoints");
        List<Map<String, String>> endpoints = new ArrayList<>();

        String spec = getOpenApiSpec();
        if (spec == null || spec.isEmpty() || spec.equals("{}")) {
            return endpoints;
        }

        try {
            endpoints = parseEndpointsFromJson(spec);
        } catch (Exception e) {
            log.warn("Failed to parse endpoints from OpenAPI spec: {}", e.getMessage());
        }

        return endpoints;
    }

    /**
     * Checks if Swagger UI is accessible by issuing an HTTP HEAD request.
     *
     * @return true if Swagger UI responds with a 200 status code
     */
    public boolean isSwaggerUiEnabled() {
        String url = getSwaggerUiUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            boolean accessible = response.statusCode() == 200;
            log.debug("Swagger UI at {} is accessible: {}", url, accessible);
            return accessible;
        } catch (Exception e) {
            log.debug("Swagger UI at {} is not accessible: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the full Swagger UI URL.
     *
     * @return the Swagger UI URL
     */
    public String getSwaggerUiUrl() {
        return baseUrl + swaggerUiPath;
    }

    /**
     * Registers a global header parameter that will be added to all operations
     * via SpringDoc's OperationCustomizer.
     *
     * @param name        the header name (e.g., "X-Tenant-ID")
     * @param description a description of the header's purpose
     * @return this facade for chaining
     */
    public ApiDocsFacade addGlobalHeader(String name, String description) {
        globalHeaders.add(new GlobalHeader(name, description));
        log.info("Registered global header: {}", name);
        return this;
    }

    /**
     * Registers a security scheme that will be applied to the OpenAPI spec
     * via SpringDoc's OpenApiCustomizer.
     *
     * <p>Supported types: "http", "apiKey", "oauth2", "openIdConnect".</p>
     *
     * @param name   the security scheme name (e.g., "bearerAuth")
     * @param type   the scheme type (e.g., "http")
     * @param scheme the authentication scheme (e.g., "bearer", "basic")
     * @return this facade for chaining
     */
    public ApiDocsFacade addSecurityScheme(String name, String type, String scheme) {
        securitySchemes.add(new SecuritySchemeEntry(name, type, scheme, null));
        log.info("Registered security scheme: {} (type={}, scheme={})", name, type, scheme);
        return this;
    }

    // ---- SpringDoc Integration Hooks ----

    /**
     * Creates an OpenApiCustomizer that applies the facade's global headers
     * and security schemes to the OpenAPI spec.
     *
     * <p>Register this as a Spring bean so SpringDoc picks it up:</p>
     * <pre>{@code
     * @Bean
     * public OpenApiCustomizer facadeOpenApiCustomizer() {
     *     return ApiDocsFacade.getInstance().createOpenApiCustomizer();
     * }
     * }</pre>
     *
     * @return an OpenApiCustomizer functional interface implementation, or null
     *         if SpringDoc is not on the classpath
     */
    public Object createOpenApiCustomizer() {
        if (!isSpringDocAvailable()) {
            log.warn("SpringDoc is not on the classpath; OpenApiCustomizer will not be created");
            return null;
        }
        return new SpringDocOpenApiCustomizerAdapter(this);
    }

    /**
     * Creates an OperationCustomizer that adds global headers to every operation.
     *
     * <p>Register this as a Spring bean so SpringDoc picks it up:</p>
     * <pre>{@code
     * @Bean
     * public OperationCustomizer facadeOperationCustomizer() {
     *     return ApiDocsFacade.getInstance().createOperationCustomizer();
     * }
     * }</pre>
     *
     * @return an OperationCustomizer functional interface implementation, or null
     *         if SpringDoc is not on the classpath
     */
    public Object createOperationCustomizer() {
        if (!isSpringDocAvailable()) {
            log.warn("SpringDoc is not on the classpath; OperationCustomizer will not be created");
            return null;
        }
        return new SpringDocOperationCustomizerAdapter(this);
    }

    /**
     * Allows injecting a reference to the SpringDoc OpenApiResource bean for
     * direct spec retrieval without HTTP calls.
     *
     * @param openApiResource the OpenApiResource bean
     */
    public void setOpenApiResource(Object openApiResource) {
        this.openApiResource = openApiResource;
    }

    // ---- Accessor Methods ----

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables API documentation.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Map<String, String> getTags() {
        return new ConcurrentHashMap<>(tags);
    }

    /**
     * Returns the list of registered global headers.
     *
     * @return unmodifiable list of global headers
     */
    public List<GlobalHeader> getGlobalHeaders() {
        return Collections.unmodifiableList(new ArrayList<>(globalHeaders));
    }

    /**
     * Returns the list of registered security schemes.
     *
     * @return unmodifiable list of security schemes
     */
    public List<SecuritySchemeEntry> getSecuritySchemes() {
        return Collections.unmodifiableList(new ArrayList<>(securitySchemes));
    }

    /**
     * Returns the list of registered servers.
     *
     * @return unmodifiable list of servers
     */
    public List<ServerEntry> getServers() {
        return Collections.unmodifiableList(new ArrayList<>(servers));
    }

    public String getTitle() {
        return title;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    // ---- Internal Helpers ----

    /**
     * Checks whether SpringDoc classes are available on the classpath.
     */
    private boolean isSpringDocAvailable() {
        try {
            Class.forName("org.springdoc.core.customizers.OpenApiCustomizer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Attempts to fetch the OpenAPI spec directly from an injected SpringDoc bean
     * by reflectively invoking its openapiJson method, avoiding a network round-trip.
     */
    private String fetchSpecFromBean() {
        if (openApiResource == null) {
            return null;
        }
        try {
            // SpringDoc's OpenApiWebMvcResource exposes openapiJson(HttpServletRequest, String, Locale).
            // Since we cannot depend on the servlet API at compile time inside this facade,
            // we use reflection to call the method with null arguments where possible.
            var methods = openApiResource.getClass().getMethods();
            for (var method : methods) {
                if ("openapiJson".equals(method.getName()) && method.getParameterCount() >= 1) {
                    Object result = method.invoke(openApiResource,
                            new Object[method.getParameterCount()]);
                    if (result != null) {
                        // The return type is typically a ResponseEntity or byte[]/String
                        String body = result.toString();
                        if (body.contains("openapi")) {
                            return body;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Direct bean spec retrieval not available: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Fetches a spec from an HTTP endpoint.
     */
    private String fetchFromEndpoint(String url, String acceptHeader) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", acceptHeader)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.debug("Endpoint {} returned status {}", url, response.statusCode());
        } catch (Exception e) {
            log.debug("Failed to fetch spec from {}: {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * Builds a minimal fallback JSON spec from locally configured metadata.
     */
    private String buildFallbackJsonSpec() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"openapi\": \"3.0.1\",\n");
        sb.append("  \"info\": {\n");
        sb.append("    \"title\": \"").append(escapeJson(title)).append("\",\n");
        sb.append("    \"version\": \"").append(escapeJson(version)).append("\",\n");
        sb.append("    \"description\": \"").append(escapeJson(description)).append("\"");
        if (contactName != null || contactEmail != null) {
            sb.append(",\n    \"contact\": {");
            boolean hasField = false;
            if (contactName != null) {
                sb.append("\"name\": \"").append(escapeJson(contactName)).append("\"");
                hasField = true;
            }
            if (contactEmail != null) {
                if (hasField) sb.append(", ");
                sb.append("\"email\": \"").append(escapeJson(contactEmail)).append("\"");
                hasField = true;
            }
            if (contactUrl != null) {
                if (hasField) sb.append(", ");
                sb.append("\"url\": \"").append(escapeJson(contactUrl)).append("\"");
            }
            sb.append("}");
        }
        sb.append("\n  },\n");
        sb.append("  \"paths\": {},\n");
        sb.append("  \"components\": {\n");
        sb.append("    \"securitySchemes\": {");
        boolean first = true;
        for (SecuritySchemeEntry entry : securitySchemes) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\n      \"").append(escapeJson(entry.name())).append("\": {");
            sb.append("\"type\": \"").append(escapeJson(entry.type())).append("\"");
            if (entry.scheme() != null) {
                sb.append(", \"scheme\": \"").append(escapeJson(entry.scheme())).append("\"");
            }
            if (entry.bearerFormat() != null) {
                sb.append(", \"bearerFormat\": \"").append(escapeJson(entry.bearerFormat())).append("\"");
            }
            sb.append("}");
        }
        sb.append("\n    }\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a minimal fallback YAML spec from locally configured metadata.
     */
    private String buildFallbackYamlSpec() {
        StringBuilder sb = new StringBuilder();
        sb.append("openapi: '3.0.1'\n");
        sb.append("info:\n");
        sb.append("  title: '").append(escapeYaml(title)).append("'\n");
        sb.append("  version: '").append(escapeYaml(version)).append("'\n");
        sb.append("  description: '").append(escapeYaml(description)).append("'\n");
        if (contactName != null || contactEmail != null) {
            sb.append("  contact:\n");
            if (contactName != null) {
                sb.append("    name: '").append(escapeYaml(contactName)).append("'\n");
            }
            if (contactEmail != null) {
                sb.append("    email: '").append(escapeYaml(contactEmail)).append("'\n");
            }
            if (contactUrl != null) {
                sb.append("    url: '").append(escapeYaml(contactUrl)).append("'\n");
            }
        }
        sb.append("paths: {}\n");
        if (!securitySchemes.isEmpty()) {
            sb.append("components:\n");
            sb.append("  securitySchemes:\n");
            for (SecuritySchemeEntry entry : securitySchemes) {
                sb.append("    ").append(entry.name()).append(":\n");
                sb.append("      type: '").append(escapeYaml(entry.type())).append("'\n");
                if (entry.scheme() != null) {
                    sb.append("      scheme: '").append(escapeYaml(entry.scheme())).append("'\n");
                }
                if (entry.bearerFormat() != null) {
                    sb.append("      bearerFormat: '").append(escapeYaml(entry.bearerFormat())).append("'\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses endpoint information from an OpenAPI JSON spec string.
     * Uses lightweight string parsing to avoid a hard dependency on Jackson at facade level.
     */
    private List<Map<String, String>> parseEndpointsFromJson(String json) {
        List<Map<String, String>> endpoints = new ArrayList<>();

        // Find the "paths" object
        int pathsIndex = json.indexOf("\"paths\"");
        if (pathsIndex == -1) {
            return endpoints;
        }

        // Find the opening brace of the paths object
        int braceStart = json.indexOf('{', pathsIndex + 7);
        if (braceStart == -1) {
            return endpoints;
        }

        // Extract the paths block by brace matching
        String pathsBlock = extractJsonBlock(json, braceStart);
        if (pathsBlock == null || pathsBlock.equals("{}")) {
            return endpoints;
        }

        // Parse each path key
        int searchFrom = 0;
        while (true) {
            int pathKeyStart = pathsBlock.indexOf('"', searchFrom);
            if (pathKeyStart == -1) break;
            int pathKeyEnd = pathsBlock.indexOf('"', pathKeyStart + 1);
            if (pathKeyEnd == -1) break;

            String path = pathsBlock.substring(pathKeyStart + 1, pathKeyEnd);

            // Skip if this looks like a nested key inside a method block
            if (!path.startsWith("/")) {
                searchFrom = pathKeyEnd + 1;
                continue;
            }

            // Find the path's object block
            int pathObjStart = pathsBlock.indexOf('{', pathKeyEnd);
            if (pathObjStart == -1) break;
            String pathObj = extractJsonBlock(pathsBlock, pathObjStart);
            if (pathObj == null) break;

            // Extract methods from this path object
            String[] methods = {"get", "post", "put", "delete", "patch", "head", "options"};
            for (String method : methods) {
                String methodKey = "\"" + method + "\"";
                int methodIndex = pathObj.indexOf(methodKey);
                if (methodIndex != -1) {
                    Map<String, String> endpoint = new HashMap<>();
                    endpoint.put("method", method.toUpperCase());
                    endpoint.put("path", path);

                    // Try to extract summary
                    int methodObjStart = pathObj.indexOf('{', methodIndex);
                    if (methodObjStart != -1) {
                        String methodBlock = extractJsonBlock(pathObj, methodObjStart);
                        if (methodBlock != null) {
                            String summary = extractJsonStringValue(methodBlock, "summary");
                            endpoint.put("summary", summary != null ? summary : "");
                        }
                    }

                    endpoints.add(endpoint);
                }
            }

            searchFrom = pathObjStart + (pathObj != null ? pathObj.length() : 1);
        }

        return endpoints;
    }

    /**
     * Extracts a JSON block starting at the given brace position, matching braces.
     */
    private String extractJsonBlock(String json, int braceStart) {
        if (braceStart >= json.length() || json.charAt(braceStart) != '{') {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(braceStart, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Extracts a simple string value for a given key from a JSON object string.
     */
    private String extractJsonStringValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;

        // Find the opening quote of the value
        int valueStart = json.indexOf('"', colonIndex + 1);
        if (valueStart == -1) return null;

        // Find the closing quote (handle escapes)
        int valueEnd = valueStart + 1;
        boolean esc = false;
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (esc) {
                esc = false;
                valueEnd++;
                continue;
            }
            if (c == '\\') {
                esc = true;
                valueEnd++;
                continue;
            }
            if (c == '"') {
                return json.substring(valueStart + 1, valueEnd);
            }
            valueEnd++;
        }
        return null;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    private String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    // ---- Inner Record Types ----

    /**
     * Represents a global header parameter to be added to all operations.
     *
     * @param name        the header name
     * @param description the header description
     */
    public record GlobalHeader(String name, String description) {}

    /**
     * Represents a registered security scheme.
     *
     * @param name         the scheme name
     * @param type         the scheme type (http, apiKey, oauth2, openIdConnect)
     * @param scheme       the authentication scheme (bearer, basic, etc.)
     * @param bearerFormat the bearer format (JWT, etc.), nullable
     */
    public record SecuritySchemeEntry(String name, String type, String scheme, String bearerFormat) {}

    /**
     * Represents a server entry.
     *
     * @param url         the server URL
     * @param description the server description
     */
    public record ServerEntry(String url, String description) {}

    // ---- SpringDoc Adapter Classes ----

    /**
     * Adapter that implements SpringDoc's OpenApiCustomizer to apply the facade's
     * security schemes and metadata to the generated OpenAPI spec.
     *
     * <p>This class uses SpringDoc types directly. If SpringDoc is not on the
     * classpath, this class should not be instantiated (checked by the caller).</p>
     */
    static class SpringDocOpenApiCustomizerAdapter
            implements org.springdoc.core.customizers.OpenApiCustomizer {

        private static final org.slf4j.Logger LOGGER =
                org.slf4j.LoggerFactory.getLogger(SpringDocOpenApiCustomizerAdapter.class);

        private final ApiDocsFacade facade;

        SpringDocOpenApiCustomizerAdapter(ApiDocsFacade facade) {
            this.facade = facade;
        }

        @Override
        public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
            LOGGER.debug("Applying ApiDocsFacade customizations to OpenAPI spec");

            // Apply info overrides
            if (openApi.getInfo() == null) {
                openApi.setInfo(new io.swagger.v3.oas.models.info.Info());
            }
            openApi.getInfo()
                    .title(facade.title)
                    .version(facade.version)
                    .description(facade.description);

            if (facade.contactName != null || facade.contactEmail != null) {
                io.swagger.v3.oas.models.info.Contact contact =
                        new io.swagger.v3.oas.models.info.Contact();
                contact.setName(facade.contactName);
                contact.setEmail(facade.contactEmail);
                contact.setUrl(facade.contactUrl);
                openApi.getInfo().setContact(contact);
            }

            if (facade.licenseName != null) {
                io.swagger.v3.oas.models.info.License license =
                        new io.swagger.v3.oas.models.info.License();
                license.setName(facade.licenseName);
                license.setUrl(facade.licenseUrl);
                openApi.getInfo().setLicense(license);
            }

            if (facade.termsOfService != null) {
                openApi.getInfo().setTermsOfService(facade.termsOfService);
            }

            // Apply security schemes
            if (!facade.securitySchemes.isEmpty()) {
                if (openApi.getComponents() == null) {
                    openApi.setComponents(new io.swagger.v3.oas.models.Components());
                }
                for (SecuritySchemeEntry entry : facade.securitySchemes) {
                    io.swagger.v3.oas.models.security.SecurityScheme secScheme =
                            new io.swagger.v3.oas.models.security.SecurityScheme();
                    secScheme.setType(mapSecuritySchemeType(entry.type()));
                    if (entry.scheme() != null) {
                        secScheme.setScheme(entry.scheme());
                    }
                    if (entry.bearerFormat() != null) {
                        secScheme.setBearerFormat(entry.bearerFormat());
                    }
                    openApi.getComponents().addSecuritySchemes(entry.name(), secScheme);
                }
            }

            // Apply servers
            if (!facade.servers.isEmpty()) {
                List<io.swagger.v3.oas.models.servers.Server> serverList = new ArrayList<>();
                for (ServerEntry se : facade.servers) {
                    io.swagger.v3.oas.models.servers.Server server =
                            new io.swagger.v3.oas.models.servers.Server();
                    server.setUrl(se.url());
                    server.setDescription(se.description());
                    serverList.add(server);
                }
                openApi.setServers(serverList);
            }

            // Apply tags
            if (!facade.tags.isEmpty()) {
                List<io.swagger.v3.oas.models.tags.Tag> tagList = new ArrayList<>();
                facade.tags.forEach((name, desc) -> {
                    io.swagger.v3.oas.models.tags.Tag tag =
                            new io.swagger.v3.oas.models.tags.Tag();
                    tag.setName(name);
                    tag.setDescription(desc);
                    tagList.add(tag);
                });
                openApi.setTags(tagList);
            }
        }

        private io.swagger.v3.oas.models.security.SecurityScheme.Type mapSecuritySchemeType(String type) {
            if (type == null) {
                return io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP;
            }
            return switch (type.toUpperCase()) {
                case "HTTP" -> io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP;
                case "APIKEY", "API_KEY" ->
                        io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY;
                case "OAUTH2" -> io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2;
                case "OPENIDCONNECT", "OPENID_CONNECT" ->
                        io.swagger.v3.oas.models.security.SecurityScheme.Type.OPENIDCONNECT;
                default -> io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP;
            };
        }
    }

    /**
     * Adapter that implements SpringDoc's OperationCustomizer to add global header
     * parameters to every operation in the generated OpenAPI spec.
     */
    static class SpringDocOperationCustomizerAdapter
            implements org.springdoc.core.customizers.OperationCustomizer {

        private final ApiDocsFacade facade;

        SpringDocOperationCustomizerAdapter(ApiDocsFacade facade) {
            this.facade = facade;
        }

        @Override
        public io.swagger.v3.oas.models.Operation customize(
                io.swagger.v3.oas.models.Operation operation,
                org.springframework.web.method.HandlerMethod handlerMethod) {

            for (GlobalHeader header : facade.globalHeaders) {
                io.swagger.v3.oas.models.parameters.Parameter param =
                        new io.swagger.v3.oas.models.parameters.Parameter();
                param.setName(header.name());
                param.setIn("header");
                param.setRequired(false);
                param.setDescription(header.description());
                param.setSchema(new io.swagger.v3.oas.models.media.Schema<String>()
                        .type("string"));
                operation.addParametersItem(param);
            }

            return operation;
        }
    }

    /**
     * Resets the singleton instance. Intended for testing only.
     */
    static void resetInstance() {
        synchronized (ApiDocsFacade.class) {
            instance = null;
        }
    }
}
