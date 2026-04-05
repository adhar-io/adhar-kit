package com.adhar.kit.rewrite.recipe.cross;

/**
 * Generates OpenRewrite YAML for migrating Spring Boot to Vert.x.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class SpringToVertx {

    private SpringToVertx() {}

    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.cross.SpringToVertx
                displayName: Migrate Spring Boot to Vert.x
                description: >-
                  Converts Spring Boot patterns to Vert.x reactive equivalents.
                  Note: Vert.x uses a fundamentally different programming model
                  (event-loop, reactive). This recipe handles annotation and
                  type renames but architectural changes require manual review.
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.RestController
                      newFullyQualifiedTypeName: io.vertx.ext.web.Router
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Value
                      newFullyQualifiedTypeName: io.vertx.config.ConfigRetriever
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.RequestBody
                      newFullyQualifiedTypeName: io.vertx.core.json.JsonObject
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: org.springframework.http
                      newPackageName: io.vertx.core.http
                      recursive: true
                """;
    }
}
