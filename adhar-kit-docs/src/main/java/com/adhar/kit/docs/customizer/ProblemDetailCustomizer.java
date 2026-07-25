package com.adhar.kit.docs.customizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Customizer that documents error responses using the RFC 9457
 * ("Problem Details for HTTP APIs") {@code application/problem+json} media type,
 * as an alternative/complement to the {@link com.adhar.kit.docs.model.ApiErrorResponse}
 * based error schema produced by {@link AdharOpenApiCustomizer}.
 *
 * <p>RFC 9457 defines five canonical problem members: {@code type}, {@code title},
 * {@code status}, {@code detail}, and {@code instance}. This customizer registers a
 * reusable {@code ProblemDetail} schema under {@code components/schemas} and attaches
 * it, via {@code application/problem+json}, to a configurable set of error status
 * codes on every operation.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Bean
 * public OpenApiCustomizer problemDetailCustomizer() {
 *     ProblemDetailCustomizer customizer = new ProblemDetailCustomizer();
 *     return customizer::customize;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 */
@Slf4j
public class ProblemDetailCustomizer {

    /** Media type used for RFC 9457 problem responses. */
    public static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";

    /** Name under which the problem detail schema is registered in components/schemas. */
    public static final String PROBLEM_DETAIL_SCHEMA_NAME = "ProblemDetail";

    private static final Set<String> DEFAULT_STATUS_CODES =
            Set.of("400", "401", "403", "404", "409", "422", "500");

    private final Set<String> statusCodes;

    /**
     * Creates a customizer covering the default set of error status codes
     * (400, 401, 403, 404, 409, 422, 500).
     */
    public ProblemDetailCustomizer() {
        this(DEFAULT_STATUS_CODES);
    }

    /**
     * Creates a customizer covering the given set of HTTP status codes.
     *
     * @param statusCodes the status codes (as strings, e.g. "404") to document
     */
    public ProblemDetailCustomizer(Set<String> statusCodes) {
        this.statusCodes = new LinkedHashSet<>(statusCodes);
    }

    /**
     * Applies the RFC 9457 problem-detail responses to every operation in the spec,
     * registering the shared schema in components/schemas.
     *
     * @param openApi the OpenAPI specification to mutate
     */
    public void customize(OpenAPI openApi) {
        log.info("Applying RFC 9457 problem+json customizations to OpenAPI spec");
        registerSchema(openApi);

        if (openApi.getPaths() != null) {
            openApi.getPaths().forEach((path, pathItem) -> customizePath(pathItem));
        }
    }

    private void customizePath(PathItem pathItem) {
        applyToOperation(pathItem.getGet());
        applyToOperation(pathItem.getPost());
        applyToOperation(pathItem.getPut());
        applyToOperation(pathItem.getDelete());
        applyToOperation(pathItem.getPatch());
    }

    private void applyToOperation(Operation operation) {
        if (operation == null) {
            return;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        for (String statusCode : statusCodes) {
            if (!responses.containsKey(statusCode)) {
                responses.addApiResponse(statusCode, buildProblemResponse(statusCode));
            }
        }
    }

    private ApiResponse buildProblemResponse(String statusCode) {
        Schema<?> schemaRef = new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA_NAME);
        MediaType mediaType = new MediaType().schema(schemaRef);
        Content content = new Content().addMediaType(PROBLEM_JSON_MEDIA_TYPE, mediaType);

        return new ApiResponse()
                .description(reasonPhrase(statusCode))
                .content(content);
    }

    private void registerSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        if (components.getSchemas() == null || !components.getSchemas().containsKey(PROBLEM_DETAIL_SCHEMA_NAME)) {
            components.addSchemas(PROBLEM_DETAIL_SCHEMA_NAME, problemDetailSchema());
        }
    }

    /**
     * Builds the RFC 9457 problem detail schema: {@code type}, {@code title},
     * {@code status}, {@code detail}, {@code instance}.
     *
     * @return the schema
     */
    public static Schema<?> problemDetailSchema() {
        return new Schema<>()
                .type("object")
                .addProperty("type", new Schema<>()
                        .type("string")
                        .format("uri")
                        .description("A URI reference that identifies the problem type")
                        ._default("about:blank"))
                .addProperty("title", new Schema<>()
                        .type("string")
                        .description("A short, human-readable summary of the problem type"))
                .addProperty("status", new Schema<>()
                        .type("integer")
                        .format("int32")
                        .description("The HTTP status code generated by the origin server"))
                .addProperty("detail", new Schema<>()
                        .type("string")
                        .description("A human-readable explanation specific to this occurrence of the problem"))
                .addProperty("instance", new Schema<>()
                        .type("string")
                        .format("uri")
                        .description("A URI reference that identifies the specific occurrence of the problem"));
    }

    private String reasonPhrase(String statusCode) {
        return switch (statusCode) {
            case "400" -> "Bad Request";
            case "401" -> "Unauthorized";
            case "403" -> "Forbidden";
            case "404" -> "Not Found";
            case "409" -> "Conflict";
            case "422" -> "Unprocessable Entity";
            case "500" -> "Internal Server Error";
            default -> "Error";
        };
    }
}
