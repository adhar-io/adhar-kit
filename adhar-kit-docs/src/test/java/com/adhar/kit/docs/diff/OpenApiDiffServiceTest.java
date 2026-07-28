package com.adhar.kit.docs.diff;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiDiffServiceTest {

    private final OpenApiDiffService service = new OpenApiDiffService();

    // ---- Representative old-vs-new spec fixtures ----

    private static final String OLD_SPEC = """
            {
              "openapi": "3.0.1",
              "info": {"title": "Orders API", "version": "1.0.0"},
              "paths": {
                "/orders": {
                  "get": {
                    "parameters": [
                      {"name": "status", "in": "query", "required": false,
                       "schema": {"type": "string", "enum": ["OPEN", "CLOSED", "CANCELLED"]}},
                      {"name": "limit", "in": "query", "required": false,
                       "schema": {"type": "integer"}}
                    ]
                  },
                  "post": {}
                },
                "/legacy": {
                  "get": {}
                }
              },
              "components": {
                "schemas": {
                  "Order": {
                    "type": "object",
                    "required": ["id"],
                    "properties": {
                      "id": {"type": "string"},
                      "total": {"type": "number"},
                      "notes": {"type": "string"}
                    }
                  },
                  "Legacy": {"type": "object"}
                }
              }
            }
            """;

    private static final String NEW_SPEC = """
            {
              "openapi": "3.0.1",
              "info": {"title": "Orders API", "version": "2.0.0"},
              "paths": {
                "/orders": {
                  "get": {
                    "parameters": [
                      {"name": "status", "in": "query", "required": false,
                       "schema": {"type": "string", "enum": ["OPEN", "CLOSED"]}},
                      {"name": "tenant", "in": "query", "required": true,
                       "schema": {"type": "string"}}
                    ]
                  }
                },
                "/orders/{id}": {
                  "get": {}
                }
              },
              "components": {
                "schemas": {
                  "Order": {
                    "type": "object",
                    "required": ["id", "total"],
                    "properties": {
                      "id": {"type": "string"},
                      "total": {"type": "string"},
                      "notes": {"type": "string"}
                    }
                  }
                }
              }
            }
            """;

    private SpecDiffReport fullReport() {
        return service.diff(OLD_SPEC, NEW_SPEC);
    }

    private boolean has(SpecDiffReport report, Change.Type type, Change.Severity severity) {
        return report.changes().stream()
                .anyMatch(c -> c.type() == type && c.severity() == severity);
    }

    @Test
    void detectsRemovedPathAsBreaking() {
        assertThat(has(fullReport(), Change.Type.PATH_REMOVED, Change.Severity.BREAKING)).isTrue();
    }

    @Test
    void detectsAddedPathAsNonBreaking() {
        assertThat(has(fullReport(), Change.Type.PATH_ADDED, Change.Severity.NON_BREAKING)).isTrue();
    }

    @Test
    void detectsRemovedOperationAsBreaking() {
        // POST /orders was removed
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.OPERATION_REMOVED
                        && c.severity() == Change.Severity.BREAKING
                        && c.location().contains("post"));
    }

    @Test
    void detectsAddedRequiredParameterAsBreaking() {
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.PARAMETER_ADDED
                        && c.severity() == Change.Severity.BREAKING
                        && c.location().contains("tenant"));
    }

    @Test
    void detectsRemovedOptionalParameterAsNonBreaking() {
        // "limit" (optional) was removed
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.PARAMETER_REMOVED
                        && c.severity() == Change.Severity.NON_BREAKING
                        && c.location().contains("limit"));
    }

    @Test
    void detectsRemovedEnumValueAsBreaking() {
        // CANCELLED removed from status enum
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.ENUM_VALUE_REMOVED
                        && c.severity() == Change.Severity.BREAKING
                        && c.description().contains("CANCELLED"));
    }

    @Test
    void detectsChangedPropertyTypeAsBreaking() {
        // Order.total number -> string
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.TYPE_CHANGED
                        && c.severity() == Change.Severity.BREAKING
                        && c.location().contains("total"));
    }

    @Test
    void detectsNewlyRequiredFieldAsBreaking() {
        // Order.required gains "total"
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.PROPERTY_NOW_REQUIRED
                        && c.severity() == Change.Severity.BREAKING
                        && c.location().contains("total"));
    }

    @Test
    void detectsRemovedSchemaAsBreaking() {
        assertThat(fullReport().changes()).anyMatch(c ->
                c.type() == Change.Type.SCHEMA_REMOVED
                        && c.severity() == Change.Severity.BREAKING
                        && c.location().contains("Legacy"));
    }

    @Test
    void reportFlagsBreakingChangesPresent() {
        SpecDiffReport report = fullReport();
        assertThat(report.hasBreakingChanges()).isTrue();
        assertThat(report.breakingCount()).isGreaterThan(0);
        assertThat(report.breakingChanges()).allMatch(Change::isBreaking);
        assertThat(report.nonBreakingChanges()).noneMatch(Change::isBreaking);
        assertThat(report.breakingCount() + report.nonBreakingCount())
                .isEqualTo(report.changes().size());
        assertThat(report.isEmpty()).isFalse();
        assertThat(report.summary()).contains("breaking");
    }

    @Test
    void identicalSpecsProduceNoChanges() {
        SpecDiffReport report = service.diff(OLD_SPEC, OLD_SPEC);
        assertThat(report.isEmpty()).isTrue();
        assertThat(report.hasBreakingChanges()).isFalse();
        assertThat(report.changes()).isEmpty();
    }

    @Test
    void detectsAddedEnumValueAsNonBreaking() {
        String base = """
                {"paths":{},"components":{"schemas":{"S":{"type":"string","enum":["A"]}}}}
                """;
        String updated = """
                {"paths":{},"components":{"schemas":{"S":{"type":"string","enum":["A","B"]}}}}
                """;
        SpecDiffReport report = service.diff(base, updated);
        assertThat(report.changes()).anyMatch(c ->
                c.type() == Change.Type.ENUM_VALUE_ADDED
                        && c.severity() == Change.Severity.NON_BREAKING);
        assertThat(report.hasBreakingChanges()).isFalse();
    }

    @Test
    void detectsAddedOperationAsNonBreaking() {
        String base = "{\"paths\":{\"/a\":{\"get\":{}}}}";
        String updated = "{\"paths\":{\"/a\":{\"get\":{},\"post\":{}}}}";
        SpecDiffReport report = service.diff(base, updated);
        assertThat(has(report, Change.Type.OPERATION_ADDED, Change.Severity.NON_BREAKING)).isTrue();
    }

    @Test
    void detectsParameterBecameRequired() {
        String base = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":["
                + "{\"name\":\"q\",\"in\":\"query\",\"required\":false,\"schema\":{\"type\":\"string\"}}]}}}}";
        String updated = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":["
                + "{\"name\":\"q\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\"}}]}}}}";
        SpecDiffReport report = service.diff(base, updated);
        assertThat(has(report, Change.Type.PARAMETER_NOW_REQUIRED, Change.Severity.BREAKING)).isTrue();
    }

    @Test
    void detectsChangedParameterType() {
        String base = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":["
                + "{\"name\":\"q\",\"in\":\"query\",\"schema\":{\"type\":\"string\"}}]}}}}";
        String updated = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":["
                + "{\"name\":\"q\",\"in\":\"query\",\"schema\":{\"type\":\"integer\"}}]}}}}";
        SpecDiffReport report = service.diff(base, updated);
        assertThat(report.changes()).anyMatch(c ->
                c.type() == Change.Type.TYPE_CHANGED
                        && c.location().contains("q"));
    }

    @Test
    void parsesYamlSpecs() {
        String oldYaml = """
                openapi: 3.0.1
                paths:
                  /a:
                    get: {}
                """;
        String newYaml = """
                openapi: 3.0.1
                paths: {}
                """;
        SpecDiffReport report = service.diff(oldYaml, newYaml);
        assertThat(has(report, Change.Type.PATH_REMOVED, Change.Severity.BREAKING)).isTrue();
    }

    @Test
    void comparesOpenApiModels() {
        OpenAPI oldApi = new OpenAPI().info(new Info().title("t").version("1"))
                .paths(new Paths().addPathItem("/a", new PathItem().get(new Operation())));
        OpenAPI newApi = new OpenAPI().info(new Info().title("t").version("2"))
                .paths(new Paths());
        SpecDiffReport report = service.diff(oldApi, newApi);
        assertThat(has(report, Change.Type.PATH_REMOVED, Change.Severity.BREAKING)).isTrue();
    }

    @Test
    void detectsRemovedRequiredParameterAsBreaking() {
        String base = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":["
                + "{\"name\":\"q\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\"}}]}}}}";
        String updated = "{\"paths\":{\"/a\":{\"get\":{\"parameters\":[]}}}}";
        SpecDiffReport report = service.diff(base, updated);
        assertThat(has(report, Change.Type.PARAMETER_REMOVED, Change.Severity.BREAKING)).isTrue();
    }

    @Test
    void renderProducesReadableReport() {
        String rendered = OpenApiDiffService.render(fullReport());
        assertThat(rendered).contains("OpenAPI breaking-change report");
        assertThat(rendered).contains("BREAKING");
    }

    @Test
    void invalidSpecThrowsParseException() {
        assertThatThrownBy(() -> service.diff("{not valid json", "{}"))
                .isInstanceOf(OpenApiDiffService.SpecParseException.class);
    }

    @Test
    void nullArgumentsRejected() {
        assertThatThrownBy(() -> service.diff((String) null, "{}"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void changeRecordExposesConvenienceAccessors() {
        Change change = new Change(Change.Type.PATH_REMOVED, Change.Severity.BREAKING, "loc", "desc");
        assertThat(change.isBreaking()).isTrue();
        assertThat(change.type()).isEqualTo(Change.Type.PATH_REMOVED);
        assertThat(List.of(Change.Severity.values())).contains(Change.Severity.NON_BREAKING);
        assertThat(Change.Type.valueOf("TYPE_CHANGED")).isEqualTo(Change.Type.TYPE_CHANGED);
    }
}
