package com.adhar.kit.docs.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes a breaking-change diff between two OpenAPI specification documents.
 *
 * <p>The comparison is a pure Jackson tree-walk (no SpringDoc/servlet dependency),
 * which makes it suitable as a reusable CI gate: point it at an {@code old} and a
 * {@code new} spec and it returns a structured {@link SpecDiffReport} classifying every
 * detected change as {@link Change.Severity#BREAKING BREAKING} or
 * {@link Change.Severity#NON_BREAKING NON_BREAKING}.</p>
 *
 * <p><b>Detected changes</b></p>
 * <ul>
 *   <li>Removed / added paths (removal is breaking)</li>
 *   <li>Removed / added operations (HTTP methods) on a path (removal is breaking)</li>
 *   <li>Removed / added operation parameters (a removed required parameter and an added
 *       required parameter are breaking)</li>
 *   <li>Changed parameter / schema types (breaking)</li>
 *   <li>Newly-required fields on a schema or parameter (breaking)</li>
 *   <li>Removed / added enum values (removal is breaking)</li>
 *   <li>Removed component schemas (breaking)</li>
 * </ul>
 *
 * <p><b>Example</b></p>
 * <pre>{@code
 * OpenApiDiffService diff = new OpenApiDiffService();
 * SpecDiffReport report = diff.diff(oldJson, newJson);
 * if (report.hasBreakingChanges()) {
 *     throw new IllegalStateException(report.summary());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class OpenApiDiffService {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final int MAX_SCHEMA_DEPTH = 50;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Compares two already-parsed OpenAPI documents.
     *
     * @param oldSpec the baseline (previous) spec tree
     * @param newSpec the candidate (new) spec tree
     * @return a structured report of every detected change
     */
    public SpecDiffReport diff(JsonNode oldSpec, JsonNode newSpec) {
        Objects.requireNonNull(oldSpec, "oldSpec must not be null");
        Objects.requireNonNull(newSpec, "newSpec must not be null");

        List<Change> changes = new ArrayList<>();
        comparePaths(oldSpec.path("paths"), newSpec.path("paths"), changes);
        compareComponentSchemas(
                oldSpec.path("components").path("schemas"),
                newSpec.path("components").path("schemas"),
                changes);

        SpecDiffReport report = new SpecDiffReport(changes);
        log.info("OpenAPI diff complete: {} change(s), {} breaking",
                report.changes().size(), report.breakingCount());
        return report;
    }

    /**
     * Compares two OpenAPI documents supplied as raw JSON or YAML text. The format of
     * each argument is auto-detected (a leading {@code '{'} is treated as JSON, otherwise
     * YAML).
     *
     * @param oldSpec the baseline spec as JSON or YAML
     * @param newSpec the candidate spec as JSON or YAML
     * @return a structured report of every detected change
     */
    public SpecDiffReport diff(String oldSpec, String newSpec) {
        return diff(parse(oldSpec), parse(newSpec));
    }

    /**
     * Compares two Swagger {@link OpenAPI} models by serializing each to a Jackson tree.
     *
     * @param oldSpec the baseline model
     * @param newSpec the candidate model
     * @return a structured report of every detected change
     */
    public SpecDiffReport diff(OpenAPI oldSpec, OpenAPI newSpec) {
        Objects.requireNonNull(oldSpec, "oldSpec must not be null");
        Objects.requireNonNull(newSpec, "newSpec must not be null");
        return diff(Json.mapper().valueToTree(oldSpec), Json.mapper().valueToTree(newSpec));
    }

    private JsonNode parse(String spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String trimmed = spec.stripLeading();
        try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return jsonMapper.readTree(spec);
            }
            return yamlMapper.readTree(spec);
        } catch (Exception e) {
            throw new SpecParseException("Failed to parse OpenAPI spec", e);
        }
    }

    // ---- Paths / operations ----

    private void comparePaths(JsonNode oldPaths, JsonNode newPaths, List<Change> changes) {
        if (oldPaths.isObject()) {
            for (Map.Entry<String, JsonNode> entry : oldPaths.properties()) {
                String path = entry.getKey();
                JsonNode newPathItem = newPaths.path(path);
                if (newPathItem.isMissingNode()) {
                    changes.add(new Change(Change.Type.PATH_REMOVED, Change.Severity.BREAKING,
                            "paths." + path, "Path '" + path + "' was removed"));
                } else {
                    compareOperations(path, entry.getValue(), newPathItem, changes);
                }
            }
        }
        if (newPaths.isObject()) {
            for (Map.Entry<String, JsonNode> entry : newPaths.properties()) {
                if (oldPaths.path(entry.getKey()).isMissingNode()) {
                    changes.add(new Change(Change.Type.PATH_ADDED, Change.Severity.NON_BREAKING,
                            "paths." + entry.getKey(), "Path '" + entry.getKey() + "' was added"));
                }
            }
        }
    }

    private void compareOperations(String path, JsonNode oldItem, JsonNode newItem, List<Change> changes) {
        for (String method : HTTP_METHODS) {
            boolean inOld = oldItem.has(method);
            boolean inNew = newItem.has(method);
            String location = "paths." + path + "." + method;
            if (inOld && !inNew) {
                changes.add(new Change(Change.Type.OPERATION_REMOVED, Change.Severity.BREAKING,
                        location, method.toUpperCase() + " " + path + " was removed"));
            } else if (!inOld && inNew) {
                changes.add(new Change(Change.Type.OPERATION_ADDED, Change.Severity.NON_BREAKING,
                        location, method.toUpperCase() + " " + path + " was added"));
            } else if (inOld) {
                compareParameters(location, oldItem.get(method).path("parameters"),
                        newItem.get(method).path("parameters"), changes);
            }
        }
    }

    private void compareParameters(String location, JsonNode oldParams, JsonNode newParams,
                                   List<Change> changes) {
        Map<String, JsonNode> oldByKey = indexParameters(oldParams);
        Map<String, JsonNode> newByKey = indexParameters(newParams);

        for (Map.Entry<String, JsonNode> entry : oldByKey.entrySet()) {
            JsonNode oldParam = entry.getValue();
            JsonNode newParam = newByKey.get(entry.getKey());
            String paramLoc = location + ".parameters." + entry.getKey();
            if (newParam == null) {
                boolean required = oldParam.path("required").asBoolean(false);
                changes.add(new Change(Change.Type.PARAMETER_REMOVED,
                        required ? Change.Severity.BREAKING : Change.Severity.NON_BREAKING,
                        paramLoc, "Parameter '" + entry.getKey() + "' was removed"));
            } else {
                boolean oldRequired = oldParam.path("required").asBoolean(false);
                boolean newRequired = newParam.path("required").asBoolean(false);
                if (!oldRequired && newRequired) {
                    changes.add(new Change(Change.Type.PARAMETER_NOW_REQUIRED, Change.Severity.BREAKING,
                            paramLoc, "Parameter '" + entry.getKey() + "' became required"));
                }
                compareSchema(paramSchema(oldParam), paramSchema(newParam), paramLoc, changes, 0);
            }
        }

        for (Map.Entry<String, JsonNode> entry : newByKey.entrySet()) {
            if (!oldByKey.containsKey(entry.getKey())) {
                boolean required = entry.getValue().path("required").asBoolean(false);
                changes.add(new Change(Change.Type.PARAMETER_ADDED,
                        required ? Change.Severity.BREAKING : Change.Severity.NON_BREAKING,
                        location + ".parameters." + entry.getKey(),
                        "Parameter '" + entry.getKey() + "' was added"
                                + (required ? " (required)" : "")));
            }
        }
    }

    private Map<String, JsonNode> indexParameters(JsonNode params) {
        Map<String, JsonNode> byKey = new java.util.LinkedHashMap<>();
        if (params.isArray()) {
            for (JsonNode param : params) {
                String name = param.path("name").asText("");
                String in = param.path("in").asText("");
                if (!name.isEmpty()) {
                    byKey.put(in.isEmpty() ? name : in + ":" + name, param);
                }
            }
        }
        return byKey;
    }

    private JsonNode paramSchema(JsonNode param) {
        JsonNode schema = param.path("schema");
        return schema.isMissingNode() ? param : schema;
    }

    // ---- Component schemas ----

    private void compareComponentSchemas(JsonNode oldSchemas, JsonNode newSchemas, List<Change> changes) {
        if (!oldSchemas.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : oldSchemas.properties()) {
            String name = entry.getKey();
            JsonNode newSchema = newSchemas.path(name);
            String location = "components.schemas." + name;
            if (newSchema.isMissingNode()) {
                changes.add(new Change(Change.Type.SCHEMA_REMOVED, Change.Severity.BREAKING,
                        location, "Schema '" + name + "' was removed"));
            } else {
                compareSchema(entry.getValue(), newSchema, location, changes, 0);
            }
        }
    }

    /**
     * Recursively compares two schema nodes for type changes, enum changes,
     * newly-required properties, and nested property type changes.
     */
    private void compareSchema(JsonNode oldSchema, JsonNode newSchema, String location,
                               List<Change> changes, int depth) {
        if (depth > MAX_SCHEMA_DEPTH || oldSchema == null || newSchema == null
                || !oldSchema.isObject() || !newSchema.isObject()) {
            return;
        }

        compareType(oldSchema, newSchema, location, changes);
        compareEnum(oldSchema, newSchema, location, changes);
        compareRequired(oldSchema, newSchema, location, changes);

        JsonNode oldProps = oldSchema.path("properties");
        JsonNode newProps = newSchema.path("properties");
        if (oldProps.isObject() && newProps.isObject()) {
            for (Map.Entry<String, JsonNode> entry : oldProps.properties()) {
                JsonNode newProp = newProps.path(entry.getKey());
                if (newProp.isObject()) {
                    compareSchema(entry.getValue(), newProp,
                            location + ".properties." + entry.getKey(), changes, depth + 1);
                }
            }
        }
    }

    private void compareType(JsonNode oldSchema, JsonNode newSchema, String location, List<Change> changes) {
        String oldType = typeOf(oldSchema);
        String newType = typeOf(newSchema);
        if (oldType != null && newType != null && !oldType.equals(newType)) {
            changes.add(new Change(Change.Type.TYPE_CHANGED, Change.Severity.BREAKING,
                    location, "Type changed from '" + oldType + "' to '" + newType + "'"));
        }
    }

    private String typeOf(JsonNode schema) {
        if (schema.hasNonNull("$ref")) {
            return schema.get("$ref").asText();
        }
        if (schema.hasNonNull("type")) {
            return schema.get("type").asText();
        }
        return null;
    }

    private void compareEnum(JsonNode oldSchema, JsonNode newSchema, String location, List<Change> changes) {
        JsonNode oldEnum = oldSchema.path("enum");
        if (!oldEnum.isArray()) {
            return;
        }
        Set<String> oldValues = enumValues(oldEnum);
        Set<String> newValues = enumValues(newSchema.path("enum"));
        for (String value : oldValues) {
            if (!newValues.contains(value)) {
                changes.add(new Change(Change.Type.ENUM_VALUE_REMOVED, Change.Severity.BREAKING,
                        location, "Enum value '" + value + "' was removed"));
            }
        }
        for (String value : newValues) {
            if (!oldValues.contains(value)) {
                changes.add(new Change(Change.Type.ENUM_VALUE_ADDED, Change.Severity.NON_BREAKING,
                        location, "Enum value '" + value + "' was added"));
            }
        }
    }

    private Set<String> enumValues(JsonNode enumNode) {
        Set<String> values = new LinkedHashSet<>();
        if (enumNode.isArray()) {
            for (JsonNode value : enumNode) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private void compareRequired(JsonNode oldSchema, JsonNode newSchema, String location, List<Change> changes) {
        Set<String> oldRequired = enumValues(oldSchema.path("required"));
        Set<String> newRequired = enumValues(newSchema.path("required"));
        for (String field : newRequired) {
            if (!oldRequired.contains(field)) {
                changes.add(new Change(Change.Type.PROPERTY_NOW_REQUIRED, Change.Severity.BREAKING,
                        location + ".required." + field,
                        "Field '" + field + "' became required"));
            }
        }
    }

    // ---- CLI helper ----

    /**
     * Renders a report as a human-readable, line-oriented string suitable for CI logs.
     *
     * @param report the report to render
     * @return the rendered text
     */
    public static String render(SpecDiffReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("OpenAPI breaking-change report\n");
        sb.append("==============================\n");
        sb.append(report.summary()).append('\n');
        for (Change change : report.changes()) {
            sb.append("  [").append(change.severity()).append("] ")
              .append(change.type()).append(" @ ").append(change.location())
              .append(" - ").append(change.description()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Command-line entry point usable as a CI gate: {@code <old-spec-file> <new-spec-file>}.
     * Exits with status {@code 1} when breaking changes are detected, {@code 0} otherwise.
     *
     * @param args old spec path, new spec path
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: OpenApiDiffService <old-spec-file> <new-spec-file>");
            System.exit(2);
            return;
        }
        try {
            OpenApiDiffService service = new OpenApiDiffService();
            String oldSpec = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            String newSpec = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
            SpecDiffReport report = service.diff(oldSpec, newSpec);
            System.out.println(render(report));
            System.exit(report.hasBreakingChanges() ? 1 : 0);
        } catch (Exception e) {
            System.err.println("Failed to diff specs: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Thrown when an OpenAPI spec cannot be parsed.
     */
    public static class SpecParseException extends RuntimeException {
        public SpecParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
