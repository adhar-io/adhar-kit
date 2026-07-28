package com.adhar.kit.docs.asyncapi;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Exports a generated {@link AsyncApiDocument} to JSON and/or YAML files on disk,
 * mirroring {@code com.adhar.kit.docs.export.OpenApiSpecExporter} so that an
 * {@code asyncapi.json}/{@code asyncapi.yaml} artifact can be published alongside the
 * OpenAPI export in CI.
 *
 * <p><b>Example</b></p>
 * <pre>{@code
 * AsyncApiSpecExporter exporter = new AsyncApiSpecExporter();
 * AsyncApiSpecExporter.ExportResult result = exporter.exportAll(document, Path.of("target"));
 * // result.jsonPath(), result.yamlPath()
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class AsyncApiSpecExporter {

    /** Default JSON output file name. */
    public static final String DEFAULT_JSON_FILENAME = "asyncapi.json";

    /** Default YAML output file name. */
    public static final String DEFAULT_YAML_FILENAME = "asyncapi.yaml";

    /** Default output directory, relative to the current working directory. */
    public static final Path DEFAULT_OUTPUT_DIR = Path.of("target");

    /**
     * Serializes an AsyncAPI document to a pretty-printed JSON string.
     *
     * @param document the AsyncAPI document
     * @return the JSON representation
     */
    public String toJson(AsyncApiDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return document.toJson();
    }

    /**
     * Serializes an AsyncAPI document to a YAML string.
     *
     * @param document the AsyncAPI document
     * @return the YAML representation
     */
    public String toYaml(AsyncApiDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return document.toYaml();
    }

    /**
     * Writes the JSON representation to {@code outputDirectory/asyncapi.json}.
     *
     * @param document        the AsyncAPI document
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportJson(AsyncApiDocument document, Path outputDirectory) {
        return writeToFile(toJson(document), outputDirectory, DEFAULT_JSON_FILENAME);
    }

    /**
     * Writes the JSON representation to {@code target/asyncapi.json}.
     *
     * @param document the AsyncAPI document
     * @return the path of the written file
     */
    public Path exportJson(AsyncApiDocument document) {
        return exportJson(document, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Writes the YAML representation to {@code outputDirectory/asyncapi.yaml}.
     *
     * @param document        the AsyncAPI document
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportYaml(AsyncApiDocument document, Path outputDirectory) {
        return writeToFile(toYaml(document), outputDirectory, DEFAULT_YAML_FILENAME);
    }

    /**
     * Writes the YAML representation to {@code target/asyncapi.yaml}.
     *
     * @param document the AsyncAPI document
     * @return the path of the written file
     */
    public Path exportYaml(AsyncApiDocument document) {
        return exportYaml(document, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Writes both the JSON and YAML representation to {@code outputDirectory}.
     *
     * @param document        the AsyncAPI document
     * @param outputDirectory the directory to write to (created if missing)
     * @return an {@link ExportResult} with the paths of both written files
     */
    public ExportResult exportAll(AsyncApiDocument document, Path outputDirectory) {
        Path json = exportJson(document, outputDirectory);
        Path yaml = exportYaml(document, outputDirectory);
        return new ExportResult(json, yaml);
    }

    /**
     * Writes both the JSON and YAML representation to {@code target/}.
     *
     * @param document the AsyncAPI document
     * @return an {@link ExportResult} with the paths of both written files
     */
    public ExportResult exportAll(AsyncApiDocument document) {
        return exportAll(document, DEFAULT_OUTPUT_DIR);
    }

    private Path writeToFile(String content, Path outputDirectory, String filename) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        try {
            Files.createDirectories(outputDirectory);
            Path target = outputDirectory.resolve(filename);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("Exported AsyncAPI spec to {}", target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            throw new AsyncApiExportException(
                    "Failed to write AsyncAPI spec to " + outputDirectory.resolve(filename), e);
        }
    }

    /**
     * The result of exporting both JSON and YAML representations of a document.
     *
     * @param jsonPath path to the written JSON file
     * @param yamlPath path to the written YAML file
     */
    public record ExportResult(Path jsonPath, Path yamlPath) {
    }

    /**
     * Thrown when serializing or writing an AsyncAPI document fails.
     */
    public static class AsyncApiExportException extends RuntimeException {
        public AsyncApiExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
