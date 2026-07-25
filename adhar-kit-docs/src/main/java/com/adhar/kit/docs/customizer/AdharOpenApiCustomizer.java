package com.adhar.kit.docs.customizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customizer for OpenAPI documentation.
 *
 * <p>Provides common customizations for enterprise microservices:</p>
 * <ul>
 *   <li>Add common headers (Correlation-ID, Request-ID)</li>
 *   <li>Add common response schemas</li>
 *   <li>Add common error responses</li>
 *   <li>Add examples and descriptions</li>
 * </ul>
 *
 * <p>Instances created directly via {@code new AdharOpenApiCustomizer()} apply the
 * common headers and common error responses by default (but not examples), matching
 * the historical default behaviour of this class. Instances created via {@link #builder()}
 * only apply the features explicitly requested on the builder:</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Bean
 * public OpenApiCustomizer commonHeadersCustomizer() {
 *     return AdharOpenApiCustomizer.builder()
 *         .addCommonHeaders()
 *         .addCommonResponses()
 *         .addExamples()
 *         .build();
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharOpenApiCustomizer {

    private final boolean includeHeaders;
    private final boolean includeResponses;
    private final boolean includeExamples;

    /**
     * Creates a customizer that applies common headers and common error responses,
     * preserving this class's historical default behaviour.
     */
    public AdharOpenApiCustomizer() {
        this(true, true, false);
    }

    AdharOpenApiCustomizer(boolean includeHeaders, boolean includeResponses, boolean includeExamples) {
        this.includeHeaders = includeHeaders;
        this.includeResponses = includeResponses;
        this.includeExamples = includeExamples;
    }

    /**
     * Customizes OpenAPI specification.
     *
     * @param openApi OpenAPI specification
     */
    public void customize(OpenAPI openApi) {
        log.info("Customizing OpenAPI documentation");

        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach(this::customizePath);
        }
    }

    /**
     * Customizes a path item.
     */
    private void customizePath(String path, PathItem pathItem) {
        // Customize each operation
        customizeOperation(pathItem.getGet());
        customizeOperation(pathItem.getPost());
        customizeOperation(pathItem.getPut());
        customizeOperation(pathItem.getDelete());
        customizeOperation(pathItem.getPatch());
    }

    /**
     * Customizes an operation.
     */
    private void customizeOperation(Operation operation) {
        if (operation == null) {
            return;
        }

        if (includeHeaders) {
            addCommonHeaders(operation);
        }

        if (includeResponses) {
            addCommonResponses(operation);
        }

        if (includeExamples) {
            addExamples(operation);
        }
    }

    /**
     * Adds common headers to operation.
     */
    private void addCommonHeaders(Operation operation) {
        // Correlation ID header
        Parameter correlationId = new Parameter()
            .name("X-Correlation-ID")
            .in("header")
            .required(false)
            .description("Correlation ID for request tracing")
            .schema(new Schema<String>().type("string").format("uuid"));

        // Request ID header
        Parameter requestId = new Parameter()
            .name("X-Request-ID")
            .in("header")
            .required(false)
            .description("Unique request identifier")
            .schema(new Schema<String>().type("string").format("uuid"));

        operation.addParametersItem(correlationId);
        operation.addParametersItem(requestId);
    }

    /**
     * Adds common responses to operation.
     */
    private void addCommonResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();

        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        // Add 400 Bad Request if not present
        if (!responses.containsKey("400")) {
            responses.addApiResponse("400", createErrorResponse(
                "Bad Request",
                "Invalid request parameters",
                400
            ));
        }

        // Add 401 Unauthorized if not present
        if (!responses.containsKey("401")) {
            responses.addApiResponse("401", createErrorResponse(
                "Unauthorized",
                "Authentication required",
                401
            ));
        }

        // Add 403 Forbidden if not present
        if (!responses.containsKey("403")) {
            responses.addApiResponse("403", createErrorResponse(
                "Forbidden",
                "Access denied",
                403
            ));
        }

        // Add 404 Not Found if not present
        if (!responses.containsKey("404")) {
            responses.addApiResponse("404", createErrorResponse(
                "Not Found",
                "Resource not found",
                404
            ));
        }

        // Add 500 Internal Server Error if not present
        if (!responses.containsKey("500")) {
            responses.addApiResponse("500", createErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred",
                500
            ));
        }
    }

    /**
     * Attaches example request/response payloads to an operation's request body and
     * response content, based on the schema already associated with each media type.
     *
     * <p>Existing examples are never overwritten.</p>
     */
    private void addExamples(Operation operation) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            requestBody.getContent().forEach((mediaTypeKey, mediaType) -> addExampleToMediaType(mediaType));
        }

        ApiResponses responses = operation.getResponses();
        if (responses != null) {
            responses.values().forEach(response -> {
                if (response.getContent() != null) {
                    response.getContent().forEach((mediaTypeKey, mediaType) -> addExampleToMediaType(mediaType));
                }
            });
        }
    }

    private void addExampleToMediaType(MediaType mediaType) {
        if (mediaType.getExample() != null || mediaType.getExamples() != null) {
            return;
        }
        Object example = buildExampleForSchema(mediaType.getSchema());
        if (example != null) {
            mediaType.setExample(example);
        }
    }

    /**
     * Builds a representative example value for the given schema. Object schemas with
     * declared properties produce a map keyed by property name; scalar schemas produce a
     * single representative value.
     */
    @SuppressWarnings("unchecked")
    private Object buildExampleForSchema(Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> example = new LinkedHashMap<>();
            properties.forEach((name, propertySchema) -> example.put(name, buildScalarExample(propertySchema)));
            return example;
        }
        return buildScalarExample(schema);
    }

    private Object buildScalarExample(Schema<?> schema) {
        if (schema == null) {
            return "value";
        }
        String type = schema.getType();
        String format = schema.getFormat();
        if (type == null) {
            return "value";
        }
        return switch (type) {
            case "integer" -> 0;
            case "number" -> 0.0;
            case "boolean" -> true;
            case "array" -> List.of();
            case "string" -> switch (format == null ? "" : format) {
                case "date-time" -> "2024-01-01T00:00:00Z";
                case "date" -> "2024-01-01";
                case "uuid" -> "550e8400-e29b-41d4-a716-446655440000";
                default -> "string";
            };
            default -> "value";
        };
    }

    /**
     * Creates error response.
     */
    private ApiResponse createErrorResponse(String summary, String description, int status) {
        ApiResponse response = new ApiResponse()
            .description(description);

        // Add error response schema
        Content content = new Content();
        MediaType mediaType = new MediaType();

        Schema<?> schema = new Schema<>()
            .type("object")
            .addProperty("timestamp", new Schema<>().type("string").format("date-time"))
            .addProperty("status", new Schema<>().type("integer"))
            .addProperty("error", new Schema<>().type("string"))
            .addProperty("message", new Schema<>().type("string"))
            .addProperty("path", new Schema<>().type("string"))
            .addProperty("correlationId", new Schema<>().type("string"));

        mediaType.schema(schema);

        if (includeExamples) {
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("timestamp", "2024-01-01T00:00:00Z");
            example.put("status", status);
            example.put("error", summary);
            example.put("message", description);
            example.put("path", "/api/resource");
            example.put("correlationId", "550e8400-e29b-41d4-a716-446655440000");
            mediaType.setExample(example);
        }

        content.addMediaType("application/json", mediaType);
        response.content(content);

        // Add response headers
        response.addHeaderObject("X-Correlation-ID", new Header()
            .description("Correlation ID for tracing")
            .schema(new Schema<>().type("string")));

        response.addHeaderObject("X-Request-ID", new Header()
            .description("Request ID")
            .schema(new Schema<>().type("string")));

        return response;
    }

    /**
     * Builder for OpenAPI customizer.
     *
     * <p>Unlike a plain {@code new AdharOpenApiCustomizer()}, a builder-produced
     * customizer only applies the features explicitly opted into via
     * {@link #addCommonHeaders()}, {@link #addCommonResponses()}, and
     * {@link #addExamples()}.</p>
     */
    public static class Builder {
        private boolean includeHeaders;
        private boolean includeResponses;
        private boolean includeExamples;

        /**
         * Enables adding the {@code X-Correlation-ID} and {@code X-Request-ID} request
         * headers to every operation.
         *
         * @return this builder
         */
        public Builder addCommonHeaders() {
            this.includeHeaders = true;
            return this;
        }

        /**
         * Enables adding standard 400/401/403/404/500 error responses (with the shared
         * error schema) to every operation that doesn't already declare them.
         *
         * @return this builder
         */
        public Builder addCommonResponses() {
            this.includeResponses = true;
            return this;
        }

        /**
         * Enables attaching representative example payloads to request bodies and
         * response content that don't already declare an example.
         *
         * @return this builder
         */
        public Builder addExamples() {
            this.includeExamples = true;
            return this;
        }

        public AdharOpenApiCustomizer build() {
            return new AdharOpenApiCustomizer(includeHeaders, includeResponses, includeExamples);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
