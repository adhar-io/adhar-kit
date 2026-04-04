package com.adhar.kit.rewrite.recipe.cross;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Spring Boot
 * application patterns to Quarkus equivalents.
 *
 * <p>Covers the following conversions:</p>
 * <ul>
 *   <li>{@code @Service/@Component} to {@code @ApplicationScoped}</li>
 *   <li>{@code @Autowired} to {@code @Inject}</li>
 *   <li>{@code @Value} to {@code @ConfigProperty}</li>
 *   <li>{@code @RestController} to {@code @Path} + {@code @ApplicationScoped}</li>
 *   <li>{@code @RequestMapping} to JAX-RS annotations</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class SpringToQuarkus {

    private SpringToQuarkus() {}

    /**
     * Returns the YAML recipe definition for Spring to Quarkus migration.
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
