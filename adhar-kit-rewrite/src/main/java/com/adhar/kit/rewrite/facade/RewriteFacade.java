package com.adhar.kit.rewrite.facade;

import com.adhar.kit.rewrite.catalog.RecipeCatalog;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade for running OpenRewrite recipes programmatically.
 * Provides a simple API to modernize, migrate, and enforce coding standards
 * on Java source files without requiring Maven plugin configuration.
 *
 * <p>Since full OpenRewrite execution requires complex LST parsing and build-time
 * infrastructure, this facade operates as a <b>configuration generator and recipe
 * catalog manager</b>. It generates {@code rewrite.yml} configuration files and
 * provides Maven commands for actual execution via the OpenRewrite Maven plugin.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * RewriteFacade rewrite = RewriteFacade.getInstance();
 *
 * // List available recipes
 * rewrite.listRecipeSets();
 *
 * // Dry-run a migration
 * RewriteResult result = rewrite.dryRun("java-25", projectDir);
 * result.changes().forEach(change -> log.info("Would change: {}", change.path()));
 *
 * // Apply a migration
 * RewriteResult result = rewrite.apply("spring-boot-4", projectDir);
 * log.info("Modified {} files", result.changedFileCount());
 *
 * // Run full Adhar Kit modernization
 * rewrite.apply("adhar-full-modernization", projectDir);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class RewriteFacade {

    private static final RewriteFacade INSTANCE = new RewriteFacade();

    private static final String REWRITE_YML_FILENAME = "rewrite.yml";
    private static final String MAVEN_PLUGIN_GROUP = "org.openrewrite.maven";
    private static final String MAVEN_PLUGIN_ARTIFACT = "rewrite-maven-plugin";

    private RewriteFacade() {}

    /**
     * Returns the singleton instance of RewriteFacade.
     *
     * @return the singleton facade instance
     */
    public static RewriteFacade getInstance() {
        return INSTANCE;
    }

    /**
     * Preview changes without writing to disk by generating a rewrite configuration
     * and outputting the dry-run Maven command.
     *
     * @param recipeSetKey the recipe set key from the catalog (e.g., "java-25", "spring-boot-4")
     * @param projectDir   the root directory of the project to analyze
     * @return a result describing the generated configuration and command
     * @throws IllegalArgumentException if the recipe set key is not found
     */
    public RewriteResult dryRun(String recipeSetKey, Path projectDir) {
        RecipeCatalog.RecipeSet recipeSet = requireRecipeSet(recipeSetKey);
        return dryRun(recipeSet.recipeNames(), projectDir);
    }

    /**
     * Apply changes to files by generating a rewrite configuration
     * and outputting the apply Maven command.
     *
     * @param recipeSetKey the recipe set key from the catalog
     * @param projectDir   the root directory of the project to modify
     * @return a result describing the generated configuration and command
     * @throws IllegalArgumentException if the recipe set key is not found
     */
    public RewriteResult apply(String recipeSetKey, Path projectDir) {
        RecipeCatalog.RecipeSet recipeSet = requireRecipeSet(recipeSetKey);
        return apply(recipeSet.recipeNames(), projectDir);
    }

    /**
     * Preview changes for specific recipes without writing to disk.
     *
     * @param recipeNames fully-qualified OpenRewrite recipe class names
     * @param projectDir  the root directory of the project to analyze
     * @return a result describing the generated configuration and command
     */
    public RewriteResult dryRun(List<String> recipeNames, Path projectDir) {
        long start = System.currentTimeMillis();
        Path configFile = generateRewriteYml(recipeNames, projectDir);
        long duration = System.currentTimeMillis() - start;

        String command = buildMavenCommand("dryRun", recipeNames);
        log.info("Dry-run configuration generated at: {}", configFile);
        log.info("Execute with: {}", command);

        FileChange configChange = new FileChange(
                configFile.toString(),
                "RewriteFacade",
                "Generated rewrite.yml for dry-run. Execute: " + command
        );

        return new RewriteResult(0, List.of(configChange), duration, true);
    }

    /**
     * Apply changes for specific recipes by generating configuration and outputting the command.
     *
     * @param recipeNames fully-qualified OpenRewrite recipe class names
     * @param projectDir  the root directory of the project to modify
     * @return a result describing the generated configuration and command
     */
    public RewriteResult apply(List<String> recipeNames, Path projectDir) {
        long start = System.currentTimeMillis();
        Path configFile = generateRewriteYml(recipeNames, projectDir);
        long duration = System.currentTimeMillis() - start;

        String command = buildMavenCommand("run", recipeNames);
        log.info("Apply configuration generated at: {}", configFile);
        log.info("Execute with: {}", command);

        FileChange configChange = new FileChange(
                configFile.toString(),
                "RewriteFacade",
                "Generated rewrite.yml for apply. Execute: " + command
        );

        return new RewriteResult(1, List.of(configChange), duration, false);
    }

    /**
     * List all available recipe sets from the catalog.
     *
     * @return unmodifiable map of recipe set keys to recipe sets
     */
    public Map<String, RecipeCatalog.RecipeSet> listRecipeSets() {
        return RecipeCatalog.getAll();
    }

    /**
     * List all available recipe set keys.
     *
     * @return list of recipe set keys
     */
    public List<String> listRecipeSetKeys() {
        return RecipeCatalog.listKeys();
    }

    /**
     * Get a specific recipe set by key.
     *
     * @param key the recipe set key
     * @return the recipe set, or null if not found
     */
    public RecipeCatalog.RecipeSet getRecipeSet(String key) {
        return RecipeCatalog.get(key);
    }

    /**
     * Check if a recipe set's recipe classes are available on the classpath.
     *
     * @param recipeSetKey the recipe set key to check
     * @return true if all recipe classes can be loaded
     */
    public boolean isRecipeAvailable(String recipeSetKey) {
        RecipeCatalog.RecipeSet recipeSet = RecipeCatalog.get(recipeSetKey);
        if (recipeSet == null) {
            return false;
        }
        for (String recipeName : recipeSet.recipeNames()) {
            try {
                Class.forName(recipeName);
            } catch (ClassNotFoundException e) {
                log.debug("Recipe class not found on classpath: {}", recipeName);
                return false;
            }
        }
        return true;
    }

    /**
     * Returns health information about the rewrite module.
     *
     * @return map containing status, available recipe count, and catalog keys
     */
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("recipeSets", RecipeCatalog.getAll().size());
        health.put("availableKeys", RecipeCatalog.listKeys());
        health.put("categories", RecipeCatalog.Category.values().length);
        return health;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private RecipeCatalog.RecipeSet requireRecipeSet(String key) {
        RecipeCatalog.RecipeSet recipeSet = RecipeCatalog.get(key);
        if (recipeSet == null) {
            throw new IllegalArgumentException(
                    "Unknown recipe set key: '" + key + "'. Available keys: " + RecipeCatalog.listKeys()
            );
        }
        return recipeSet;
    }

    private Path generateRewriteYml(List<String> recipeNames, Path projectDir) {
        String yaml = buildRewriteYaml(recipeNames);
        Path outputFile = projectDir.resolve(REWRITE_YML_FILENAME);
        try {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, yaml);
            log.debug("Generated rewrite.yml at: {}", outputFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write rewrite.yml to " + outputFile, e);
        }
        return outputFile;
    }

    private String buildRewriteYaml(List<String> recipeNames) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Generated by Adhar Kit RewriteFacade\n");
        yaml.append("# https://docs.openrewrite.org/reference/yaml-format-reference\n");
        yaml.append("---\n");
        yaml.append("type: specs.openrewrite.org/v1beta/recipe\n");
        yaml.append("name: com.adhar.kit.rewrite.GeneratedRecipe\n");
        yaml.append("displayName: Adhar Kit Generated Recipe\n");
        yaml.append("description: Auto-generated composite recipe from RewriteFacade.\n");
        yaml.append("recipeList:\n");
        for (String recipe : recipeNames) {
            yaml.append("  - ").append(recipe).append("\n");
        }
        return yaml.toString();
    }

    private String buildMavenCommand(String goal, List<String> recipeNames) {
        String activeRecipes = recipeNames.stream()
                .collect(Collectors.joining(","));
        return String.format(
                "mvn %s:%s:%s -Drewrite.activeRecipes=%s",
                MAVEN_PLUGIN_GROUP, MAVEN_PLUGIN_ARTIFACT, goal, activeRecipes
        );
    }

    /**
     * Result of a rewrite operation.
     *
     * @param changedFileCount number of files changed (or 0 for dry-run config generation)
     * @param changes          list of file changes or generated artifacts
     * @param durationMs       time taken in milliseconds
     * @param dryRun           true if this was a dry-run (no actual file modifications)
     */
    public record RewriteResult(
            int changedFileCount,
            List<FileChange> changes,
            long durationMs,
            boolean dryRun
    ) {}

    /**
     * Describes a single file change or generated artifact.
     *
     * @param path       file path that was changed or generated
     * @param recipeName the recipe or component that produced the change
     * @param diff       description of the change or diff content
     */
    public record FileChange(
            String path,
            String recipeName,
            String diff
    ) {}
}
