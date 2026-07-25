package com.adhar.kit.rewrite.recipe.cross;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

import java.util.List;

/**
 * Migrates Spring Boot application patterns to Micronaut equivalents.
 *
 * <p>Covers the following conversions:</p>
 * <ul>
 *   <li>{@code @Service/@Component/@Repository} to {@code @Singleton}</li>
 *   <li>{@code @Autowired} to {@code @Inject}</li>
 *   <li>{@code @Value} to {@code @Value} (Micronaut)</li>
 *   <li>{@code @RestController} to {@code @Controller}</li>
 *   <li>{@code @GetMapping/@PostMapping/...} to Micronaut HTTP annotations</li>
 * </ul>
 *
 * <p>This is a real, executable {@link Recipe}: each conversion delegates to OpenRewrite's
 * battle-tested {@link ChangeType} recipe via {@link #getRecipeList()}, so the migration runs
 * in-process against the AST. The historical {@link #getYamlDefinition()} YAML generator is
 * retained as a fallback for consumers that drive the migration through {@code rewrite.yml}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SpringToMicronaut extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot to Micronaut";
    }

    @Override
    public String getDescription() {
        return "Converts Spring Boot annotations and patterns to Micronaut equivalents.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                // DI annotations
                new ChangeType("org.springframework.stereotype.Service",
                        "jakarta.inject.Singleton", true),
                new ChangeType("org.springframework.stereotype.Component",
                        "jakarta.inject.Singleton", true),
                new ChangeType("org.springframework.stereotype.Repository",
                        "jakarta.inject.Singleton", true),
                new ChangeType("org.springframework.beans.factory.annotation.Autowired",
                        "jakarta.inject.Inject", true),
                // Controller
                new ChangeType("org.springframework.web.bind.annotation.RestController",
                        "io.micronaut.http.annotation.Controller", true),
                new ChangeType("org.springframework.web.bind.annotation.GetMapping",
                        "io.micronaut.http.annotation.Get", true),
                new ChangeType("org.springframework.web.bind.annotation.PostMapping",
                        "io.micronaut.http.annotation.Post", true),
                new ChangeType("org.springframework.web.bind.annotation.PutMapping",
                        "io.micronaut.http.annotation.Put", true),
                new ChangeType("org.springframework.web.bind.annotation.DeleteMapping",
                        "io.micronaut.http.annotation.Delete", true),
                // Config
                new ChangeType("org.springframework.beans.factory.annotation.Value",
                        "io.micronaut.context.annotation.Value", true)
        );
    }

    /**
     * Returns the YAML recipe definition for Spring to Micronaut migration. Retained as a
     * fallback for consumers that place the definition in a {@code rewrite.yml} file and run it
     * with the OpenRewrite Maven plugin instead of executing this class in-process.
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
