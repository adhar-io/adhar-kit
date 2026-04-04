package com.adhar.kit.graphql;

import com.adhar.kit.graphql.pagination.PaginationUtils;
import com.adhar.kit.graphql.validation.InputValidator.ValidationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlFacade}.
 */
class GraphQlFacadeTest {

    private GraphQlFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        // Reset the singleton instance before each test to ensure isolation
        Field instanceField = GraphQlFacade.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        facade = GraphQlFacade.getInstance();
    }

    // ----------------------------------------------------------------
    // Singleton
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("should return the same instance on repeated calls")
        void returnsSameInstance() {
            GraphQlFacade first = GraphQlFacade.getInstance();
            GraphQlFacade second = GraphQlFacade.getInstance();

            assertThat(first).isSameAs(second);
        }
    }

    // ----------------------------------------------------------------
    // Schema Registration delegation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Schema registration")
    class SchemaRegistration {

        @Test
        @DisplayName("registerType delegates to schema registry")
        void registerTypeDelegates() {
            facade.registerType("User", "type User { id: ID!, name: String! }");

            String schema = facade.getSchema();
            assertThat(schema).contains("type User");
        }

        @Test
        @DisplayName("registerQuery delegates to schema registry")
        void registerQueryDelegates() {
            facade.registerQuery("user", "user(id: ID!): User");

            String schema = facade.getSchema();
            assertThat(schema).contains("type Query");
            assertThat(schema).contains("user(id: ID!): User");
        }

        @Test
        @DisplayName("registerMutation delegates to schema registry")
        void registerMutationDelegates() {
            facade.registerMutation("createUser", "createUser(name: String!): User");

            String schema = facade.getSchema();
            assertThat(schema).contains("type Mutation");
            assertThat(schema).contains("createUser(name: String!): User");
        }

        @Test
        @DisplayName("getSchema returns merged SDL from all registrations")
        void getSchemaMergesAll() {
            facade.registerType("User", "type User { id: ID!, name: String! }");
            facade.registerQuery("user", "user(id: ID!): User");
            facade.registerMutation("createUser", "createUser(name: String!): User");

            String schema = facade.getSchema();

            assertThat(schema)
                    .contains("type User { id: ID!, name: String! }")
                    .contains("type Query")
                    .contains("user(id: ID!): User")
                    .contains("type Mutation")
                    .contains("createUser(name: String!): User");
        }
    }

    // ----------------------------------------------------------------
    // Pagination
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("paginate()")
    class Paginate {

        @Test
        @DisplayName("should return empty connection for empty list")
        void emptyList() {
            PaginationUtils.Connection<String> result = facade.paginate(List.of(), 10, null);

            assertThat(result.edges()).isEmpty();
            assertThat(result.pageInfo().hasNextPage()).isFalse();
            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
        }

        @Test
        @DisplayName("should return first N items when no cursor is provided")
        void firstNItems() {
            List<String> items = List.of("a", "b", "c", "d", "e");

            PaginationUtils.Connection<String> result = facade.paginate(items, 3, null);

            assertThat(result.edges()).hasSize(3);
            assertThat(result.edges().get(0).node()).isEqualTo("a");
            assertThat(result.edges().get(2).node()).isEqualTo("c");
            assertThat(result.pageInfo().hasNextPage()).isTrue();
            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
        }

        @Test
        @DisplayName("should page from after cursor position")
        void afterCursor() {
            List<String> items = List.of("a", "b", "c", "d", "e");
            String cursorForB = PaginationUtils.encodeCursor(1); // index of "b"

            PaginationUtils.Connection<String> result = facade.paginate(items, 2, cursorForB);

            assertThat(result.edges()).hasSize(2);
            assertThat(result.edges().get(0).node()).isEqualTo("c");
            assertThat(result.edges().get(1).node()).isEqualTo("d");
            assertThat(result.pageInfo().hasPreviousPage()).isTrue();
            assertThat(result.pageInfo().hasNextPage()).isTrue();
        }

        @Test
        @DisplayName("should handle first larger than list size")
        void firstLargerThanList() {
            List<String> items = List.of("a", "b");

            PaginationUtils.Connection<String> result = facade.paginate(items, 100, null);

            assertThat(result.edges()).hasSize(2);
            assertThat(result.pageInfo().hasNextPage()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // Validation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("should return valid result for object with no violations")
        void validInput() {
            var input = new ValidInput("hello");

            ValidationResult result = facade.validate(input);

            assertThat(result.isValid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("should return errors for invalid input")
        void invalidInput() {
            var input = new ValidInput("");

            ValidationResult result = facade.validate(input);

            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        // Simple test record/class with Jakarta Validation annotations
        record ValidInput(@NotBlank @Size(min = 1, max = 50) String name) {
        }
    }

    // ----------------------------------------------------------------
    // isEnabled
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("isEnabled()")
    class IsEnabled {

        @Test
        @DisplayName("should return true by default")
        void defaultEnabled() {
            assertThat(facade.isEnabled()).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // Health
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("health()")
    class Health {

        @Test
        @DisplayName("should return expected keys")
        void healthKeys() {
            Map<String, Object> health = facade.health();

            assertThat(health).containsKeys(
                    "status",
                    "enabled",
                    "registeredTypes",
                    "registeredQueries",
                    "registeredMutations",
                    "registeredDataLoaders",
                    "introspectionEnabled"
            );
        }

        @Test
        @DisplayName("should report UP status when enabled")
        void statusUp() {
            Map<String, Object> health = facade.health();

            assertThat(health.get("status")).isEqualTo("UP");
            assertThat(health.get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("should reflect registered counts")
        void registeredCounts() {
            facade.registerType("User", "type User { id: ID! }");
            facade.registerQuery("user", "user(id: ID!): User");
            facade.registerMutation("createUser", "createUser(name: String!): User");

            Map<String, Object> health = facade.health();

            assertThat(health.get("registeredTypes")).isEqualTo(1);
            assertThat(health.get("registeredQueries")).isEqualTo(1);
            assertThat(health.get("registeredMutations")).isEqualTo(1);
        }
    }
}
