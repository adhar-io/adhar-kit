package com.adhar.kit.maven.catalog;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CatalogUpdater}.
 */
class CatalogUpdaterTest {

    private static final String CATALOG = """
            apiVersion: backstage.io/v1alpha1
            kind: Component
            metadata:
              name: orders
              # Shown in the console's catalog.
              description: Takes orders
            spec:
              type: service
              lifecycle: experimental
              owner: team-orders
              dependsOn:
                - resource:default/orders-db
            """;

    private static final CatalogUpdater.Api V1 =
            new CatalogUpdater.Api(1, "1.3.0", "Orders", "Order intake", "./api/v1/openapi.yaml");
    private static final CatalogUpdater.Api V2 =
            new CatalogUpdater.Api(2, "2.0.0", "Orders", null, "./api/v2/openapi.yaml");

    private final CatalogUpdater updater = new CatalogUpdater();

    @Test
    void declaresAnApiPerMajorAndLeavesTheRestAsWritten() {
        CatalogUpdater.Result result = updater.update(CATALOG, null, List.of(V1, V2));

        assertThat(result.changed()).isTrue();
        assertThat(result.apiNames()).containsExactly("orders-v1", "orders-v2");
        assertThat(result.text())
                .startsWith(CATALOG.substring(0, CATALOG.indexOf("spec:")))   // comment kept
                .contains("""
                          dependsOn:
                            - resource:default/orders-db
                          providesApis:
                            - orders-v1
                            - orders-v2
                        ---
                        apiVersion: backstage.io/v1alpha1
                        kind: API
                        """);

        List<Map<String, Object>> entities = entities(result.text());
        assertThat(entities).hasSize(3);
        Map<String, Object> v1 = entities.get(1);
        assertThat(path(v1, "metadata", "name")).isEqualTo("orders-v1");
        assertThat(path(v1, "metadata", "title")).isEqualTo("Orders v1");
        assertThat(path(v1, "metadata", "description")).isEqualTo("Order intake");
        assertThat(path(v1, "metadata", "annotations", CatalogUpdater.GENERATED_BY)).isEqualTo("adhar-kit-maven-plugin");
        assertThat(path(v1, "spec", "type")).isEqualTo("openapi");
        assertThat(path(v1, "spec", "owner")).isEqualTo("team-orders");
        assertThat(path(v1, "spec", "lifecycle")).isEqualTo("experimental");
        assertThat(path(v1, "spec", "definition", "$text")).isEqualTo("./api/v1/openapi.yaml");
        assertThat(path(entities.get(2), "metadata", "description")).isEqualTo("Version 2 of Orders");
    }

    @Test
    void runningAgainChangesNothing() {
        String once = updater.update(CATALOG, null, List.of(V1, V2)).text();
        CatalogUpdater.Result twice = updater.update(once, null, List.of(V1, V2));
        assertThat(twice.changed()).isFalse();
        assertThat(twice.text()).isEqualTo(once);
    }

    @Test
    void aRetiredMajorDisappearsButHandWrittenApisStay() {
        String withOwnApi = CATALOG.replace("  dependsOn:", "  providesApis: [\"api:default/orders-events\"]\n  dependsOn:")
                + "---\napiVersion: backstage.io/v1alpha1\nkind: API\nmetadata:\n  name: orders-events\nspec:\n  type: asyncapi\n";
        String both = updater.update(withOwnApi, null, List.of(V1, V2)).text();
        assertThat(both).contains("    - api:default/orders-events\n    - orders-v1\n    - orders-v2\n");

        String onlyV2 = updater.update(both, null, List.of(V2)).text();
        assertThat(onlyV2).contains("  providesApis:\n    - api:default/orders-events\n    - orders-v2\n")
                .doesNotContain("orders-v1")
                .contains("name: orders-events");
        assertThat(entities(onlyV2)).hasSize(3);
    }

    @Test
    void aComponentWithoutSpecOrWithBlockItemsAtTheKeysIndentIsHandled() {
        String noSpec = "kind: Component\nmetadata:\n  name: tiny\n";
        assertThat(updater.update(noSpec, null, List.of(V1)).text())
                .startsWith("kind: Component\nmetadata:\n  name: tiny\nspec:\n  providesApis:\n    - tiny-v1\n---\n");

        String sameIndent = "kind: Component\nmetadata:\n  name: svc\nspec:\n  providesApis:\n  - other # theirs\n  owner: me\n";
        assertThat(updater.update(sameIndent, null, List.of(V1)).text())
                .startsWith("kind: Component\nmetadata:\n  name: svc\nspec:\n  providesApis:\n    - other\n    - svc-v1\n  owner: me\n");
    }

    @Test
    void theComponentMustBeUnambiguous() {
        String two = CATALOG + "---\nkind: Component\nmetadata:\n  name: worker\nspec:\n  type: service\n";
        assertThatThrownBy(() -> updater.update(two, null, List.of(V1)))
                .hasMessageContaining("more than one Component");
        assertThat(updater.update(two, "worker", List.of(V1)).text())
                .contains("name: worker\nspec:\n  type: service\n  providesApis:\n    - worker-v1\n");
        assertThatThrownBy(() -> updater.update(CATALOG, "billing", List.of(V1)))
                .hasMessageContaining("no Component named 'billing'");
        assertThatThrownBy(() -> updater.update("kind: Group\n", null, List.of(V1)))
                .hasMessageContaining("declares no Component");
    }

    @Test
    void withoutApisNothingIsDeclared() {
        CatalogUpdater.Result result = updater.update(CATALOG, null, List.of());
        assertThat(result.changed()).isFalse();
        String withGenerated = updater.update(CATALOG, null, List.of(V1)).text();
        assertThat(updater.update(withGenerated, null, List.of()).text()).isEqualTo(CATALOG);
    }

    @Test
    void theProvidedApisEntryIsTheOnlyLineOfTheComponentThatChanges() {
        String text = updater.update(CATALOG, null, List.of(V1)).text();
        assertThat(updater.withProvidedApis(text.substring(0, text.indexOf("---")), Set.of("orders-v1"), List.of()))
                .doesNotContain("providesApis");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entities(String text) {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Object document : new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(text)) {
            entities.add((Map<String, Object>) document);
        }
        return entities;
    }

    private static Object path(Map<String, Object> entity, String... keys) {
        Object current = entity;
        for (String key : keys) {
            current = ((Map<?, ?>) current).get(key);
        }
        return current;
    }
}
