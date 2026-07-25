package com.adhar.kit.docs.export;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Exports a generated {@link OpenAPI} specification to JSON and/or YAML files on disk.
 *
 * <p>Useful for CI pipelines that want to publish a static {@code openapi.json}/
 * {@code openapi.yaml} artifact (e.g. for contract testing, client generation, or
 * uploading to an API catalog) without needing a running web server.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * OpenApiSpecExporter exporter = new OpenApiSpecExporter();
 * OpenApiSpecExporter.ExportResult result = exporter.exportAll(openApi, Path.of("target", "openapi"));
 * // result.jsonPath(), result.yamlPath()
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class OpenApiSpecExporter {

    /** Default JSON output file name. */
    public static final String DEFAULT_JSON_FILENAME = "openapi.json";

    /** Default YAML output file name. */
    public static final String DEFAULT_YAML_FILENAME = "openapi.yaml";

    /** Default output directory, relative to the current working directory. */
    public static final Path DEFAULT_OUTPUT_DIR = Path.of("target");

    /**
     * Serializes an {@link OpenAPI} model to a pretty-printed JSON string.
     *
     * @param openApi the OpenAPI model
     * @return the JSON representation
     */
    public String toJson(OpenAPI openApi) {
        Objects.requireNonNull(openApi, "openApi must not be null");
        try {
            return Json.pretty(openApi);
        } catch (Exception e) {
            throw new OpenApiExportException("Failed to serialize OpenAPI spec to JSON", e);
        }
    }

    /**
     * Serializes an {@link OpenAPI} model to a YAML string.
     *
     * @param openApi the OpenAPI model
     * @return the YAML representation
     */
    public String toYaml(OpenAPI openApi) {
        Objects.requireNonNull(openApi, "openApi must not be null");
        try {
            return Yaml.pretty(openApi);
        } catch (Exception e) {
            throw new OpenApiExportException("Failed to serialize OpenAPI spec to YAML", e);
        }
    }

    /**
     * Writes the JSON representation of the given OpenAPI model to
     * {@code outputDirectory/openapi.json}.
     *
     * @param openApi         the OpenAPI model
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportJson(OpenAPI openApi, Path outputDirectory) {
        return writeToFile(toJson(openApi), outputDirectory, DEFAULT_JSON_FILENAME);
    }

    /**
     * Writes the JSON representation of the given OpenAPI model to
     * {@code target/openapi.json}.
     *
     * @param openApi the OpenAPI model
     * @return the path of the written file
     */
    public Path exportJson(OpenAPI openApi) {
        return exportJson(openApi, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Writes a raw, already-serialized JSON spec string to {@code outputDirectory/openapi.json}.
     *
     * @param rawJson         the JSON content
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportJson(String rawJson, Path outputDirectory) {
        return writeToFile(rawJson, outputDirectory, DEFAULT_JSON_FILENAME);
    }

    /**
     * Writes the YAML representation of the given OpenAPI model to
     * {@code outputDirectory/openapi.yaml}.
     *
     * @param openApi         the OpenAPI model
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportYaml(OpenAPI openApi, Path outputDirectory) {
        return writeToFile(toYaml(openApi), outputDirectory, DEFAULT_YAML_FILENAME);
    }

    /**
     * Writes the YAML representation of the given OpenAPI model to
     * {@code target/openapi.yaml}.
     *
     * @param openApi the OpenAPI model
     * @return the path of the written file
     */
    public Path exportYaml(OpenAPI openApi) {
        return exportYaml(openApi, DEFAULT_OUTPUT_DIR);
    }

    /**
     * Writes a raw, already-serialized YAML spec string to {@code outputDirectory/openapi.yaml}.
     *
     * @param rawYaml         the YAML content
     * @param outputDirectory the directory to write to (created if missing)
     * @return the path of the written file
     */
    public Path exportYaml(String rawYaml, Path outputDirectory) {
        return writeToFile(rawYaml, outputDirectory, DEFAULT_YAML_FILENAME);
    }

    /**
     * Writes both the JSON and YAML representation of the given OpenAPI model to
     * {@code outputDirectory}.
     *
     * @param openApi         the OpenAPI model
     * @param outputDirectory the directory to write to (created if missing)
     * @return an {@link ExportResult} with the paths of both written files
     */
    public ExportResult exportAll(OpenAPI openApi, Path outputDirectory) {
        Path json = exportJson(openApi, outputDirectory);
        Path yaml = exportYaml(openApi, outputDirectory);
        return new ExportResult(json, yaml);
    }

    /**
     * Writes both the JSON and YAML representation of the given OpenAPI model to
     * {@code target/}.
     *
     * @param openApi the OpenAPI model
     * @return an {@link ExportResult} with the paths of both written files
     */
    public ExportResult exportAll(OpenAPI openApi) {
        return exportAll(openApi, DEFAULT_OUTPUT_DIR);
    }

    private Path writeToFile(String content, Path outputDirectory, String filename) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        try {
            Files.createDirectories(outputDirectory);
            Path target = outputDirectory.resolve(filename);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("Exported OpenAPI spec to {}", target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            throw new OpenApiExportException(
                    "Failed to write OpenAPI spec to " + outputDirectory.resolve(filename), e);
        }
    }

    /**
     * The result of exporting both JSON and YAML representations of a spec.
     *
     * @param jsonPath path to the written JSON file
     * @param yamlPath path to the written YAML file
     */
    public record ExportResult(Path jsonPath, Path yamlPath) {
    }

    /**
     * Thrown when serializing or writing an OpenAPI spec fails.
     */
    public static class OpenApiExportException extends RuntimeException {
        public OpenApiExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
