package com.adhar.kit.maven.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenApiLinter}: each rule of the API contract on its own.
 */
class OpenApiLinterTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");

    private final OpenApiLinter linter = new OpenApiLinter();

    /** A minimal spec that follows the contract; tests break one rule at a time. */
    private static final String VALID = """
            openapi: 3.0.3
            info: {title: Orders, version: 1.0.0}
            paths:
              /api/v1/orders:
                get:
                  operationId: listOrders
                  tags: [orders]
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {type: array, items: {$ref: '#/components/schemas/Order'}}
                    '500':
                      description: failure
                      content:
                        application/problem+json:
                          schema: {$ref: '#/components/schemas/Problem'}
            components:
              schemas:
                Order: {type: object, properties: {id: {type: string}}}
                Problem: {type: object, properties: {title: {type: string}}}
            """;

    @Test
    void theReferenceSpecFollowsTheContract() {
        assertThat(linter.lint(PETS, 1)).isEmpty();
    }

    @Test
    void aMinimalSpecFollowsTheContract() throws Exception {
        assertThat(lint(VALID, 1)).isEmpty();
    }

    @Test
    void openApi31AndSwagger2AreRejected() throws Exception {
        assertThat(rules(lint(VALID.replace("openapi: 3.0.3", "openapi: 3.1.0"), 1))).containsExactly("openapi-version");
        assertThat(lint(VALID.replace("openapi: 3.0.3", "swagger: '2.0'"), 1))
                .anySatisfy(f -> assertThat(f.message()).contains("Swagger 2.0"));
    }

    @Test
    void infoVersionMustBeSemanticAndMatchTheMajor() throws Exception {
        List<LintFinding> notSemantic = lint(VALID.replace("version: 1.0.0", "version: '1.0'"), 1);
        assertThat(rules(notSemantic)).containsExactly("info-version");
        assertThat(notSemantic.get(0).message()).contains("not a semantic version");

        // Unquoted 1.0 is a YAML number, not a version string.
        assertThat(lint(VALID.replace("version: 1.0.0", "version: 1.0"), 1).get(0).message())
                .contains("not a string");

        List<LintFinding> wrongMajor = lint(VALID.replace("version: 1.0.0", "version: 2.1.0"), 1);
        assertThat(wrongMajor).singleElement()
                .satisfies(f -> assertThat(f.message()).contains("does not belong to /api/v1"));

        assertThat(lint(VALID.replace("version: 1.0.0", "version: 1.0.0-rc.1+build.7"), 1)).isEmpty();
        assertThat(rules(lint(VALID.replace(", version: 1.0.0", ""), 1))).containsExactly("info-version");
    }

    @Test
    void everyPathStartsWithTheMajorsPrefix() throws Exception {
        List<LintFinding> findings = lint(VALID.replace("/api/v1/orders", "/api/v2/orders"), 1);
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("path-prefix");
            assertThat(f.location()).isEqualTo("paths./api/v2/orders");
        });
        // /api/v10 is not under /api/v1: the prefix ends at a segment boundary.
        assertThat(rules(lint(VALID.replace("/api/v1/orders", "/api/v10/orders"), 1))).containsExactly("path-prefix");
        assertThat(lint(VALID.replace("/api/v1/orders", "/api/v1"), 1)).isEmpty();
    }

    @Test
    void serverUrlsMustNotCarryABasePath() throws Exception {
        String withServers = VALID.replace("paths:", """
                servers:
                  - url: https://orders.example.com
                  - url: /
                  - url: '{scheme}://{host}/api/v1'
                  - url: /orders
                paths:""");
        List<LintFinding> findings = lint(withServers, 1);
        assertThat(findings).extracting(LintFinding::location).containsExactly("servers[2].url", "servers[3].url");
        assertThat(rules(findings)).containsOnly("servers");
    }

    @Test
    void everyOperationNeedsAUniqueOperationIdAndATag() throws Exception {
        assertThat(lint(VALID.replace("operationId: listOrders", "summary: list"), 1))
                .singleElement().satisfies(f -> assertThat(f.rule()).isEqualTo("operation-id"));
        assertThat(rules(lint(VALID.replace("tags: [orders]", "tags: []"), 1))).containsExactly("operation-tags");

        String twice = VALID.replace("components:", """
                  /api/v1/orders/{id}:
                    get:
                      operationId: listOrders
                      tags: [orders]
                      parameters: [{name: id, in: path, required: true, schema: {type: string}}]
                      responses:
                        '200': {description: ok}
                components:""");
        assertThat(lint(twice, 1)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("operation-id");
            assertThat(f.message()).contains("repeats operationId 'listOrders' of GET /api/v1/orders");
        });
    }

    @Test
    void errorResponsesMustBeProblemJson() throws Exception {
        List<LintFinding> plainJson = lint(VALID.replace("application/problem+json", "application/json"), 1);
        assertThat(plainJson).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("problem-json");
            assertThat(f.location()).isEqualTo("GET /api/v1/orders responses.500");
        });

        String noContent = VALID.replace("""
                        '500':
                          description: failure
                          content:
                            application/problem+json:
                              schema: {$ref: '#/components/schemas/Problem'}
                """, """
                        '404': {description: missing}
                        4XX: {description: client error}
                        default: {description: anything else}
                """);
        assertThat(lint(noContent, 1)).extracting(LintFinding::location).containsExactly(
                "GET /api/v1/orders responses.404",
                "GET /api/v1/orders responses.4XX",
                "GET /api/v1/orders responses.default");

        String noResponses = VALID.replace("      responses:\n        '200':",
                "      responses: {}\n      x-responses:\n        '200':");
        assertThat(rules(lint(noResponses, 1))).containsExactly("responses");
    }

    @Test
    void referencesMustStayInsideTheDocumentAndResolve() throws Exception {
        List<LintFinding> external = lint(VALID.replace("'#/components/schemas/Order'", "'./order.yaml'"), 1);
        assertThat(external).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("external-ref");
            assertThat(f.location()).endsWith("schema.items.$ref");
        });

        assertThat(rules(lint(VALID.replace("'#/components/schemas/Order'", "'#/components/schemas/Missing'"), 1)))
                .containsExactly("unresolved-ref");

        // A '+' in a pointer is a literal character, not an encoded space.
        String plusInPointer = VALID.replace("components:", """
                  /api/v1/receipts:
                    get:
                      operationId: listReceipts
                      tags: [orders]
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {$ref: '#/paths/~1api~1v1~1orders/get/responses/500/content/application~1problem+json/schema'}
                        '500': {$ref: '#/components/responses/Failure'}
                components:
                  responses:
                    Failure:
                      description: failure
                      content:
                        application/problem+json:
                          schema: {$ref: '#/components/schemas/Problem'}""");
        assertThat(lint(plusInPointer, 1)).isEmpty();
    }

    @Test
    void inlineBodySchemasAreAWarningOnly() throws Exception {
        List<LintFinding> findings = lint(VALID.replace("items: {$ref: '#/components/schemas/Order'}",
                "items: {type: object, properties: {id: {type: string}}}"), 1);
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(LintFinding.Severity.WARNING);
            assertThat(f.rule()).isEqualTo("inline-schema");
            assertThat(f.isError()).isFalse();
        });
    }

    @Test
    void aSpecWithoutOperationsIsAWarning() throws Exception {
        String empty = """
                openapi: 3.0.3
                info: {title: Nothing, version: 3.0.0}
                paths: {}
                """;
        assertThat(lint(empty, 3)).singleElement().satisfies(f -> {
            assertThat(f.rule()).isEqualTo("paths");
            assertThat(f.severity()).isEqualTo(LintFinding.Severity.WARNING);
        });
    }

    @Test
    void unreadableAndDuplicatedDocumentsAreParseErrors(@TempDir Path dir) throws Exception {
        Path broken = Files.writeString(dir.resolve("broken.yaml"), "openapi: [3.0.3\n");
        assertThat(linter.lint(broken, 1)).singleElement()
                .satisfies(f -> assertThat(f.rule()).isEqualTo("parse"));

        Path duplicated = Files.writeString(dir.resolve("duplicated.yaml"),
                VALID + "paths:\n  /api/v1/again: {}\n");
        assertThat(linter.lint(duplicated, 1)).singleElement()
                .satisfies(f -> assertThat(f.message()).containsIgnoringCase("duplicate"));

        Path scalar = Files.writeString(dir.resolve("scalar.yaml"), "just text\n");
        assertThat(rules(linter.lint(scalar, 1))).containsExactly("parse");
    }

    @Test
    void findingsRenderWithLocationAndRule() {
        LintFinding finding = new LintFinding(LintFinding.Severity.ERROR, "path-prefix", "paths./x", "is wrong");
        assertThat(finding).hasToString("paths./x: is wrong [path-prefix]");
    }

    private List<LintFinding> lint(String yaml, int major) throws Exception {
        JsonNode document = new YAMLMapper().readTree(yaml);
        return linter.lint(document, major);
    }

    private static List<String> rules(List<LintFinding> findings) {
        return findings.stream().map(LintFinding::rule).toList();
    }
}
