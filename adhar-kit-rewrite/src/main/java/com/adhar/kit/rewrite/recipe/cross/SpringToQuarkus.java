package com.adhar.kit.rewrite.recipe.cross;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

import java.util.List;

/**
 * Migrates Spring Boot application patterns to Quarkus equivalents.
 *
 * <p>Covers the following conversions:</p>
 * <ul>
 *   <li>{@code @Service/@Component/@Repository} to {@code @ApplicationScoped}</li>
 *   <li>{@code @Autowired} to {@code @Inject}</li>
 *   <li>{@code @Value} to {@code @ConfigProperty}</li>
 *   <li>{@code @Scheduled} to Quarkus scheduler {@code @Scheduled}</li>
 *   <li>{@code @EventListener} to {@code @Observes}</li>
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
public class SpringToQuarkus extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot to Quarkus";
    }

    @Override
    public String getDescription() {
        return "Converts Spring Boot annotations and patterns to Quarkus CDI equivalents.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                // DI annotations
                new ChangeType("org.springframework.stereotype.Service",
                        "jakarta.enterprise.context.ApplicationScoped", true),
                new ChangeType("org.springframework.stereotype.Component",
                        "jakarta.enterprise.context.ApplicationScoped", true),
                new ChangeType("org.springframework.stereotype.Repository",
                        "jakarta.enterprise.context.ApplicationScoped", true),
                new ChangeType("org.springframework.beans.factory.annotation.Autowired",
                        "jakarta.inject.Inject", true),
                // Config
                new ChangeType("org.springframework.beans.factory.annotation.Value",
                        "org.eclipse.microprofile.config.inject.ConfigProperty", true),
                // Scheduling
                new ChangeType("org.springframework.scheduling.annotation.Scheduled",
                        "io.quarkus.scheduler.Scheduled", true),
                // Event handling
                new ChangeType("org.springframework.context.event.EventListener",
                        "jakarta.enterprise.event.Observes", true)
        );
    }

    /**
     * Returns the YAML recipe definition for Spring to Quarkus migration. Retained as a fallback
     * for consumers that place the definition in a {@code rewrite.yml} file and run it with the
     * OpenRewrite Maven plugin instead of executing this class in-process.
     *
     * @return YAML string defining the composite migration recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.cross.SpringToQuarkus
                displayName: Migrate Spring Boot to Quarkus
                description: >-
                  Converts Spring Boot annotations and patterns to Quarkus CDI equivalents.
                recipeList:
                  # DI annotations
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Service
                      newFullyQualifiedTypeName: jakarta.enterprise.context.ApplicationScoped
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Component
                      newFullyQualifiedTypeName: jakarta.enterprise.context.ApplicationScoped
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.stereotype.Repository
                      newFullyQualifiedTypeName: jakarta.enterprise.context.ApplicationScoped
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Autowired
                      newFullyQualifiedTypeName: jakarta.inject.Inject
                  # Config
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.beans.factory.annotation.Value
                      newFullyQualifiedTypeName: org.eclipse.microprofile.config.inject.ConfigProperty
                  # Scheduling
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.scheduling.annotation.Scheduled
                      newFullyQualifiedTypeName: io.quarkus.scheduler.Scheduled
                  # Event handling
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: org.springframework.context.event.EventListener
                      newFullyQualifiedTypeName: jakarta.enterprise.event.Observes
                """;
    }
}
