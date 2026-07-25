package com.adhar.kit.graphql.schema;

import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GraphQlSchemaRegistry}.
 */
class GraphQlSchemaRegistryTest {

    private GraphQlSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GraphQlSchemaRegistry();
    }

    // ----------------------------------------------------------------
    // registerType
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("registerType()")
    class RegisterType {

        @Test
        @DisplayName("should store type and increment type count")
        void storesType() {
            registry.registerType("User", "type User { id: ID! }");

            assertThat(registry.getTypeCount()).isEqualTo(1);
            assertThat(registry.hasType("User")).isTrue();
        }

        @Test
        @DisplayName("should overwrite existing type with same name")
        void overwritesExisting() {
            registry.registerType("User", "type User { id: ID! }");
            registry.registerType("User", "type User { id: ID!, name: String! }");

            assertThat(registry.getTypeCount()).isEqualTo(1);
            assertThat(registry.getSchema()).contains("name: String!");
        }

        @Test
        @DisplayName("should throw on null type name")
        void throwsOnNullName() {
            assertThatThrownBy(() -> registry.registerType(null, "type User { id: ID! }"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on blank SDL")
        void throwsOnBlankSdl() {
            assertThatThrownBy(() -> registry.registerType("User", ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw a clear error on syntactically invalid SDL")
        void throwsOnInvalidSyntax() {
            assertThatThrownBy(() -> registry.registerType("User", "type User { id: ID!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SDL for type 'User'");
        }

        @Test
        @DisplayName("should reject a bare field definition (not a valid standalone type)")
        void rejectsBareFieldAsType() {
            assertThatThrownBy(() -> registry.registerType("Bogus", "user(id: ID!): User"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ----------------------------------------------------------------
    // registerQuery
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("registerQuery()")
    class RegisterQuery {

        @Test
        @DisplayName("should store query and increment query count")
        void storesQuery() {
            registry.registerQuery("user", "user(id: ID!): User");

            assertThat(registry.getQueryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw on null name")
        void throwsOnNullName() {
            assertThatThrownBy(() -> registry.registerQuery(null, "user: User"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw a clear error on syntactically invalid field SDL")
        void throwsOnInvalidSyntax() {
            assertThatThrownBy(() -> registry.registerQuery("user", "user(id: ID!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SDL for query field 'user'");
        }
    }

    // ----------------------------------------------------------------
    // registerMutation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("registerMutation()")
    class RegisterMutation {

        @Test
        @DisplayName("should store mutation and increment mutation count")
        void storesMutation() {
            registry.registerMutation("createUser", "createUser(name: String!): User");

            assertThat(registry.getMutationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw on blank name")
        void throwsOnBlankName() {
            assertThatThrownBy(() -> registry.registerMutation("  ", "createUser: User"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw a clear error on syntactically invalid field SDL")
        void throwsOnInvalidSyntax() {
            assertThatThrownBy(() -> registry.registerMutation("createUser", "createUser(name: String!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SDL for mutation field 'createUser'");
        }
    }

    // ----------------------------------------------------------------
    // getSchema
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getSchema()")
    class GetSchema {

        @Test
        @DisplayName("should return empty string when nothing is registered")
        void emptyWhenNothingRegistered() {
            assertThat(registry.getSchema()).isEmpty();
        }

        @Test
        @DisplayName("should merge types, queries, and mutations into SDL")
        void mergesAll() {
            registry.registerType("User", "type User { id: ID!, name: String! }");
            registry.registerQuery("user", "user(id: ID!): User");
            registry.registerMutation("createUser", "createUser(name: String!): User");

            String schema = registry.getSchema();

            assertThat(schema).contains("type User { id: ID!, name: String! }");
            assertThat(schema).contains("type Query {");
            assertThat(schema).contains("  user(id: ID!): User");
            assertThat(schema).contains("type Mutation {");
            assertThat(schema).contains("  createUser(name: String!): User");
        }

        @Test
        @DisplayName("should omit Query block when no queries registered")
        void noQueryBlock() {
            registry.registerType("User", "type User { id: ID! }");

            String schema = registry.getSchema();

            assertThat(schema).doesNotContain("type Query");
        }

        @Test
        @DisplayName("should omit Mutation block when no mutations registered")
        void noMutationBlock() {
            registry.registerType("User", "type User { id: ID! }");

            String schema = registry.getSchema();

            assertThat(schema).doesNotContain("type Mutation");
        }
    }

    // ----------------------------------------------------------------
    // Counts
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Counts")
    class Counts {

        @Test
        @DisplayName("should return correct counts for types, queries, and mutations")
        void correctCounts() {
            registry.registerType("User", "type User { id: ID! }");
            registry.registerType("Post", "type Post { id: ID! }");
            registry.registerQuery("user", "user(id: ID!): User");
            registry.registerMutation("createUser", "createUser: User");

            assertThat(registry.getTypeCount()).isEqualTo(2);
            assertThat(registry.getQueryCount()).isEqualTo(1);
            assertThat(registry.getMutationCount()).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------
    // clear
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("should reset all registrations")
        void resetsEverything() {
            registry.registerType("User", "type User { id: ID! }");
            registry.registerQuery("user", "user(id: ID!): User");
            registry.registerMutation("createUser", "createUser: User");

            registry.clear();

            assertThat(registry.getTypeCount()).isZero();
            assertThat(registry.getQueryCount()).isZero();
            assertThat(registry.getMutationCount()).isZero();
            assertThat(registry.getSchema()).isEmpty();
        }
    }

    // ----------------------------------------------------------------
    // getTypeDefinitionRegistry
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getTypeDefinitionRegistry()")
    class GetTypeDefinitionRegistry {

        @Test
        @DisplayName("should return an empty registry when nothing is registered")
        void emptyWhenNothingRegistered() {
            TypeDefinitionRegistry typeDefinitionRegistry = registry.getTypeDefinitionRegistry();

            assertThat(typeDefinitionRegistry.types()).isEmpty();
        }

        @Test
        @DisplayName("should expose the merged schema as a parsed TypeDefinitionRegistry")
        void exposesMergedRegistry() {
            registry.registerType("User", "type User { id: ID!, name: String! }");
            registry.registerQuery("user", "user(id: ID!): User");
            registry.registerMutation("createUser", "createUser(name: String!): User");

            TypeDefinitionRegistry typeDefinitionRegistry = registry.getTypeDefinitionRegistry();

            assertThat(typeDefinitionRegistry.getType("User")).isPresent();
            assertThat(typeDefinitionRegistry.getType("Query")).isPresent();
            assertThat(typeDefinitionRegistry.getType("Mutation")).isPresent();
        }
    }

    // ----------------------------------------------------------------
    // merge
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("merge()")
    class Merge {

        @Test
        @DisplayName("should copy all types, queries, and mutations from another registry")
        void mergesAnotherRegistry() {
            GraphQlSchemaRegistry other = new GraphQlSchemaRegistry();
            other.registerType("Post", "type Post { id: ID! }");
            other.registerQuery("posts", "posts: [Post]");
            other.registerMutation("createPost", "createPost(title: String!): Post");

            registry.registerType("User", "type User { id: ID! }");
            registry.merge(other);

            assertThat(registry.getTypeCount()).isEqualTo(2);
            assertThat(registry.hasType("Post")).isTrue();
            assertThat(registry.getQueryCount()).isEqualTo(1);
            assertThat(registry.getMutationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should be a no-op when merging null")
        void mergeNullIsNoOp() {
            registry.registerType("User", "type User { id: ID! }");

            registry.merge(null);

            assertThat(registry.getTypeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("merged entries overwrite existing entries with the same name")
        void mergeOverwritesSameName() {
            registry.registerType("User", "type User { id: ID! }");
            GraphQlSchemaRegistry other = new GraphQlSchemaRegistry();
            other.registerType("User", "type User { id: ID!, name: String! }");

            registry.merge(other);

            assertThat(registry.getTypeCount()).isEqualTo(1);
            assertThat(registry.getSchema()).contains("name: String!");
        }
    }

    // ----------------------------------------------------------------
    // Thread safety
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Thread safety")
    class ThreadSafety {

        @Test
        @DisplayName("should handle concurrent registrations without error")
        void concurrentRegistrations() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Throwable> errors = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                int idx = i;
                executor.submit(() -> {
                    try {
                        registry.registerType("Type" + idx, "type Type" + idx + " { id: ID! }");
                        registry.registerQuery("query" + idx, "query" + idx + ": Type" + idx);
                        registry.registerMutation("mutation" + idx, "mutation" + idx + ": Type" + idx);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(errors).isEmpty();
            assertThat(registry.getTypeCount()).isEqualTo(threadCount);
            assertThat(registry.getQueryCount()).isEqualTo(threadCount);
            assertThat(registry.getMutationCount()).isEqualTo(threadCount);
        }
    }
}
