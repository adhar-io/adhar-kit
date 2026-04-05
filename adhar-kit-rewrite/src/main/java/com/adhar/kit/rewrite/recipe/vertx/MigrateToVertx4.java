package com.adhar.kit.rewrite.recipe.vertx;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Vert.x 3.x to 4.x.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MigrateToVertx4 {

    private MigrateToVertx4() {}

    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.vertx.MigrateToVertx4
                displayName: Migrate to Vert.x 4.x
                description: >-
                  Migrates Vert.x 3.x to 4.x with Future-based API, removed Handler callbacks, and package renames.
                recipeList:
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: io.vertx.ext.web
                      newPackageName: io.vertx.ext.web
                      recursive: true
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: io.vertx.core.json.Json
                      newFullyQualifiedTypeName: io.vertx.core.json.Json
                  - org.openrewrite.maven.ChangePropertyValue:
                      key: vertx.version
                      newValue: 4.5.13
                  - org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId:
                      oldGroupId: io.vertx
                      oldArtifactId: vertx-unit
                      newArtifactId: vertx-junit5
                """;
    }
}
