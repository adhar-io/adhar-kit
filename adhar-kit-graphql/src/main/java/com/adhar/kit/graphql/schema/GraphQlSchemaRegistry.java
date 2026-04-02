package com.adhar.kit.graphql.schema;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry for dynamically building GraphQL schemas from SDL type definitions.
 *
 * <p>Provides a thread-safe registry where types, queries, and mutations can be
 * registered independently and later merged into a single SDL schema string.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var registry = new GraphQlSchemaRegistry();
 * registry.registerType("User", "type User { id: ID!, name: String!, email: String! }");
 * registry.registerQuery("user", "user(id: ID!): User");
 * registry.registerMutation("createUser", "createUser(name: String!, email: String!): User");
 * String schema = registry.getSchema();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GraphQlSchemaRegistry {

    private final Map<String, String> types = new ConcurrentHashMap<>();
    private final Map<String, String> queries = new ConcurrentHashMap<>();
    private final Map<String, String> mutations = new ConcurrentHashMap<>();

    /**
     * Registers a GraphQL type definition in SDL format.
     *
     * @param typeName the name of the type (used as the registry key)
     * @param sdl      the SDL definition of the type (e.g., {@code "type User { id: ID! }"})
     * @throws IllegalArgumentException if typeName or sdl is null or blank
     */
    public void registerType(String typeName, String sdl) {
        validateNotBlank(typeName, "typeName");
        validateNotBlank(sdl, "sdl");
        log.debug("Registering GraphQL type: {}", typeName);
        types.put(typeName, sdl);
    }

    /**
     * Registers a GraphQL query field definition.
     *
     * @param name the query field name (used as the registry key)
     * @param sdl  the SDL field definition (e.g., {@code "user(id: ID!): User"})
     * @throws IllegalArgumentException if name or sdl is null or blank
     */
    public void registerQuery(String name, String sdl) {
        validateNotBlank(name, "name");
        validateNotBlank(sdl, "sdl");
        log.debug("Registering GraphQL query: {}", name);
        queries.put(name, sdl);
    }

    /**
     * Registers a GraphQL mutation field definition.
     *
     * @param name the mutation field name (used as the registry key)
     * @param sdl  the SDL field definition (e.g., {@code "createUser(name: String!): User"})
     * @throws IllegalArgumentException if name or sdl is null or blank
     */
    public void registerMutation(String name, String sdl) {
        validateNotBlank(name, "name");
        validateNotBlank(sdl, "sdl");
        log.debug("Registering GraphQL mutation: {}", name);
        mutations.put(name, sdl);
    }

    /**
     * Returns the merged schema SDL string composed of all registered types,
     * queries, and mutations.
     *
     * <p>The returned schema includes a {@code type Query} block (if any queries
     * are registered) and a {@code type Mutation} block (if any mutations are
     * registered), followed by all registered type definitions.</p>
     *
     * @return the merged SDL schema string
     */
    public String getSchema() {
        var builder = new StringBuilder();

        // Append type definitions
        if (!types.isEmpty()) {
            String typeDefinitions = types.values().stream()
                    .collect(Collectors.joining("\n\n"));
            builder.append(typeDefinitions);
        }

        // Append Query type
        if (!queries.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("type Query {\n");
            queries.values().forEach(q -> builder.append("  ").append(q).append("\n"));
            builder.append("}");
        }

        // Append Mutation type
        if (!mutations.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("type Mutation {\n");
            mutations.values().forEach(m -> builder.append("  ").append(m).append("\n"));
            builder.append("}");
        }

        log.debug("Generated merged schema with {} types, {} queries, {} mutations",
                types.size(), queries.size(), mutations.size());
        return builder.toString();
    }

    /**
     * Returns the number of registered types.
     *
     * @return count of registered type definitions
     */
    public int getTypeCount() {
        return types.size();
    }

    /**
     * Returns the number of registered queries.
     *
     * @return count of registered query fields
     */
    public int getQueryCount() {
        return queries.size();
    }

    /**
     * Returns the number of registered mutations.
     *
     * @return count of registered mutation fields
     */
    public int getMutationCount() {
        return mutations.size();
    }

    /**
     * Checks whether a type with the given name is already registered.
     *
     * @param typeName the type name to check
     * @return true if the type is registered
     */
    public boolean hasType(String typeName) {
        return types.containsKey(typeName);
    }

    /**
     * Removes all registered types, queries, and mutations.
     */
    public void clear() {
        types.clear();
        queries.clear();
        mutations.clear();
        log.debug("Schema registry cleared");
    }

    private void validateNotBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
