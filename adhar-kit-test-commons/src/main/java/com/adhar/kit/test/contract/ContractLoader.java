package com.adhar.kit.test.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads {@link ContractDefinition}s from WireMock-style stub-mapping JSON files, either from a
 * filesystem directory or a directory on the test classpath (typically {@code src/test/resources}).
 *
 * <p>Each {@code *.json} file is expected to follow the WireMock stub-mapping shape:</p>
 * <pre>{@code
 * {
 *   "request":  { "method": "GET", "urlPath": "/api/users/1" },
 *   "response": { "status": 200, "jsonBody": { "id": 1, "name": "John" } }
 * }
 * }</pre>
 *
 * <p>The whole document is retained verbatim ({@link ContractDefinition#rawJson()}) so it can be
 * fed directly to WireMock, while {@code request}/{@code response} are also parsed out for
 * provider-side verification. Both {@code response.jsonBody} (an inline object) and
 * {@code response.body} (a JSON string) are recognised as the expected body.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class ContractLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse a single contract document. The core, side-effect-free entry point used by the
     * directory/classpath loaders and directly unit-testable.
     *
     * @param name logical contract name
     * @param json WireMock-style stub-mapping JSON
     * @return the parsed contract
     * @throws IllegalArgumentException if the JSON is malformed
     */
    public ContractDefinition parse(String name, String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Contract '" + name + "' is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Contract '" + name + "' must be a JSON object");
        }

        JsonNode request = root.path("request");
        JsonNode response = root.path("response");

        String method = text(request, "method", "ANY");
        String path = firstText(request, "urlPath", "url", "urlPathPattern", "urlPattern");
        int status = response.path("status").asInt(200);
        JsonNode expectedBody = extractBody(name, response);

        return new ContractDefinition(name, method, path, status, expectedBody, json);
    }

    /**
     * Load every {@code *.json} contract in a filesystem directory, sorted by file name.
     */
    public List<ContractDefinition> loadFromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Contracts directory does not exist: " + directory);
        }
        List<ContractDefinition> contracts = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> contracts.add(parse(contractName(p), readString(p))));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list contracts directory: " + directory, e);
        }
        log.info("Loaded {} contract(s) from {}", contracts.size(), directory);
        return contracts;
    }

    /**
     * Load every {@code *.json} contract from a directory on the classpath (e.g.
     * {@code "contracts"} resolving to {@code src/test/resources/contracts}).
     *
     * <p>Works for the common case of a file-based classpath (exploded {@code target/test-classes});
     * loading from inside a jar is not supported.</p>
     */
    public List<ContractDefinition> loadFromClasspath(String classpathDirectory) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(classpathDirectory);
        if (url == null) {
            throw new IllegalArgumentException("Contracts directory not found on classpath: " + classpathDirectory);
        }
        try {
            return loadFromDirectory(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid classpath URL for contracts directory: " + url, e);
        }
    }

    private JsonNode extractBody(String name, JsonNode response) {
        JsonNode jsonBody = response.get("jsonBody");
        if (jsonBody != null && !jsonBody.isNull()) {
            return jsonBody;
        }
        JsonNode body = response.get("body");
        if (body != null && body.isTextual() && !body.asText().isBlank()) {
            try {
                return objectMapper.readTree(body.asText());
            } catch (IOException e) {
                log.debug("Contract '{}' response.body is not JSON; skipping body verification", name);
            }
        }
        return null;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : defaultValue;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private String contractName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read contract file: " + file, e);
        }
    }
}
