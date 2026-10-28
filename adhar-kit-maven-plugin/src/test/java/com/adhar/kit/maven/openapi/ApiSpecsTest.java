package com.adhar.kit.maven.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApiSpecs} and {@link OpenApiPreset}.
 */
class ApiSpecsTest {

    @Test
    void discoversOneSpecPerMajorDirectoryLowestFirst(@TempDir Path api) throws Exception {
        write(api.resolve("v10/openapi.yaml"));
        write(api.resolve("v2/openapi.yaml"));
        write(api.resolve("v1/openapi.yaml"));
        write(api.resolve("v3/openapi.yml"));      // not the convention's file name
        write(api.resolve("v01/openapi.yaml"));    // not a major
        write(api.resolve("beta/openapi.yaml"));
        write(api.resolve("openapi.yaml"));

        assertThat(ApiSpecs.discover(api)).extracting(ApiSpecs.Discovered::major).containsExactly(1, 2, 10);
        assertThat(ApiSpecs.discover(api).get(0).file()).isEqualTo(api.resolve("v1/openapi.yaml"));
    }

    @Test
    void aMissingApiDirectoryHasNoSpecs(@TempDir Path base) throws Exception {
        assertThat(ApiSpecs.discover(base.resolve("api"))).isEmpty();
        assertThat(ApiSpecs.discover(null)).isEmpty();
    }

    @Test
    void theMajorComesFromTheDirectoryName() {
        assertThat(ApiSpecs.majorOfDirectory(Path.of("api/v3/openapi.yaml"))).hasValue(3);
        assertThat(ApiSpecs.majorOfDirectory(Path.of("clients/payments/openapi.yaml"))).isEmpty();
    }

    @Test
    void semanticVersionsAndTheirMajors() {
        assertThat(ApiSpecs.isSemanticVersion("1.4.2")).isTrue();
        assertThat(ApiSpecs.isSemanticVersion("2.0.0-rc.1+sha.5114f85")).isTrue();
        assertThat(ApiSpecs.isSemanticVersion("1.0")).isFalse();
        assertThat(ApiSpecs.isSemanticVersion("v1.0.0")).isFalse();
        assertThat(ApiSpecs.isSemanticVersion("01.0.0")).isFalse();
        assertThat(ApiSpecs.isSemanticVersion(null)).isFalse();
        assertThat(ApiSpecs.majorOf("12.3.4")).hasValue(12);
        assertThat(ApiSpecs.majorOf("latest")).isEqualTo(OptionalInt.empty());
        assertThat(ApiSpecs.majorOf("99999999999.0.0")).isEmpty();
    }

    @Test
    void readsYamlAndJsonButNotScalars(@TempDir Path dir) throws Exception {
        Path json = Files.writeString(dir.resolve("spec.json"), "{\"openapi\": \"3.0.3\"}");
        assertThat(ApiSpecs.read(json).path("openapi").asText()).isEqualTo("3.0.3");
        Path scalar = Files.writeString(dir.resolve("spec.yaml"), "42\n");
        assertThatThrownBy(() -> ApiSpecs.read(scalar)).hasMessageContaining("not an OpenAPI document");
    }

    @Test
    void presetsResolveByIdAndKnowWhetherTheyServe() {
        assertThat(OpenApiPreset.of("Spring")).isEqualTo(OpenApiPreset.SPRING);
        assertThat(OpenApiPreset.of(" spring-client ")).isEqualTo(OpenApiPreset.SPRING_CLIENT);
        assertThat(OpenApiPreset.of("jaxrs").generatorName()).isEqualTo("jaxrs-spec");
        assertThat(OpenApiPreset.SPRING.server()).isTrue();
        assertThat(OpenApiPreset.SPRING_CLIENT.server()).isFalse();
        assertThat(OpenApiPreset.SPRING.options()).containsEntry("interfaceOnly", true)
                .containsEntry("skipDefaultInterface", true)
                .containsEntry("useSpringBoot4", true)
                .containsEntry("hideGenerationTimestamp", true);
        assertThatThrownBy(() -> OpenApiPreset.of("feign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spring, jaxrs, spring-client");
    }

    @Test
    void specsKnowWhereTheyShip() {
        ApiSpec spec = new ApiSpec(Path.of("api/v2/openapi.yaml"), 2, OpenApiPreset.SPRING, "com.example.api.v2");
        assertThat(spec.shipped()).isTrue();
        assertThat(spec.classpathLocation()).isEqualTo("api/v2/openapi.yaml");
        assertThat(spec.servedPath()).isEqualTo("/api/v2/openapi.yaml");
        assertThat(spec.modelPackage()).isEqualTo("com.example.api.v2.model");
        assertThatThrownBy(() -> new ApiSpec(Path.of("x"), -1, OpenApiPreset.SPRING, "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void write(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "openapi: 3.0.3\n");
    }
}
