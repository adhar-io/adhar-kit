package com.adhar.kit.rewrite.recipe.adhar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UseConvenienceShortcutsTest {

    private final String yaml = UseConvenienceShortcuts.getYamlDefinition();

    @Test
    void getYamlDefinition_returnsNonEmpty() {
        assertThat(yaml).isNotBlank();
    }

    @Test
    void yaml_containsCountShortcut() {
        assertThat(yaml).contains("getMetrics().increment");
        assertThat(yaml).contains("adhar.count(x)");
    }

    @Test
    void yaml_containsResilientShortcut() {
        assertThat(yaml).contains("getCircuitBreaker().execute");
        assertThat(yaml).contains("adhar.resilient(x, y)");
    }

    @Test
    void yaml_containsSaveShortcut() {
        assertThat(yaml).contains("getPersistence().save");
        assertThat(yaml).contains("adhar.save(x)");
    }

    @Test
    void yaml_containsPublishShortcut() {
        assertThat(yaml).contains("getMessaging().publish");
        assertThat(yaml).contains("adhar.publish(x, y)");
    }

    @Test
    void yaml_containsHasPermissionShortcut() {
        assertThat(yaml).contains("getSecurity().hasPermission");
        assertThat(yaml).contains("adhar.hasPermission(x)");
    }

    @Test
    void yaml_containsGetApiDocsRename() {
        assertThat(yaml).contains("newMethodName: getApiDocs");
        assertThat(yaml).contains("getDocs()");
    }

    @Test
    void yaml_containsGetUtilsRename() {
        assertThat(yaml).contains("newMethodName: getUtils");
        assertThat(yaml).contains("getCore()");
    }
}
