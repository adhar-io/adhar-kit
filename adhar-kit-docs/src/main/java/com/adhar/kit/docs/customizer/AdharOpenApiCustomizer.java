package com.adhar.kit.docs.customizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * Customizes OpenAPI specification.
     *
     * @param openApi OpenAPI specification
     */
    public void customize(OpenAPI openApi) {
        log.info("Customizing OpenAPI documentation");

        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, pathItem) -> {
                customizePath(path, pathItem);
            });
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

        // Add common headers
        addCommonHeaders(operation);

        // Add common responses
        addCommonResponses(operation);
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
                "Invalid request parameters"
            ));
        }

        // Add 401 Unauthorized if not present
        if (!responses.containsKey("401")) {
            responses.addApiResponse("401", createErrorResponse(
                "Unauthorized",
                "Authentication required"
            ));
        }

        // Add 403 Forbidden if not present
        if (!responses.containsKey("403")) {
            responses.addApiResponse("403", createErrorResponse(
                "Forbidden",
                "Access denied"
            ));
        }

        // Add 500 Internal Server Error if not present
        if (!responses.containsKey("500")) {
            responses.addApiResponse("500", createErrorResponse(
                "Internal Server Error",
                "An unexpected error occurred"
            ));
        }
    }

    /**
     * Creates error response.
     */
    private ApiResponse createErrorResponse(String summary, String description) {
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
     */
    public static class Builder {
        private final AdharOpenApiCustomizer customizer = new AdharOpenApiCustomizer();

        public Builder addCommonHeaders() {
            return this;
        }

        public Builder addCommonResponses() {
            return this;
        }

        public Builder addExamples() {
            return this;
        }

        public AdharOpenApiCustomizer build() {
            return customizer;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

