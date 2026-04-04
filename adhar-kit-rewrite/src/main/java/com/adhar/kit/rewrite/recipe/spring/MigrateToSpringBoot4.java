package com.adhar.kit.rewrite.recipe.spring;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating applications
 * from Spring Boot 3.x to Spring Boot 4.x.
 *
 * <p>Covers the following migration tasks:</p>
 * <ul>
 *   <li>Starter renames: {@code spring-boot-starter-web} to {@code spring-boot-starter-webmvc}</li>
 *   <li>Starter renames: {@code spring-boot-starter-aop} to {@code spring-boot-starter-aspectj}</li>
 *   <li>Starter renames: {@code spring-boot-starter-oauth2-resource-server} to
 *       {@code spring-boot-starter-security-oauth2-resource-server}</li>
 *   <li>Package moves: {@code org.springframework.boot.actuate.health} to
 *       {@code org.springframework.boot.health.contributor}</li>
 *   <li>Package moves: {@code org.springframework.boot.test.autoconfigure.web.servlet} to
 *       {@code org.springframework.boot.webmvc.test.autoconfigure}</li>
 * </ul>
 *
 * <p>The generated YAML is intended to be placed in a {@code rewrite.yml} file and executed
 * by the OpenRewrite Maven plugin.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class MigrateToSpringBoot4 {

    private MigrateToSpringBoot4() {}

    /**
     * Returns the YAML recipe definition for Spring Boot 4 migration.
     *
     * @return YAML string defining the composite migration recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.spring.MigrateToSpringBoot4
                displayName: Migrate to Spring Boot 4
                description: >-
                  Migrates Spring Boot 3.x applications to Spring Boot 4.x including
                  starter renames, package moves, and updated APIs.
                recipeList:
                  # ---------------------------------------------------------------
                  # Starter artifact renames in pom.xml / build.gradle
                  # ---------------------------------------------------------------
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: org.springframework.boot
                      artifactId: spring-boot-starter-web
                      newArtifactId: spring-boot-starter-webmvc
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: org.springframework.boot
                      artifactId: spring-boot-starter-aop
                      newArtifactId: spring-boot-starter-aspectj
                  - org.openrewrite.maven.ChangeDependencyArtifactId:
                      groupId: org.springframework.boot
                      artifactId: spring-boot-starter-oauth2-resource-server
                      newArtifactId: spring-boot-starter-security-oauth2-resource-server
                  # ---------------------------------------------------------------
                  # Package renames in Java source files
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: org.springframework.boot.actuate.health
                      newPackageName: org.springframework.boot.health.contributor
                      recursive: true
                  - org.openrewrite.java.ChangePackage:
                      oldPackageName: org.springframework.boot.test.autoconfigure.web.servlet
                      newPackageName: org.springframework.boot.webmvc.test.autoconfigure
                      recursive: true
                """;
    }
}
