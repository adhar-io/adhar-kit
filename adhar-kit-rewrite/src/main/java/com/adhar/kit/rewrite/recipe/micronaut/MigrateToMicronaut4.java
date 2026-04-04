package com.adhar.kit.rewrite.recipe.micronaut;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Micronaut 3.x
 * applications to Micronaut 4.x.
 *
 * <p>Covers the following migration tasks:</p>
 * <ul>
 *   <li>javax.inject to jakarta.inject namespace migration</li>
 *   <li>Micronaut annotation processor updates</li>
 *   <li>Removed/renamed API replacements</li>
 *   <li>Configuration property changes</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MigrateToMicronaut4 {

    private MigrateToMicronaut4() {}

    /**
     * Returns the YAML recipe definition for Micronaut 4 migration.
     *
     * @return YAML string defining the composite migration recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.micronaut.MigrateToMicronaut4
                displayName: Migrate to Micronaut 4.x
                description: >-
                  Migrates Micronaut 3.x applications to 4.x with Jakarta namespace,
                  annotation processor updates, and API changes.
                recipeList:
                  # Jakarta namespace
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.inject
                      newPackageName: jakarta.inject
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.annotation
                      newPackageName: jakarta.annotation
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.validation
                      newPackageName: jakarta.validation
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.persistence
                      newPackageName: jakarta.persistence
                      recursive: true
                  # Micronaut-specific renames
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: io.micronaut.http.annotation.Controller
                      newFullyQualifiedTypeName: io.micronaut.http.annotation.Controller
                  - org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId:
                      oldGroupId: io.micronaut
                      oldArtifactId: micronaut-inject-java
                      newArtifactId: micronaut-inject
                  # BOM version update
                  - org.openrewrite.maven.ChangePropertyValue:
                      key: micronaut.version
                      newValue: 4.8.0
                """;
    }
}
