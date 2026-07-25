package com.adhar.kit.docs.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiSpecExporterTest {

    private final OpenApiSpecExporter exporter = new OpenApiSpecExporter();
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private OpenAPI sampleSpec() {
        OpenAPI openApi = new OpenAPI();
        openApi.setInfo(new Info().title("Sample API").version("1.0.0"));
        openApi.setPaths(new Paths());
        return openApi;
    }

    @Test
    void exportJsonWritesValidJsonFile(@TempDir Path tempDir) throws IOException {
        Path path = exporter.exportJson(sampleSpec(), tempDir);

        assertThat(path).exists();
        assertThat(path.getFileName().toString()).isEqualTo("openapi.json");

        JsonNode node = jsonMapper.readTree(Files.readString(path));
        assertThat(node.path("info").path("title").asText()).isEqualTo("Sample API");
        assertThat(node.path("openapi").asText()).isNotBlank();
    }

    @Test
    void exportYamlWritesValidYamlFile(@TempDir Path tempDir) throws IOException {
        Path path = exporter.exportYaml(sampleSpec(), tempDir);

        assertThat(path).exists();
        assertThat(path.getFileName().toString()).isEqualTo("openapi.yaml");

        JsonNode node = yamlMapper.readTree(Files.readString(path));
        assertThat(node.path("info").path("title").asText()).isEqualTo("Sample API");
    }

    @Test
    void exportAllWritesBothFilesAndReturnsPaths(@TempDir Path tempDir) {
        OpenApiSpecExporter.ExportResult result = exporter.exportAll(sampleSpec(), tempDir);

        assertThat(result.jsonPath()).exists();
        assertThat(result.yamlPath()).exists();
        assertThat(result.jsonPath().getParent()).isEqualTo(tempDir);
        assertThat(result.yamlPath().getParent()).isEqualTo(tempDir);
    }

    @Test
    void exportCreatesMissingOutputDirectory(@TempDir Path tempDir) {
        Path nested = tempDir.resolve("nested").resolve("dir");
        assertThat(Files.exists(nested)).isFalse();

        Path path = exporter.exportJson(sampleSpec(), nested);

        assertThat(path).exists();
        assertThat(Files.isDirectory(nested)).isTrue();
    }

    @Test
    void exportRawJsonContentWritesFileVerbatim(@TempDir Path tempDir) throws IOException {
        String raw = "{\"openapi\":\"3.0.1\"}";
        Path path = exporter.exportJson(raw, tempDir);
        assertThat(Files.readString(path)).isEqualTo(raw);
    }

    @Test
    void exportRawYamlContentWritesFileVerbatim(@TempDir Path tempDir) throws IOException {
        String raw = "openapi: '3.0.1'\n";
        Path path = exporter.exportYaml(raw, tempDir);
        assertThat(Files.readString(path)).isEqualTo(raw);
    }

    @Test
    void toJsonAndToYamlProduceParsableContent() throws IOException {
        OpenAPI openApi = sampleSpec();
        String json = exporter.toJson(openApi);
        String yaml = exporter.toYaml(openApi);

        assertThat(jsonMapper.readTree(json).path("info").path("title").asText()).isEqualTo("Sample API");
        assertThat(yamlMapper.readTree(yaml).path("info").path("title").asText()).isEqualTo("Sample API");
    }

    @Test
    void toJsonRejectsNullOpenApi() {
        assertThatThrownBy(() -> exporter.toJson(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exportUsesDefaultOutputDirectoryWhenNotSpecified() {
        // Uses the module's own default (target/) so this simply verifies it doesn't throw
        // and returns a path rooted at the default directory name.
        Path path = exporter.exportJson(sampleSpec());
        assertThat(path.toString()).contains("target");
        assertThat(path).exists();
    }
}
