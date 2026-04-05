package com.adhar.kit.rewrite.recipe.cross;

/**
 * Generates OpenRewrite YAML for migrating Spring Boot to Helidon MP.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class SpringToHelidon {

    private SpringToHelidon() {}

    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.cross.SpringToHelidon
                displayName: Migrate Spring Boot to Helidon MP
                description: >-
                  Converts Spring Boot patterns to Helidon MicroProfile equivalents.
                recipeList:
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Service
                      newFullyQualifiedTypeName: jakarta.enterprise.context.ApplicationScoped
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Component
                      newFullyQualifiedTypeName: jakarta.enterprise.context.ApplicationScoped
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Autowired
                      newFullyQualifiedTypeName: jakarta.inject.Inject
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Value
                      newFullyQualifiedTypeName: org.eclipse.microprofile.config.inject.ConfigProperty
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.RestController
                      newFullyQualifiedTypeName: jakarta.ws.rs.Path
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.GetMapping
                      newFullyQualifiedTypeName: jakarta.ws.rs.GET
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.PostMapping
                      newFullyQualifiedTypeName: jakarta.ws.rs.POST
                """;
    }
}
