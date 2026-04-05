package com.adhar.kit.rewrite.recipe.helidon;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Helidon 3.x to 4.x.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MigrateToHelidon4 {

    private MigrateToHelidon4() {}

    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.helidon.MigrateToHelidon4
                displayName: Migrate to Helidon 4.x
                description: >-
                  Migrates Helidon 3.x to 4.x with Jakarta namespace, new WebServer API, and Nima virtual threads.
                recipeList:
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.inject
                      newPackageName: jakarta.inject
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.ws.rs
                      newPackageName: jakarta.ws.rs
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.json
                      newPackageName: jakarta.json
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: io.helidon.webserver.Routing
                      newPackageName: io.helidon.webserver.http.HttpRouting
                      recursive: true
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: io.helidon.webserver.ServerRequest
                      newFullyQualifiedTypeName: io.helidon.webserver.http.ServerRequest
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: io.helidon.webserver.ServerResponse
                      newFullyQualifiedTypeName: io.helidon.webserver.http.ServerResponse
                  - org.openrewrite.maven.ChangePropertyValue:
                      key: helidon.version
                      newValue: 4.2.0
                """;
    }
}
