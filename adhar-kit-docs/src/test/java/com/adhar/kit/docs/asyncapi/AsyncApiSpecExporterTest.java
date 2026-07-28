package com.adhar.kit.docs.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncApiSpecExporterTest {

    private final AsyncApiSpecExporter exporter = new AsyncApiSpecExporter();
    private final AsyncApiGenerator generator = new AsyncApiGenerator();
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private AsyncApiDocument sampleDocument() {
        return generator.generate("Orders Events", "1.0.0", "Events",
                List.of(new AsyncApiGenerator.ChannelDefinition(
                        "orderCreated", "orders.created", "created",
                        AsyncApiGenerator.Action.SEND, "OrderCreated", "object")));
    }

    @Test
    void exportJsonWritesValidJsonFile(@TempDir Path tempDir) throws IOException {
        Path path = exporter.exportJson(sampleDocument(), tempDir);
        assertThat(path).exists();
        assertThat(path.getFileName().toString()).isEqualTo("asyncapi.json");
        JsonNode node = jsonMapper.readTree(Files.readString(path));
        assertThat(node.path("asyncapi").asText()).isEqualTo("3.0.0");
        assertThat(node.path("info").path("title").asText()).isEqualTo("Orders Events");
    }

    @Test
    void exportYamlWritesValidYamlFile(@TempDir Path tempDir) throws IOException {
        Path path = exporter.exportYaml(sampleDocument(), tempDir);
        assertThat(path).exists();
        assertThat(path.getFileName().toString()).isEqualTo("asyncapi.yaml");
        JsonNode node = yamlMapper.readTree(Files.readString(path));
        assertThat(node.path("asyncapi").asText()).isEqualTo("3.0.0");
    }

    @Test
    void exportAllWritesBothFilesAndReturnsPaths(@TempDir Path tempDir) {
        AsyncApiSpecExporter.ExportResult result = exporter.exportAll(sampleDocument(), tempDir);
        assertThat(result.jsonPath()).exists();
        assertThat(result.yamlPath()).exists();
        assertThat(result.jsonPath().getParent()).isEqualTo(tempDir);
        assertThat(result.yamlPath().getParent()).isEqualTo(tempDir);
    }

    @Test
    void exportCreatesMissingOutputDirectory(@TempDir Path tempDir) {
        Path nested = tempDir.resolve("nested").resolve("dir");
        assertThat(Files.exists(nested)).isFalse();
        Path path = exporter.exportJson(sampleDocument(), nested);
        assertThat(path).exists();
        assertThat(Files.isDirectory(nested)).isTrue();
    }

    @Test
    void toJsonAndToYamlProduceParsableContent() throws IOException {
        AsyncApiDocument doc = sampleDocument();
        assertThat(jsonMapper.readTree(exporter.toJson(doc)).path("asyncapi").asText())
                .isEqualTo("3.0.0");
        assertThat(yamlMapper.readTree(exporter.toYaml(doc)).path("asyncapi").asText())
                .isEqualTo("3.0.0");
    }

    @Test
    void exportUsesDefaultOutputDirectoryWhenNotSpecified() {
        Path path = exporter.exportJson(sampleDocument());
        assertThat(path.toString()).contains("target");
        assertThat(path).exists();
    }

    @Test
    void exportYamlUsesDefaultOutputDirectoryWhenNotSpecified() {
        Path path = exporter.exportYaml(sampleDocument());
        assertThat(path.toString()).contains("target");
        assertThat(path).exists();
    }

    @Test
    void toJsonRejectsNullDocument() {
        assertThatThrownBy(() -> exporter.toJson(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void exportRejectsNullOutputDirectory() {
        assertThatThrownBy(() -> exporter.exportJson(sampleDocument(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void documentRejectsNullRoot() {
        assertThatThrownBy(() -> new AsyncApiDocument(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void documentExposesRoot() {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("asyncapi", "3.0.0");
        AsyncApiDocument doc = new AsyncApiDocument(node);
        assertThat(doc.getRoot()).isSameAs(node);
    }

    @Test
    void exportExceptionCarriesCause() {
        Exception cause = new RuntimeException("boom");
        AsyncApiSpecExporter.AsyncApiExportException ex =
                new AsyncApiSpecExporter.AsyncApiExportException("msg", cause);
        assertThat(ex).hasMessage("msg").hasCause(cause);
    }
}
