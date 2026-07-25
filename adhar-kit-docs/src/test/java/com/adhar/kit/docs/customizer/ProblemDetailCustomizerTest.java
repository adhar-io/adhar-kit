package com.adhar.kit.docs.customizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailCustomizerTest {

    @Test
    void customizeRegistersProblemDetailSchemaInComponents() {
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(new Paths());

        new ProblemDetailCustomizer().customize(openApi);

        Components components = openApi.getComponents();
        assertThat(components).isNotNull();
        assertThat(components.getSchemas()).containsKey("ProblemDetail");

        Schema<?> schema = components.getSchemas().get("ProblemDetail");
        assertThat(schema.getProperties()).containsKeys("type", "title", "status", "detail", "instance");
    }

    @Test
    void customizeDoesNotDuplicateSchemaWhenAlreadyRegistered() {
        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Schema<?> preExisting = new Schema<>().type("object").description("pre-existing");
        components.addSchemas("ProblemDetail", preExisting);
        openApi.setComponents(components);
        openApi.setPaths(new Paths());

        new ProblemDetailCustomizer().customize(openApi);

        assertThat(openApi.getComponents().getSchemas().get("ProblemDetail")).isSameAs(preExisting);
    }

    @Test
    void customizeAddsProblemJsonResponsesToOperations() {
        Operation get = new Operation();
        PathItem pathItem = new PathItem();
        pathItem.setGet(get);
        Paths paths = new Paths();
        paths.addPathItem("/api/orders", pathItem);

        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new ProblemDetailCustomizer().customize(openApi);

        ApiResponses responses = get.getResponses();
        assertThat(responses).containsKeys("400", "401", "403", "404", "409", "422", "500");

        ApiResponse notFound = responses.get("404");
        assertThat(notFound.getDescription()).isEqualTo("Not Found");
        MediaType mediaType = notFound.getContent().get(ProblemDetailCustomizer.PROBLEM_JSON_MEDIA_TYPE);
        assertThat(mediaType).isNotNull();
        assertThat(mediaType.getSchema().get$ref()).isEqualTo("#/components/schemas/ProblemDetail");
    }

    @Test
    void customizeWithCustomStatusCodesOnlyAppliesThoseCodes() {
        Operation post = new Operation();
        PathItem pathItem = new PathItem();
        pathItem.setPost(post);
        Paths paths = new Paths();
        paths.addPathItem("/api/items", pathItem);

        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new ProblemDetailCustomizer(Set.of("404")).customize(openApi);

        assertThat(post.getResponses()).containsOnlyKeys("404");
    }

    @Test
    void customizePreservesExistingResponseForSameStatusCode() {
        Operation put = new Operation();
        ApiResponses existing = new ApiResponses();
        existing.addApiResponse("404", new ApiResponse().description("custom not-found"));
        put.setResponses(existing);

        PathItem pathItem = new PathItem();
        pathItem.setPut(put);
        Paths paths = new Paths();
        paths.addPathItem("/api/x", pathItem);
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new ProblemDetailCustomizer(Set.of("404")).customize(openApi);

        assertThat(put.getResponses().get("404").getDescription()).isEqualTo("custom not-found");
    }

    @Test
    void customizeIgnoresNullOperationsAndNullPaths() {
        PathItem pathItem = new PathItem();
        Paths paths = new Paths();
        paths.addPathItem("/empty", pathItem);
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new ProblemDetailCustomizer().customize(openApi);
        assertThat(pathItem.getGet()).isNull();

        OpenAPI noPaths = new OpenAPI();
        new ProblemDetailCustomizer().customize(noPaths);
        assertThat(noPaths.getComponents().getSchemas()).containsKey("ProblemDetail");
    }

    @Test
    void problemDetailSchemaHasExpectedShape() {
        Schema<?> schema = ProblemDetailCustomizer.problemDetailSchema();
        assertThat(schema.getType()).isEqualTo("object");

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties.get("type").getFormat()).isEqualTo("uri");
        assertThat(properties.get("status").getType()).isEqualTo("integer");
        assertThat(properties.get("title").getType()).isEqualTo("string");
        assertThat(properties.get("detail").getType()).isEqualTo("string");
        assertThat(properties.get("instance").getFormat()).isEqualTo("uri");
    }
}
