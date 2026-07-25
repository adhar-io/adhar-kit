package com.adhar.kit.rewrite.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates that every recipe FQN referenced by {@link RecipeCatalog} actually resolves on the
 * current classpath, batching {@link com.adhar.kit.rewrite.facade.RewriteFacade#isRecipeAvailable(String)}'s
 * single-recipe-set check across the whole catalog and reporting exactly which recipe names (and
 * which catalog keys) failed to resolve.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * RecipeCatalogValidator.ValidationReport report = RecipeCatalogValidator.validateAll();
 * if (!report.isValid()) {
 *     log.warn(report.toSummary());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class RecipeCatalogValidator {

    private RecipeCatalogValidator() {}

    /**
     * Validates every recipe set currently registered in {@link RecipeCatalog}.
     *
     * @return a report describing which recipe names (if any) failed to resolve
     */
    public static ValidationReport validateAll() {
        return validate(RecipeCatalog.getAll());
    }

    /**
     * Validates an arbitrary map of recipe sets, keyed the same way as {@link RecipeCatalog#getAll()}.
     * Exposed separately from {@link #validateAll()} so callers (and tests) can validate a custom
     * or partial catalog, e.g. one seeded with a deliberately-bogus recipe name.
     *
     * @param catalog the recipe sets to validate
     * @return a report describing which recipe names (if any) failed to resolve
     */
    public static ValidationReport validate(Map<String, RecipeCatalog.RecipeSet> catalog) {
        Map<String, List<String>> unresolvedByKey = new LinkedHashMap<>();
        Set<String> allUnresolved = new LinkedHashSet<>();
        int totalRecipeNames = 0;

        for (Map.Entry<String, RecipeCatalog.RecipeSet> entry : catalog.entrySet()) {
            List<String> unresolvedForKey = new ArrayList<>();
            for (String recipeName : entry.getValue().recipeNames()) {
                totalRecipeNames++;
                if (!isResolvable(recipeName)) {
                    unresolvedForKey.add(recipeName);
                    allUnresolved.add(recipeName);
                }
            }
            if (!unresolvedForKey.isEmpty()) {
                unresolvedByKey.put(entry.getKey(), List.copyOf(unresolvedForKey));
            }
        }

        return new ValidationReport(
                catalog.size(),
                totalRecipeNames,
                Map.copyOf(unresolvedByKey),
                List.copyOf(allUnresolved)
        );
    }

    /**
     * Checks whether a single recipe FQN resolves on the current classpath, without initializing
     * (running static initializers of) the class.
     *
     * @param recipeFqn the fully-qualified recipe class name to check
     * @return true if the class can be loaded
     */
    public static boolean isResolvable(String recipeFqn) {
        try {
            Class.forName(recipeFqn, false, RecipeCatalogValidator.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * The outcome of validating some or all of {@link RecipeCatalog}.
     *
     * @param catalogSize              number of recipe set entries that were checked
     * @param totalRecipeNames         total number of recipe FQNs checked, across all entries
     * @param unresolvedRecipesByKey   catalog key -> unresolved recipe FQNs referenced by that key;
     *                                 empty if everything resolved
     * @param allUnresolvedRecipeNames the de-duplicated union of every unresolved recipe FQN found
     */
    public record ValidationReport(
            int catalogSize,
            int totalRecipeNames,
            Map<String, List<String>> unresolvedRecipesByKey,
            List<String> allUnresolvedRecipeNames
    ) {
        /**
         * @return true if every recipe FQN in the validated catalog resolved successfully
         */
        public boolean isValid() {
            return unresolvedRecipesByKey.isEmpty();
        }

        /**
         * @return the number of distinct unresolved recipe FQNs
         */
        public int unresolvedCount() {
            return allUnresolvedRecipeNames.size();
        }

        /**
         * @return a short, human-readable summary of the validation outcome
         */
        public String toSummary() {
            if (isValid()) {
                return "All %d recipe reference(s) across %d catalog entrie(s) resolved successfully."
                        .formatted(totalRecipeNames, catalogSize);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(unresolvedCount())
                    .append(" unresolved recipe(s) across ")
                    .append(unresolvedRecipesByKey.size())
                    .append(" catalog entrie(s) out of ")
                    .append(catalogSize)
                    .append(":\n");
            unresolvedRecipesByKey.forEach((key, names) ->
                    sb.append("  - ").append(key).append(": ").append(names).append('\n'));
            return sb.toString();
        }
    }
}
