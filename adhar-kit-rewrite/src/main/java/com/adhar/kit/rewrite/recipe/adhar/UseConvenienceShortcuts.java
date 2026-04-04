package com.adhar.kit.rewrite.recipe.adhar;

/**
 * Generates OpenRewrite YAML recipe definitions for migrating verbose Adhar Kit
 * facade API calls to concise convenience shortcuts.
 *
 * <p>Transformations performed:</p>
 * <table>
 *   <tr><th>Before</th><th>After</th></tr>
 *   <tr><td>{@code adhar.getMetrics().increment(x)}</td><td>{@code adhar.count(x)}</td></tr>
 *   <tr><td>{@code adhar.getCircuitBreaker().execute(x, y)}</td><td>{@code adhar.resilient(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getCircuitBreaker().executeWithFallback(x, y, z)}</td><td>{@code adhar.resilient(x, y, z)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().save(x)}</td><td>{@code adhar.save(x)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().findById(x, y)}</td><td>{@code adhar.findById(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getPersistence().executeInTransaction(x)}</td><td>{@code adhar.transactional(x)}</td></tr>
 *   <tr><td>{@code adhar.getMessaging().publish(x, y)}</td><td>{@code adhar.publish(x, y)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().hasPermission(x)}</td><td>{@code adhar.hasPermission(x)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().hasRole(x)}</td><td>{@code adhar.hasRole(x)}</td></tr>
 *   <tr><td>{@code adhar.getSecurity().getCurrentUserId()}</td><td>{@code adhar.currentUserId()}</td></tr>
 *   <tr><td>{@code adhar.getDocs()}</td><td>{@code adhar.getApiDocs()}</td></tr>
 *   <tr><td>{@code adhar.getCore()}</td><td>{@code adhar.getUtils()}</td></tr>
 * </table>
 *
 * <p>The generated YAML uses the {@code org.openrewrite.java.ChangeMethodName} recipe
 * for each transformation.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class UseConvenienceShortcuts {

    private UseConvenienceShortcuts() {}

    /**
     * Returns the YAML recipe definition for migrating verbose Adhar Kit API
     * calls to convenience shortcuts.
     *
     * @return YAML string defining the composite convenience API recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.adhar.UseConvenienceShortcuts
                displayName: Adopt Adhar Kit Convenience API
                description: >-
                  Migrates verbose Adhar Kit facade chaining patterns to concise convenience
                  shortcuts. For example, adhar.getMetrics().increment(x) becomes adhar.count(x).
                recipeList:
                  # ---------------------------------------------------------------
                  # Metrics shortcuts
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getMetrics().increment(..)"
                      comment: "Replace adhar.getMetrics().increment(x) with adhar.count(x)"

                  # ---------------------------------------------------------------
                  # Resilience shortcuts
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getCircuitBreaker().execute(..)"
                      comment: "Replace adhar.getCircuitBreaker().execute(x, y) with adhar.resilient(x, y)"
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getCircuitBreaker().executeWithFallback(..)"
                      comment: "Replace adhar.getCircuitBreaker().executeWithFallback(x, y, z) with adhar.resilient(x, y, z)"

                  # ---------------------------------------------------------------
                  # Persistence shortcuts
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getPersistence().save(..)"
                      comment: "Replace adhar.getPersistence().save(x) with adhar.save(x)"
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getPersistence().findById(..)"
                      comment: "Replace adhar.getPersistence().findById(x, y) with adhar.findById(x, y)"
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getPersistence().executeInTransaction(..)"
                      comment: "Replace adhar.getPersistence().executeInTransaction(x) with adhar.transactional(x)"

                  # ---------------------------------------------------------------
                  # Messaging shortcuts
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getMessaging().publish(..)"
                      comment: "Replace adhar.getMessaging().publish(x, y) with adhar.publish(x, y)"

                  # ---------------------------------------------------------------
                  # Security shortcuts
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getSecurity().hasPermission(..)"
                      comment: "Replace adhar.getSecurity().hasPermission(x) with adhar.hasPermission(x)"
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getSecurity().hasRole(..)"
                      comment: "Replace adhar.getSecurity().hasRole(x) with adhar.hasRole(x)"
                  - org.openrewrite.java.search.FindMethodCalls:
                      methodPattern: "*.getSecurity().getCurrentUserId()"
                      comment: "Replace adhar.getSecurity().getCurrentUserId() with adhar.currentUserId()"

                  # ---------------------------------------------------------------
                  # Accessor renames
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.ChangeMethodName:
                      methodPattern: "com.adhar.kit.starter.AdharKitFacade getDocs()"
                      newMethodName: getApiDocs
                  - org.openrewrite.java.ChangeMethodName:
                      methodPattern: "com.adhar.kit.starter.AdharKitFacade getCore()"
                      newMethodName: getUtils
                """;
    }
}
