package com.adhar.kit.rewrite.recipe.quarkus;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating Quarkus applications
 * to the latest 3.21+ version.
 *
 * <p>Covers the following migration tasks:</p>
 * <ul>
 *   <li>Quarkus BOM version update to 3.21+</li>
 *   <li>Deprecated extension renames (e.g., resteasy-reactive to rest)</li>
 *   <li>Config property renames (quarkus.http to quarkus.rest)</li>
 *   <li>CDI scope annotation updates</li>
 *   <li>javax.* to jakarta.* namespace migration</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MigrateToQuarkusLatest {

    private MigrateToQuarkusLatest() {}

    /**
     * Returns the YAML recipe definition for Quarkus latest migration.
     *
     * @return YAML string defining the composite migration recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.quarkus.MigrateToQuarkusLatest
                displayName: Migrate to Quarkus 3.21+
                description: >-
                  Migrates Quarkus applications to 3.21+ with extension renames,
                  config property updates, and Jakarta EE compliance.
                recipeList:
                  # Extension renames
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: io.quarkus
                      artifactId: quarkus-resteasy-reactive
                      newArtifactId: quarkus-rest
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: io.quarkus
                      artifactId: quarkus-resteasy-reactive-jackson
                      newArtifactId: quarkus-rest-jackson
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: io.quarkus
                      artifactId: quarkus-smallrye-reactive-messaging
                      newArtifactId: quarkus-messaging
                  # Config property renames
                  - org.openrewrite.properties.ChangePropertyKey:
                      oldPropertyKey: quarkus.http.port
                      newPropertyKey: quarkus.rest.port
                  - org.openrewrite.properties.ChangePropertyKey:
                      oldPropertyKey: quarkus.http.host
                      newPropertyKey: quarkus.rest.host
                  # Jakarta namespace migration
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.inject
                      newPackageName: jakarta.inject
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.enterprise
                      newPackageName: jakarta.enterprise
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: javax.ws.rs
                      newPackageName: jakarta.ws.rs
                      recursive: true
                """;
    }
}
