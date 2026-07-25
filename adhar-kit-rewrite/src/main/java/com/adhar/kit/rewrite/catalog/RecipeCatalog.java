package com.adhar.kit.rewrite.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalog of all available OpenRewrite recipe sets for the Adhar Kit platform.
 *
 * <p>Provides pre-configured recipe groups for common migration and modernization
 * scenarios. Each recipe set is identified by a unique key and maps to one or more
 * OpenRewrite recipe fully-qualified names.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Get all available recipe sets
 * Map<String, RecipeSet> catalog = RecipeCatalog.getAll();
 *
 * // Get recipes for a specific migration
 * RecipeSet javaUpgrade = RecipeCatalog.get("java-25");
 * List<String> recipeNames = javaUpgrade.recipeNames();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class RecipeCatalog {

    private static final Map<String, RecipeSet> CATALOG = new LinkedHashMap<>();

    static {
        // =====================================================================
        // Java Version Migrations
        // =====================================================================
        register("java-17", new RecipeSet(
                "Upgrade to Java 17",
                "Migrates codebase to Java 17 LTS with text blocks, records, sealed classes, pattern matching",
                Category.JAVA_MIGRATION,
                List.of("org.openrewrite.java.migrate.UpgradeToJava17")
        ));

        register("java-21", new RecipeSet(
                "Upgrade to Java 21",
                "Migrates codebase to Java 21 LTS with virtual threads, sequenced collections, string templates",
                Category.JAVA_MIGRATION,
                List.of("org.openrewrite.java.migrate.UpgradeToJava21")
        ));

        register("java-25", new RecipeSet(
                "Upgrade to Java 25",
                "Migrates codebase to Java 25 with latest language features and API improvements",
                Category.JAVA_MIGRATION,
                List.of("org.openrewrite.java.migrate.UpgradeToJava25")
        ));

        // =====================================================================
        // Spring Boot Migrations
        // =====================================================================
        register("spring-boot-3", new RecipeSet(
                "Upgrade to Spring Boot 3.x",
                "Migrates from Spring Boot 2.x to 3.x including Jakarta EE namespace, Spring Security 6, and removed APIs",
                Category.SPRING_MIGRATION,
                List.of("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0")
        ));

        register("spring-boot-4", new RecipeSet(
                "Upgrade to Spring Boot 4.x",
                "Migrates from Spring Boot 3.x to 4.x including starter renames (web->webmvc, aop->aspectj), package moves, and new APIs",
                Category.SPRING_MIGRATION,
                List.of(
                        "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
                        "com.adhar.kit.rewrite.recipe.spring.MigrateToSpringBoot4"
                )
        ));

        // =====================================================================
        // Testing Framework Migrations
        // =====================================================================
        register("junit-5", new RecipeSet(
                "Migrate to JUnit 5",
                "Converts JUnit 4 tests to JUnit 5 with Jupiter API, assertions, lifecycle annotations",
                Category.TESTING,
                List.of("org.openrewrite.java.testing.junit5.JUnit4to5Migration")
        ));

        register("assertj", new RecipeSet(
                "Adopt AssertJ",
                "Converts JUnit/Hamcrest assertions to fluent AssertJ assertions",
                Category.TESTING,
                List.of("org.openrewrite.java.testing.assertj.JUnitToAssertj")
        ));

        register("mockito-5", new RecipeSet(
                "Upgrade to Mockito 5",
                "Migrates Mockito 3/4 usage to Mockito 5 with inline mock maker and removed APIs",
                Category.TESTING,
                List.of("org.openrewrite.java.testing.mockito.Mockito1to5Migration")
        ));

        // =====================================================================
        // Security Hardening
        // =====================================================================
        register("security-best-practices", new RecipeSet(
                "Apply Security Best Practices",
                "Enforces secure coding patterns: input validation, output encoding, secure defaults",
                Category.SECURITY,
                List.of(
                        "org.openrewrite.java.security.OwaspA01",
                        "org.openrewrite.java.security.OwaspA02",
                        "org.openrewrite.java.security.OwaspA08"
                )
        ));

        // =====================================================================
        // Adhar Kit Specific
        // =====================================================================
        register("adhar-convenience-api", new RecipeSet(
                "Adopt Adhar Kit Convenience API",
                "Migrates verbose facade chaining to concise convenience shortcuts (e.g., adhar.getMetrics().increment() -> adhar.count())",
                Category.ADHAR_KIT,
                List.of("com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts")
        ));

        register("adhar-spring-boot-4", new RecipeSet(
                "Adhar Kit Spring Boot 4 Migration",
                "Migrates Adhar Kit applications to Spring Boot 4 with renamed starters, moved packages, and updated APIs",
                Category.ADHAR_KIT,
                List.of(
                        "com.adhar.kit.rewrite.recipe.spring.MigrateToSpringBoot4",
                        "com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts"
                )
        ));

        register("adhar-version-migration", new RecipeSet(
                "Migrate Adhar Kit Version",
                "Migrates a consumer project between Adhar Kit versions: bumps com.adhar.kit dependency versions and optionally renames the legacy com.adhar.adharkit.* packages",
                Category.ADHAR_KIT,
                List.of("com.adhar.kit.rewrite.recipe.adhar.MigrateAdharKitVersion")
        ));

        register("adhar-cloudevents", new RecipeSet(
                "Adopt CloudEvents Specification",
                "Migrates custom event classes to use AdharCloudEvent envelope for CloudEvents 1.0 compliance",
                Category.ADHAR_KIT,
                List.of("com.adhar.kit.rewrite.recipe.adhar.AdoptCloudEvents")
        ));

        register("adhar-full-modernization", new RecipeSet(
                "Full Adhar Kit Modernization",
                "Complete modernization: Java 25, Spring Boot 4, JUnit 5, convenience API, CloudEvents, security hardening",
                Category.ADHAR_KIT,
                List.of(
                        "org.openrewrite.java.migrate.UpgradeToJava25",
                        "com.adhar.kit.rewrite.recipe.spring.MigrateToSpringBoot4",
                        "org.openrewrite.java.testing.junit5.JUnit4to5Migration",
                        "com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts",
                        "com.adhar.kit.rewrite.recipe.adhar.AdoptCloudEvents"
                )
        ));

        // =====================================================================
        // Code Quality
        // =====================================================================
        register("code-cleanup", new RecipeSet(
                "Code Cleanup",
                "Removes unused imports, simplifies boolean expressions, uses diamond operator, modernizes loops",
                Category.CODE_QUALITY,
                List.of(
                        "org.openrewrite.java.RemoveUnusedImports",
                        "org.openrewrite.java.format.AutoFormat",
                        "org.openrewrite.staticanalysis.SimplifyBooleanExpression",
                        "org.openrewrite.staticanalysis.UseDiamondOperator"
                )
        ));

        register("logging-best-practices", new RecipeSet(
                "Logging Best Practices",
                "Enforces SLF4J usage, parameterized logging, no System.out/err, proper log levels",
                Category.CODE_QUALITY,
                List.of(
                        "org.openrewrite.java.logging.slf4j.Slf4jBestPractices",
                        "org.openrewrite.java.logging.SystemOutToLogging"
                )
        ));

        // =====================================================================
        // Quarkus Migrations
        // =====================================================================
        register("quarkus-3", new RecipeSet(
                "Upgrade to Quarkus 3.x",
                "Migrates from Quarkus 2.x to 3.x including Jakarta namespace, CDI changes, and config property updates",
                Category.QUARKUS_MIGRATION,
                List.of("org.openrewrite.quarkus.Quarkus1to2Migration")
        ));

        register("quarkus-latest", new RecipeSet(
                "Upgrade to Quarkus 3.21+",
                "Migrates Quarkus applications to the latest 3.21+ with updated extensions and API changes",
                Category.QUARKUS_MIGRATION,
                List.of("org.openrewrite.quarkus.Quarkus1to2Migration",
                        "com.adhar.kit.rewrite.recipe.quarkus.MigrateToQuarkusLatest")
        ));

        // =====================================================================
        // Micronaut Migrations
        // =====================================================================
        register("micronaut-4", new RecipeSet(
                "Upgrade to Micronaut 4.x",
                "Migrates from Micronaut 3.x to 4.x including Jakarta namespace, annotation processor changes, and removed APIs",
                Category.MICRONAUT_MIGRATION,
                List.of("com.adhar.kit.rewrite.recipe.micronaut.MigrateToMicronaut4")
        ));

        // =====================================================================
        // Jakarta EE Migrations
        // =====================================================================
        register("jakarta-ee-10", new RecipeSet(
                "Migrate to Jakarta EE 10",
                "Migrates javax.* namespace to jakarta.* for full Jakarta EE 10 compliance",
                Category.JAKARTA_MIGRATION,
                List.of("org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta")
        ));

        register("jakarta-ee-11", new RecipeSet(
                "Migrate to Jakarta EE 11",
                "Full Jakarta EE 11 compliance including CDI 4.1, Servlet 6.1, JPA 3.2",
                Category.JAKARTA_MIGRATION,
                List.of("org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta")
        ));

        // =====================================================================
        // Cross-Framework Migration
        // =====================================================================
        register("spring-to-quarkus", new RecipeSet(
                "Migrate Spring Boot to Quarkus",
                "Converts Spring Boot application patterns to Quarkus equivalents (annotations, DI, config)",
                Category.CROSS_FRAMEWORK,
                List.of("com.adhar.kit.rewrite.recipe.cross.SpringToQuarkus")
        ));

        register("spring-to-micronaut", new RecipeSet(
                "Migrate Spring Boot to Micronaut",
                "Converts Spring Boot patterns to Micronaut equivalents (annotations, DI, config)",
                Category.CROSS_FRAMEWORK,
                List.of("com.adhar.kit.rewrite.recipe.cross.SpringToMicronaut")
        ));

        // =====================================================================
        // Helidon Migrations
        // =====================================================================
        register("helidon-4", new RecipeSet(
                "Upgrade to Helidon 4.x",
                "Migrates Helidon 3.x to 4.x with Jakarta namespace, new WebServer API, and config changes",
                Category.HELIDON_MIGRATION,
                List.of("com.adhar.kit.rewrite.recipe.helidon.MigrateToHelidon4")
        ));

        // =====================================================================
        // Vert.x Migrations
        // =====================================================================
        register("vertx-4", new RecipeSet(
                "Upgrade to Vert.x 4.x",
                "Migrates Vert.x 3.x to 4.x with Future-based API, removed callbacks, and package renames",
                Category.VERTX_MIGRATION,
                List.of("com.adhar.kit.rewrite.recipe.vertx.MigrateToVertx4")
        ));

        // Cross-framework: Spring to Helidon/Vert.x
        register("spring-to-helidon", new RecipeSet(
                "Migrate Spring Boot to Helidon",
                "Converts Spring Boot patterns to Helidon MP equivalents",
                Category.CROSS_FRAMEWORK,
                List.of("com.adhar.kit.rewrite.recipe.cross.SpringToHelidon")
        ));

        register("spring-to-vertx", new RecipeSet(
                "Migrate Spring Boot to Vert.x",
                "Converts Spring Boot patterns to Vert.x reactive equivalents",
                Category.CROSS_FRAMEWORK,
                List.of("com.adhar.kit.rewrite.recipe.cross.SpringToVertx")
        ));

        // =====================================================================
        // Dependency Management
        // =====================================================================
        register("dependency-upgrade", new RecipeSet(
                "Upgrade All Dependencies",
                "Updates all Maven/Gradle dependencies to their latest stable versions",
                Category.CODE_QUALITY,
                List.of("org.openrewrite.maven.UpgradeDependencyVersion")
        ));
    }

    private RecipeCatalog() {}

    /**
     * Get a recipe set by its key.
     *
     * @param key the recipe set key (e.g., "java-25", "spring-boot-4", "adhar-convenience-api")
     * @return the recipe set, or null if not found
     */
    public static RecipeSet get(String key) {
        return CATALOG.get(key);
    }

    /**
     * Get all available recipe sets.
     *
     * @return unmodifiable map of all recipe sets
     */
    public static Map<String, RecipeSet> getAll() {
        return Collections.unmodifiableMap(CATALOG);
    }

    /**
     * Get recipe sets by category.
     *
     * @param category the category to filter by
     * @return list of matching recipe sets
     */
    public static List<Map.Entry<String, RecipeSet>> getByCategory(Category category) {
        return CATALOG.entrySet().stream()
                .filter(e -> e.getValue().category() == category)
                .toList();
    }

    /**
     * List all available recipe set keys.
     *
     * @return list of recipe set keys
     */
    public static List<String> listKeys() {
        return List.copyOf(CATALOG.keySet());
    }

    private static void register(String key, RecipeSet recipeSet) {
        CATALOG.put(key, recipeSet);
    }

    /**
     * A named, categorized set of OpenRewrite recipes that accomplish a migration goal.
     *
     * @param displayName   human-readable name
     * @param description   what this recipe set does
     * @param category      categorization for grouping
     * @param recipeNames   fully-qualified OpenRewrite recipe class names
     */
    public record RecipeSet(
            String displayName,
            String description,
            Category category,
            List<String> recipeNames
    ) {}

    /**
     * Categories for organizing recipe sets.
     */
    public enum Category {
        JAVA_MIGRATION("Java Version Migrations"),
        SPRING_MIGRATION("Spring Boot Migrations"),
        TESTING("Testing Framework Migrations"),
        SECURITY("Security Hardening"),
        CODE_QUALITY("Code Quality"),
        QUARKUS_MIGRATION("Quarkus Migrations"),
        MICRONAUT_MIGRATION("Micronaut Migrations"),
        JAKARTA_MIGRATION("Jakarta EE Migrations"),
        HELIDON_MIGRATION("Helidon Migrations"),
        VERTX_MIGRATION("Vert.x Migrations"),
        CROSS_FRAMEWORK("Cross-Framework Migration"),
        ADHAR_KIT("Adhar Kit Specific");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
