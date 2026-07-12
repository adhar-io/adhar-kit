package com.adhar.kit.docs.customizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdharOpenApiCustomizerTest {

    @Test
    void builderBuildsCustomizer() {
        AdharOpenApiCustomizer customizer = AdharOpenApiCustomizer.builder()
                .addCommonHeaders()
                .addCommonResponses()
                .addExamples()
                .build();
        assertThat(customizer).isNotNull();
    }

    @Test
    void customizeWithNullPathsDoesNothing() {
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(null);
        // Should not throw.
        new AdharOpenApiCustomizer().customize(openApi);
        assertThat(openApi.getPaths()).isNull();
    }

    @Test
    void customizeAddsHeadersAndResponsesToOperations() {
        Operation get = new Operation();
        Operation post = new Operation();
        PathItem pathItem = new PathItem();
        pathItem.setGet(get);
        pathItem.setPost(post);

        Paths paths = new Paths();
        paths.addPathItem("/api/orders", pathItem);

        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new AdharOpenApiCustomizer().customize(openApi);

        // Both operations get the two common headers.
        assertThat(get.getParameters()).hasSize(2);
        assertThat(get.getParameters()).extracting("name")
                .containsExactlyInAnyOrder("X-Correlation-ID", "X-Request-ID");

        // Common error responses added.
        ApiResponses responses = get.getResponses();
        assertThat(responses).containsKeys("400", "401", "403", "500");
        ApiResponse badRequest = responses.get("400");
        assertThat(badRequest.getDescription()).isEqualTo("Invalid request parameters");
        assertThat(badRequest.getContent()).containsKey("application/json");
        assertThat(badRequest.getHeaders()).containsKeys("X-Correlation-ID", "X-Request-ID");

        assertThat(post.getParameters()).hasSize(2);
        assertThat(post.getResponses()).containsKeys("400", "401", "403", "500");
    }

    @Test
    void customizePreservesExistingResponses() {
        Operation put = new Operation();
        ApiResponses existing = new ApiResponses();
        existing.addApiResponse("400", new ApiResponse().description("custom 400"));
        put.setResponses(existing);

        PathItem pathItem = new PathItem();
        pathItem.setPut(put);
        Paths paths = new Paths();
        paths.addPathItem("/api/items", pathItem);
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new AdharOpenApiCustomizer().customize(openApi);

        // Existing 400 not overwritten, others added.
        assertThat(put.getResponses().get("400").getDescription()).isEqualTo("custom 400");
        assertThat(put.getResponses()).containsKeys("401", "403", "500");
    }

    @Test
    void customizeIgnoresNullOperations() {
        PathItem pathItem = new PathItem(); // all operations null
        Paths paths = new Paths();
        paths.addPathItem("/empty", pathItem);
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        // Should not throw despite null get/post/put/delete/patch.
        new AdharOpenApiCustomizer().customize(openApi);
        assertThat(pathItem.getGet()).isNull();
    }

    @Test
    void customizeHandlesDeleteAndPatch() {
        Operation delete = new Operation();
        Operation patch = new Operation();
        PathItem pathItem = new PathItem();
        pathItem.setDelete(delete);
        pathItem.setPatch(patch);
        Paths paths = new Paths();
        paths.addPathItem("/api/x", pathItem);
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(paths);

        new AdharOpenApiCustomizer().customize(openApi);

        assertThat(delete.getParameters()).hasSize(2);
        assertThat(patch.getParameters()).hasSize(2);
    }
}
