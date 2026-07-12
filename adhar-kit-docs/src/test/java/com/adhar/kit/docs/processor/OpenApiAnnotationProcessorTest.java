package com.adhar.kit.docs.processor;

import com.adhar.kit.docs.annotation.EnableOpenApiDocs;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAnnotationProcessorTest {

    @EnableOpenApiDocs(
            title = "Annotated API",
            version = "2.5.0",
            description = "Annotated description",
            enableJwtSecurity = true,
            enableApiKeySecurity = true,
            packagesToScan = {"com.example.a", "com.example.b"})
    static class FullyAnnotated {
    }

    @EnableOpenApiDocs(title = "Minimal", version = "1.0.0")
    static class MinimalAnnotated {
    }

    @Test
    void processAnnotationWithFullConfig() {
        EnableOpenApiDocs annotation = FullyAnnotated.class.getAnnotation(EnableOpenApiDocs.class);
        OpenAPI openApi = OpenApiAnnotationProcessor.processAnnotation(annotation, FullyAnnotated.class);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Annotated API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("2.5.0");
        assertThat(openApi.getInfo().getDescription()).isEqualTo("Annotated description");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKeys("bearerAuth", "apiKey");
    }

    @Test
    void processAnnotationWithMinimalConfigAutoDetectsPackage() {
        EnableOpenApiDocs annotation = MinimalAnnotated.class.getAnnotation(EnableOpenApiDocs.class);
        OpenAPI openApi = OpenApiAnnotationProcessor.processAnnotation(annotation, MinimalAnnotated.class);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Minimal");
        // No security flags enabled -> no security schemes registered.
        assertThat(openApi.getComponents().getSecuritySchemes()).isNullOrEmpty();
    }

    @Test
    void detectFrameworkReturnsSpringBootOnThisClasspath() {
        assertThat(OpenApiAnnotationProcessor.detectFramework())
                .isEqualTo(OpenApiAnnotationProcessor.Framework.SPRING_BOOT);
    }

    @Test
    void frameworkEnumValues() {
        assertThat(OpenApiAnnotationProcessor.Framework.valueOf("QUARKUS"))
                .isEqualTo(OpenApiAnnotationProcessor.Framework.QUARKUS);
        assertThat(OpenApiAnnotationProcessor.Framework.values()).hasSize(4);
    }
}
