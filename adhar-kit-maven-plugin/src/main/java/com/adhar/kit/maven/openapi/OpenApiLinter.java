package com.adhar.kit.maven.openapi;

import com.adhar.kit.maven.openapi.LintFinding.Severity;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Checks an {@code api/v<major>/openapi.yaml} against the platform's API contract
 * before any code is generated from it.
 *
 * <p>Errors (they fail the build):</p>
 * <ul>
 *   <li>{@code openapi-version} - the document is OpenAPI 3.0.x; every generator in
 *       use supports 3.0, not all support 3.1</li>
 *   <li>{@code info-version} - {@code info.version} is a semantic version whose major is
 *       the directory's major</li>
 *   <li>{@code path-prefix} - every path starts with {@code /api/v<major>}</li>
 *   <li>{@code servers} - no server URL carries a base path, which would be prepended
 *       to paths that are already absolute</li>
 *   <li>{@code operation-id} / {@code operation-tags} - every operation has a unique
 *       operationId (the generated method) and a tag (the generated interface)</li>
 *   <li>{@code responses} / {@code problem-json} - every operation declares responses,
 *       and every 4xx, 5xx and default response is {@code application/problem+json}</li>
 *   <li>{@code external-ref} / {@code unresolved-ref} - every {@code $ref} points inside
 *       the document and resolves, so the shipped file is the whole contract</li>
 * </ul>
 *
 * <p>Warnings: {@code inline-schema} - request and response bodies {@code $ref} a
 * schema under {@code components.schemas} instead of declaring an object inline, which
 * the generators name after the operation; {@code paths} - the document declares no
 * operations.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class OpenApiLinter {

    /** The RFC 9457 media type every error response must use. */
    public static final String PROBLEM_JSON = "application/problem+json";

    private static final List<String> HTTP_METHODS =
            List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final Pattern OPENAPI_30 = Pattern.compile("3\\.0\\.\\d+");
    private static final Pattern ERROR_STATUS = Pattern.compile("[45](?:\\d\\d|XX)");
    private static final Pattern SUCCESS_STATUS = Pattern.compile("2(?:\\d\\d|XX)");
    // Scheme (possibly a {variable}) and authority; what remains of a server URL is its path.
    private static final Pattern SCHEME_AND_AUTHORITY = Pattern.compile("^(?:[^/:]+:)?//[^/]*");
    private static final int MAX_REF_HOPS = 16;

    /**
     * Lints a spec file.
     *
     * @param file  the spec
     * @param major the major its directory (or configuration) assigns it
     * @return every finding, errors and warnings, in document order
     */
    public List<LintFinding> lint(Path file, int major) {
        JsonNode document;
        try {
            document = ApiSpecs.read(file);
        } catch (IOException e) {
            return List.of(new LintFinding(Severity.ERROR, "parse", String.valueOf(file.getFileName()),
                    "cannot be read as YAML or JSON: " + e.getMessage()));
        }
        return lint(document, major);
    }

    /**
     * Lints a parsed spec.
     *
     * @param document the spec tree
     * @param major    the major it serves
     * @return every finding, errors and warnings, in document order
     */
    public List<LintFinding> lint(JsonNode document, int major) {
        List<LintFinding> findings = new ArrayList<>();
        checkOpenApiVersion(document, findings);
        checkInfoVersion(document, major, findings);
        checkServers(document, major, findings);
        checkPaths(document, major, findings);
        checkReferences(document, document, "", findings);
        return findings;
    }

    private void checkOpenApiVersion(JsonNode document, List<LintFinding> findings) {
        JsonNode openapi = document.get("openapi");
        if (openapi == null) {
            if (document.has("swagger")) {
                error(findings, "openapi-version", "swagger",
                        "Swagger 2.0 is not supported; convert the spec to OpenAPI 3.0.3");
            } else {
                error(findings, "openapi-version", "openapi", "is missing; declare openapi: 3.0.3");
            }
        } else if (!openapi.isTextual() || !OPENAPI_30.matcher(openapi.asText()).matches()) {
            error(findings, "openapi-version", "openapi", "is '" + openapi.asText()
                    + "'; declare 3.0.3, because not every generator in use supports OpenAPI 3.1");
        }
    }

    private void checkInfoVersion(JsonNode document, int major, List<LintFinding> findings) {
        JsonNode version = document.path("info").path("version");
        String example = major + ".0.0";
        if (version.isMissingNode() || version.isNull()) {
            error(findings, "info-version", "info.version",
                    "is missing; set it to the semantic version of this API, e.g. " + example);
        } else if (!version.isTextual()) {
            error(findings, "info-version", "info.version", "'" + version.asText()
                    + "' is not a string; quote a semantic version (MAJOR.MINOR.PATCH), e.g. \"" + example + "\"");
        } else if (!ApiSpecs.isSemanticVersion(version.asText())) {
            error(findings, "info-version", "info.version", "'" + version.asText()
                    + "' is not a semantic version (MAJOR.MINOR.PATCH), e.g. " + example);
        } else {
            OptionalInt declared = ApiSpecs.majorOf(version.asText());
            if (declared.isPresent() && declared.getAsInt() != major) {
                error(findings, "info-version", "info.version", "'" + version.asText()
                        + "' does not belong to /api/v" + major + "; its major must be " + major
                        + " (a new major is a new api/v<major>/ directory)");
            }
        }
    }

    private void checkServers(JsonNode document, int major, List<LintFinding> findings) {
        JsonNode servers = document.path("servers");
        if (!servers.isArray()) {
            return;
        }
        for (int i = 0; i < servers.size(); i++) {
            String url = servers.get(i).path("url").asText("");
            String path = SCHEME_AND_AUTHORITY.matcher(url).replaceFirst("");
            if (!path.isEmpty() && !path.equals("/")) {
                error(findings, "servers", "servers[" + i + "].url", "'" + url
                        + "' carries a base path; paths already start with /api/v" + major
                        + ", so give the host only (or '/')");
            }
        }
    }

    private void checkPaths(JsonNode document, int major, List<LintFinding> findings) {
        JsonNode paths = document.path("paths");
        if (!paths.isObject() || paths.isEmpty()) {
            warning(findings, "paths", "paths", "declares no operations");
            return;
        }
        String prefix = "/api/v" + major;
        Map<String, String> operationIds = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : paths.properties()) {
            String path = entry.getKey();
            if (!path.equals(prefix) && !path.startsWith(prefix + "/")) {
                error(findings, "path-prefix", "paths." + path, "does not start with " + prefix
                        + "/; every path of api/v" + major + "/openapi.yaml is served under " + prefix);
            }
            JsonNode item = resolve(document, entry.getValue());
            if (item == null || !item.isObject()) {
                continue;
            }
            for (String method : HTTP_METHODS) {
                JsonNode operation = item.get(method);
                if (operation != null && operation.isObject()) {
                    String where = method.toUpperCase(Locale.ROOT) + " " + path;
                    checkOperation(document, operation, where, operationIds, findings);
                }
            }
        }
    }

    private void checkOperation(JsonNode document, JsonNode operation, String where,
                                Map<String, String> operationIds, List<LintFinding> findings) {
        String operationId = operation.path("operationId").asText("").trim();
        if (operationId.isEmpty()) {
            error(findings, "operation-id", where, "has no operationId; it names the generated method");
        } else {
            String previous = operationIds.putIfAbsent(operationId, where);
            if (previous != null) {
                error(findings, "operation-id", where, "repeats operationId '" + operationId + "' of " + previous);
            }
        }

        JsonNode tags = operation.path("tags");
        if (!tags.isArray() || tags.isEmpty() || tags.get(0).asText("").isBlank()) {
            error(findings, "operation-tags", where,
                    "has no tags; the first tag names the generated interface (<Tag>Api)");
        }

        JsonNode requestBody = resolve(document, operation.path("requestBody"));
        if (requestBody != null) {
            checkInlineSchemas(requestBody.path("content"), where + " requestBody", findings);
        }

        JsonNode responses = operation.path("responses");
        if (!responses.isObject() || responses.isEmpty()) {
            error(findings, "responses", where, "declares no responses");
            return;
        }
        for (Map.Entry<String, JsonNode> entry : responses.properties()) {
            String status = entry.getKey();
            JsonNode response = resolve(document, entry.getValue());
            if (response == null) {
                continue; // reported by checkReferences
            }
            String location = where + " responses." + status;
            if (ERROR_STATUS.matcher(status).matches() || status.equals("default")) {
                if (!hasProblemJson(response.path("content"))) {
                    error(findings, "problem-json", location, "must be described as " + PROBLEM_JSON
                            + " (RFC 9457), the error format every service returns");
                }
            } else if (SUCCESS_STATUS.matcher(status).matches()) {
                checkInlineSchemas(response.path("content"), location, findings);
            }
        }
    }

    private boolean hasProblemJson(JsonNode content) {
        if (!content.isObject()) {
            return false;
        }
        for (Map.Entry<String, JsonNode> mediaType : content.properties()) {
            if (mediaType.getKey().toLowerCase(Locale.ROOT).startsWith(PROBLEM_JSON)) {
                return true;
            }
        }
        return false;
    }

    private void checkInlineSchemas(JsonNode content, String location, List<LintFinding> findings) {
        if (!content.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> mediaType : content.properties()) {
            if (!mediaType.getKey().toLowerCase(Locale.ROOT).contains("json")) {
                continue;
            }
            JsonNode schema = mediaType.getValue().path("schema");
            if (isInlineObject(schema) || isInlineObject(schema.path("items"))) {
                warning(findings, "inline-schema", location, "declares an inline object schema for "
                        + mediaType.getKey() + "; define it under components.schemas and $ref it,"
                        + " so the generated class gets a name of its own");
            }
        }
    }

    private boolean isInlineObject(JsonNode schema) {
        return schema.isObject() && !schema.has("$ref") && schema.has("properties");
    }

    private void checkReferences(JsonNode document, JsonNode node, String location, List<LintFinding> findings) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual()) {
                String target = ref.asText();
                String where = location.isEmpty() ? "$ref" : location + ".$ref";
                if (!target.startsWith("#")) {
                    error(findings, "external-ref", where, "'" + target + "' points outside this document;"
                            + " keep every definition under components, so the shipped file is the whole contract");
                } else if (pointer(document, target) == null) {
                    error(findings, "unresolved-ref", where, "'" + target + "' does not resolve");
                }
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String child = location.isEmpty() ? field.getKey() : location + "." + field.getKey();
                checkReferences(document, field.getValue(), child, findings);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkReferences(document, node.get(i), location + "[" + i + "]", findings);
            }
        }
    }

    /**
     * Follows local {@code $ref}s from {@code node}; null when one is external, does not
     * resolve, or the chain loops.
     */
    private JsonNode resolve(JsonNode document, JsonNode node) {
        JsonNode current = node;
        for (int hop = 0; hop < MAX_REF_HOPS; hop++) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            JsonNode ref = current.get("$ref");
            if (ref == null || !ref.isTextual()) {
                return current;
            }
            if (!ref.asText().startsWith("#")) {
                return null;
            }
            current = pointer(document, ref.asText());
        }
        return null;
    }

    private JsonNode pointer(JsonNode document, String ref) {
        // A fragment is percent-encoded (RFC 3986), which URLDecoder only approximates:
        // a literal '+' (application~1problem+json) must survive the decoding.
        String fragment = URLDecoder.decode(ref.substring(1).replace("+", "%2B"), StandardCharsets.UTF_8);
        if (fragment.isEmpty()) {
            return document;
        }
        try {
            JsonNode target = document.at(fragment);
            return target.isMissingNode() ? null : target;
        } catch (IllegalArgumentException notAPointer) {
            return null;
        }
    }

    private static void error(List<LintFinding> findings, String rule, String location, String message) {
        findings.add(new LintFinding(Severity.ERROR, rule, location, message));
    }

    private static void warning(List<LintFinding> findings, String rule, String location, String message) {
        findings.add(new LintFinding(Severity.WARNING, rule, location, message));
    }
}
