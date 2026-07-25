package com.adhar.kit.rewrite.recipe.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.Map;

/**
 * Migrates applications from Spring Boot 3.x to Spring Boot 4.x.
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
 * <p>This is a real, executable {@link Recipe}: the starter renames are performed by a dedicated
 * {@link XmlIsoVisitor}-backed sub-recipe operating directly on {@code pom.xml} documents (so it
 * works offline, without Maven model resolution), and the package moves delegate to OpenRewrite's
 * battle-tested {@link ChangePackage} recipe. The historical {@link #getYamlDefinition()} YAML
 * generator is retained as a fallback for consumers that drive the migration through a
 * {@code rewrite.yml} file and the OpenRewrite Maven plugin.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class MigrateToSpringBoot4 extends Recipe {

    private static final String SPRING_BOOT_GROUP_ID = "org.springframework.boot";

    /** Spring Boot 3.x starter artifactId -> Spring Boot 4.x starter artifactId. */
    static final Map<String, String> STARTER_RENAMES = Map.of(
            "spring-boot-starter-web", "spring-boot-starter-webmvc",
            "spring-boot-starter-aop", "spring-boot-starter-aspectj",
            "spring-boot-starter-oauth2-resource-server", "spring-boot-starter-security-oauth2-resource-server"
    );

    @Override
    public String getDisplayName() {
        return "Migrate to Spring Boot 4";
    }

    @Override
    public String getDescription() {
        return "Migrates Spring Boot 3.x applications to Spring Boot 4.x including starter "
                + "renames, package moves, and updated APIs.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new RenameSpringBootStarters(),
                new ChangePackage(
                        "org.springframework.boot.actuate.health",
                        "org.springframework.boot.health.contributor",
                        true),
                new ChangePackage(
                        "org.springframework.boot.test.autoconfigure.web.servlet",
                        "org.springframework.boot.webmvc.test.autoconfigure",
                        true)
        );
    }

    /**
     * Visitor-backed sub-recipe that renames Spring Boot 3.x starter {@code <artifactId>}s to
     * their Spring Boot 4.x equivalents inside {@code pom.xml} documents. Operates on the raw XML
     * LST so it does not require the (renamed) artifacts to be resolvable while the recipe runs.
     */
    public static class RenameSpringBootStarters extends Recipe {

        @Override
        public String getDisplayName() {
            return "Rename Spring Boot starters for Spring Boot 4";
        }

        @Override
        public String getDescription() {
            return "Renames `spring-boot-starter-web`, `spring-boot-starter-aop` and "
                    + "`spring-boot-starter-oauth2-resource-server` to their Spring Boot 4 names.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<ExecutionContext>() {

                @Override
                public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                    if (!document.getSourcePath().getFileName().toString().equals("pom.xml")) {
                        return document;
                    }
                    return super.visitDocument(document, ctx);
                }

                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag t = super.visitTag(tag, ctx);
                    if (!"dependency".equals(t.getName()) && !"exclusion".equals(t.getName())) {
                        return t;
                    }
                    boolean springBootGroup = t.getChildValue("groupId")
                            .filter(SPRING_BOOT_GROUP_ID::equals)
                            .isPresent();
                    if (!springBootGroup) {
                        return t;
                    }
                    t.getChild("artifactId").ifPresent(artifactId ->
                            artifactId.getValue()
                                    .map(STARTER_RENAMES::get)
                                    .ifPresent(newArtifactId ->
                                            doAfterVisit(new ChangeTagValueVisitor<>(artifactId, newArtifactId))));
                    return t;
                }
            };
        }
    }

    /**
     * Returns the YAML recipe definition for Spring Boot 4 migration. Retained as a fallback for
     * consumers that place the definition in a {@code rewrite.yml} file and run it with the
     * OpenRewrite Maven plugin instead of executing this class in-process.
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
                  - org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId:
                      oldGroupId: org.springframework.boot
                      oldArtifactId: spring-boot-starter-web
                      newArtifactId: spring-boot-starter-webmvc
                  - org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId:
                      oldGroupId: org.springframework.boot
                      oldArtifactId: spring-boot-starter-aop
                      newArtifactId: spring-boot-starter-aspectj
                  - org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId:
                      oldGroupId: org.springframework.boot
                      oldArtifactId: spring-boot-starter-oauth2-resource-server
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
