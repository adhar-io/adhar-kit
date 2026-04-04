package com.adhar.kit.rewrite.recipe.cross;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Spring Boot
 * application patterns to Micronaut equivalents.
 *
 * <p>Covers the following conversions:</p>
 * <ul>
 *   <li>{@code @Service/@Component} to {@code @Singleton}</li>
 *   <li>{@code @Autowired} to {@code @Inject}</li>
 *   <li>{@code @Value} to {@code @Value} (Micronaut)</li>
 *   <li>{@code @RestController} to {@code @Controller}</li>
 *   <li>{@code @RequestMapping} to Micronaut HTTP annotations</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class SpringToMicronaut {

    private SpringToMicronaut() {}

    /**
     * Returns the YAML recipe definition for Spring to Micronaut migration.
     *
     * @return YAML string defining the composite migration recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.cross.SpringToMicronaut
                displayName: Migrate Spring Boot to Micronaut
                description: >-
                  Converts Spring Boot annotations and patterns to Micronaut equivalents.
                recipeList:
                  # DI annotations
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Service
                      newFullyQualifiedTypeName: jakarta.inject.Singleton
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Component
                      newFullyQualifiedTypeName: jakarta.inject.Singleton
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Repository
                      newFullyQualifiedTypeName: jakarta.inject.Singleton
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Autowired
                      newFullyQualifiedTypeName: jakarta.inject.Inject
                  # Controller
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.RestController
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Controller
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.GetMapping
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Get
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.PostMapping
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Post
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.PutMapping
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Put
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.web.bind.annotation.DeleteMapping
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Delete
                  # Config
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Value
                      newFullyQualifiedTypeName: io.micronaut.context.annotation.Value
                """;
    }
}
