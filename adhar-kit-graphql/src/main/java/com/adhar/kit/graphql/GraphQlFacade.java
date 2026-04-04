package com.adhar.kit.graphql;

import com.adhar.kit.graphql.config.GraphQlProperties;
import com.adhar.kit.graphql.dataloader.DataLoaderRegistrar;
import com.adhar.kit.graphql.dataloader.DataLoaderRegistrar.BatchLoaderFunction;
import com.adhar.kit.graphql.pagination.PaginationUtils;
import com.adhar.kit.graphql.schema.GraphQlSchemaRegistry;
import com.adhar.kit.graphql.validation.InputValidator;
import com.adhar.kit.graphql.validation.InputValidator.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified facade for the Adhar GraphQL module.
 *
 * <p>Provides a single entry point for schema registration, input validation,
 * cursor-based pagination, and DataLoader batch loading. Uses a double-checked
 * locking singleton so it can be accessed from non-Spring contexts as well.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * GraphQlFacade graphql = GraphQlFacade.getInstance();
 *
 * graphql.registerType("User", "type User { id: ID!, name: String! }");
 * graphql.registerQuery("user", "user(id: ID!): User");
 * graphql.registerMutation("createUser", "createUser(name: String!): User");
 *
 * String schema = graphql.getSchema();
 *
 * PaginationUtils.Connection<User> page = graphql.paginate(users, 20, null);
 *
 * ValidationResult result = graphql.validate(input);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GraphQlFacade {

    private static volatile GraphQlFacade instance;

    private final GraphQlProperties properties;
    private final GraphQlSchemaRegistry schemaRegistry;
    private final InputValidator inputValidator;
    private final DataLoaderRegistrar dataLoaderRegistrar;

    private GraphQlFacade() {
        this.properties = new GraphQlProperties();
        this.schemaRegistry = new GraphQlSchemaRegistry();
        this.inputValidator = new InputValidator();
        this.dataLoaderRegistrar = new DataLoaderRegistrar();
        log.info("Initialized GraphQlFacade");
    }

    /**
     * Returns the singleton instance of GraphQlFacade, creating it if necessary.
     *
     * @return the shared GraphQlFacade instance
     */
    public static GraphQlFacade getInstance() {
        if (instance == null) {
            synchronized (GraphQlFacade.class) {
                if (instance == null) {
                    instance = new GraphQlFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Registers a GraphQL type definition in SDL format.
     *
     * @param name the type name (used as the registry key)
     * @param sdl  the SDL definition (e.g., {@code "type User { id: ID! }"})
     */
    public void registerType(String name, String sdl) {
        schemaRegistry.registerType(name, sdl);
    }

    /**
     * Registers a GraphQL query field definition.
     *
     * @param name the query field name
     * @param sdl  the SDL field definition (e.g., {@code "user(id: ID!): User"})
     */
    public void registerQuery(String name, String sdl) {
        schemaRegistry.registerQuery(name, sdl);
    }

    /**
     * Registers a GraphQL mutation field definition.
     *
     * @param name the mutation field name
     * @param sdl  the SDL field definition (e.g., {@code "createUser(name: String!): User"})
     */
    public void registerMutation(String name, String sdl) {
        schemaRegistry.registerMutation(name, sdl);
    }

    /**
     * Returns the merged schema SDL string composed of all registered types,
     * queries, and mutations.
     *
     * @return the merged SDL schema string
     */
    public String getSchema() {
        return schemaRegistry.getSchema();
    }

    /**
     * Creates a Relay-style cursor-based paginated connection from a list of items.
     *
     * @param items       the full list of items to paginate
     * @param first       the maximum number of items to return
     * @param afterCursor the cursor after which to start (null to start from the beginning)
     * @param <T>         the item type
     * @return a connection containing the requested page of items
     */
    public <T> PaginationUtils.Connection<T> paginate(List<T> items, int first, String afterCursor) {
        return PaginationUtils.fromList(items, first, afterCursor);
    }

    /**
     * Validates the given input object against its Jakarta Validation annotations.
     *
     * @param input the input object to validate
     * @return a {@link ValidationResult} containing any constraint violations
     */
    public ValidationResult validate(Object input) {
        return inputValidator.validate(input);
    }

    /**
     * Registers a batch loader function for the DataLoader pattern.
     *
     * @param name   the unique name for this data loader
     * @param loader the batch loader function
     */
    public void registerBatchLoader(String name, BatchLoaderFunction<?, ?> loader) {
        dataLoaderRegistrar.registerBatchLoader(name, loader);
    }

    /**
     * Returns the GraphQL configuration properties.
     *
     * @return the current {@link GraphQlProperties}
     */
    public GraphQlProperties getProperties() {
        return properties;
    }

    /**
     * Checks whether the GraphQL module is enabled.
     *
     * @return true if GraphQL is enabled in the configuration
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Returns health and status information about the GraphQL module.
     *
     * <p>The returned map contains:</p>
     * <ul>
     *   <li>{@code status} - "UP" if enabled, "DISABLED" otherwise</li>
     *   <li>{@code enabled} - whether the module is enabled</li>
     *   <li>{@code registeredTypes} - number of registered type definitions</li>
     *   <li>{@code registeredQueries} - number of registered query fields</li>
     *   <li>{@code registeredMutations} - number of registered mutation fields</li>
     *   <li>{@code registeredDataLoaders} - number of registered batch loaders</li>
     *   <li>{@code introspectionEnabled} - whether schema introspection is allowed</li>
     * </ul>
     *
     * @return an unmodifiable map with GraphQL module status information
     */
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", properties.isEnabled() ? "UP" : "DISABLED");
        status.put("enabled", properties.isEnabled());
        status.put("registeredTypes", schemaRegistry.getTypeCount());
        status.put("registeredQueries", schemaRegistry.getQueryCount());
        status.put("registeredMutations", schemaRegistry.getMutationCount());
        status.put("registeredDataLoaders", dataLoaderRegistrar.size());
        status.put("introspectionEnabled", properties.isIntrospectionEnabled());
        log.debug("GraphQL health check: {}", status);
        return Map.copyOf(status);
    }
}
