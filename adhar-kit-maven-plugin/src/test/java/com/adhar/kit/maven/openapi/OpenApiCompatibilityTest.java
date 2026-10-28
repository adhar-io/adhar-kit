package com.adhar.kit.maven.openapi;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenApiCompatibility}.
 */
class OpenApiCompatibilityTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");

    private final OpenApiCompatibility compatibility = new OpenApiCompatibility();

    @Test
    void removingAnOperationBreaksClients() throws Exception {
        String before = Files.readString(PETS);
        String after = before.replaceAll("(?s)    delete:\\n      operationId: deletePet.*?(?=  /api/v1/owners)", "");

        OpenApiCompatibility.Result result = compatibility.compare(before, after);

        assertThat(result.breaking()).isTrue();
        assertThat(result.breakingChanges()).containsExactly("removed DELETE /api/v1/pets/{petId}");
        assertThat(result.report()).contains("/api/v1/pets/{petId}");
    }

    @Test
    void aNewRequiredRequestFieldBreaksClientsButANewResponseFieldDoesNot() throws Exception {
        String before = Files.readString(PETS);
        String requiredTag = before.replace("      required: [name]\n      properties:\n        name:",
                "      required: [name, tag]\n      properties:\n        tag:\n          type: string\n        name:");
        assertThat(requiredTag).isNotEqualTo(before);
        assertThat(compatibility.compare(before, requiredTag).breakingChanges())
                .anySatisfy(change -> assertThat(change).contains("POST /api/v1/pets"));

        String ownerField = before.replace("        id:\n          type: string\n        pets:",
                "        id:\n          type: string\n        nickname:\n          type: string\n        pets:")
                .replace("version: 1.2.0", "version: 1.3.0");
        OpenApiCompatibility.Result added = compatibility.compare(before, ownerField);
        assertThat(added.breaking()).isFalse();
        assertThat(added.changed()).isTrue();
        assertThat(added.versionRaised()).isTrue();
        assertThat(added.oldVersion()).isEqualTo("1.2.0");
        assertThat(added.newVersion()).isEqualTo("1.3.0");
    }

    @Test
    void anIdenticalSpecIsUnchanged() throws Exception {
        String spec = Files.readString(PETS);
        OpenApiCompatibility.Result result = compatibility.compare(spec, spec);
        assertThat(result.breaking()).isFalse();
        assertThat(result.changed()).isFalse();
        assertThat(result.versionRaised()).isFalse();
    }

    @Test
    void versionsOrderBySemanticVersioningPrecedence() {
        assertThat(OpenApiCompatibility.compareVersions("1.10.0", "1.9.9")).isPositive();
        assertThat(OpenApiCompatibility.compareVersions("2.0.0", "2.0.0-rc.1")).isPositive();
        assertThat(OpenApiCompatibility.compareVersions("2.0.0-rc.2", "2.0.0-rc.10")).isNegative();
        assertThat(OpenApiCompatibility.compareVersions("2.0.0-alpha", "2.0.0-1")).isPositive();
        assertThat(OpenApiCompatibility.compareVersions("2.0.0-rc.1", "2.0.0-rc.1.1")).isNegative();
        assertThat(OpenApiCompatibility.compareVersions("1.2.3+build.1", "1.2.3+build.2")).isZero();
        assertThat(OpenApiCompatibility.compareVersions("1.2", "1.1.0")).isZero();
        assertThat(OpenApiCompatibility.compareVersions(null, "1.1.0")).isZero();
    }
}
