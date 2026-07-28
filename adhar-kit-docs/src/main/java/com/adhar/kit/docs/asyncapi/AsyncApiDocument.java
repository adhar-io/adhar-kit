package com.adhar.kit.docs.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.Objects;

/**
 * An immutable, generated AsyncAPI 3.0 document backed by a Jackson tree.
 *
 * <p>Instances are produced by {@link AsyncApiGenerator} and serialized (JSON/YAML) by
 * {@link AsyncApiSpecExporter}, mirroring the OpenAPI export flow.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public final class AsyncApiDocument {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final JsonNode root;

    /**
     * Wraps the given AsyncAPI root node.
     *
     * @param root the AsyncAPI 3.0 root document node
     */
    public AsyncApiDocument(JsonNode root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    /**
     * @return the underlying document tree
     */
    public JsonNode getRoot() {
        return root;
    }

    /**
     * @return the document as a pretty-printed JSON string
     */
    public String toJson() {
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AsyncAPI document to JSON", e);
        }
    }

    /**
     * @return the document as a YAML string
     */
    public String toYaml() {
        try {
            return YAML_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AsyncAPI document to YAML", e);
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
