package com.adhar.kit.graphql.dataloader;

import com.adhar.kit.graphql.dataloader.DataLoaderRegistrar.BatchLoaderFunction;
import graphql.GraphQLContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataLoaderRegistrar}.
 */
class DataLoaderRegistrarTest {

    private DataLoaderRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new DataLoaderRegistrar();
    }

    private static BatchLoaderFunction<Long, String> sampleLoader() {
        return keys -> CompletableFuture.completedFuture(keys.stream().map(k -> "v" + k).toList());
    }

    @Test
    @DisplayName("registers and retrieves a batch loader")
    void registerAndGet() {
        BatchLoaderFunction<Long, String> loader = sampleLoader();
        registrar.registerBatchLoader("users", loader);

        BatchLoaderFunction<Long, String> retrieved = registrar.getBatchLoader("users");
        assertThat(retrieved).isSameAs(loader);
        assertThat(registrar.hasLoader("users")).isTrue();
        assertThat(registrar.size()).isEqualTo(1);
        assertThat(registrar.getRegisteredNames()).containsExactly("users");
    }

    @Test
    @DisplayName("registered loader executes correctly")
    void loaderExecutes() throws Exception {
        registrar.registerBatchLoader("users", sampleLoader());
        BatchLoaderFunction<Long, String> loader = registrar.getBatchLoader("users");

        List<String> result = loader.load(List.of(1L, 2L)).get();
        assertThat(result).containsExactly("v1", "v2");
    }

    @Test
    @DisplayName("re-registering same name replaces the loader")
    void replaceLoader() {
        BatchLoaderFunction<Long, String> first = sampleLoader();
        BatchLoaderFunction<Long, String> second = sampleLoader();
        registrar.registerBatchLoader("users", first);
        registrar.registerBatchLoader("users", second);

        assertThat(registrar.size()).isEqualTo(1);
        assertThat(registrar.getBatchLoader("users")).isSameAs(second);
    }

    @Test
    @DisplayName("getBatchLoader returns null for unknown name")
    void getUnknown() {
        assertThat(registrar.getBatchLoader("missing")).isNull();
        assertThat(registrar.hasLoader("missing")).isFalse();
    }

    @Test
    @DisplayName("rejects null name")
    void rejectsNullName() {
        assertThatThrownBy(() -> registrar.registerBatchLoader(null, sampleLoader()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("rejects blank name")
    void rejectsBlankName() {
        assertThatThrownBy(() -> registrar.registerBatchLoader("   ", sampleLoader()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("rejects null loader")
    void rejectsNullLoader() {
        assertThatThrownBy(() -> registrar.registerBatchLoader("users", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BatchLoaderFunction");
    }

    @Test
    @DisplayName("empty registrar reports zero size")
    void emptyRegistrar() {
        assertThat(registrar.size()).isZero();
        assertThat(registrar.getRegisteredNames()).isEmpty();
    }

    // ----------------------------------------------------------------
    // Spring GraphQL DataLoaderRegistrar adaptation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("registerDataLoaders() (Spring GraphQL adaptation)")
    class RegisterDataLoaders {

        @Test
        @DisplayName("hasRegistrations reflects whether any batch loader is registered")
        void hasRegistrationsReflectsState() {
            assertThat(registrar.hasRegistrations()).isFalse();

            registrar.registerBatchLoader("users", sampleLoader());

            assertThat(registrar.hasRegistrations()).isTrue();
        }

        @Test
        @DisplayName("adapts a registered batch loader into a graphql-java DataLoader")
        void adaptsIntoDataLoader() {
            registrar.registerBatchLoader("users", sampleLoader());

            DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry().build();
            registrar.registerDataLoaders(dataLoaderRegistry, GraphQLContext.newContext().build());

            assertThat(dataLoaderRegistry.getDataLoadersMap()).containsKey("users");
        }

        @Test
        @DisplayName("two loads issued before dispatch batch into a single BatchLoaderFunction call")
        void twoLoadsBatchIntoOneCall() throws Exception {
            AtomicInteger invocationCount = new AtomicInteger();
            List<List<Long>> receivedBatches = new ArrayList<>();
            BatchLoaderFunction<Long, String> countingLoader = keys -> {
                invocationCount.incrementAndGet();
                receivedBatches.add(List.copyOf(keys));
                return CompletableFuture.completedFuture(keys.stream().map(k -> "user-" + k).toList());
            };
            registrar.registerBatchLoader("users", countingLoader);

            DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry().build();
            registrar.registerDataLoaders(dataLoaderRegistry, GraphQLContext.newContext().build());
            DataLoader<Long, String> dataLoader = dataLoaderRegistry.getDataLoader("users");

            CompletableFuture<String> first = dataLoader.load(1L);
            CompletableFuture<String> second = dataLoader.load(2L);
            dataLoaderRegistry.dispatchAll();

            assertThat(first.get()).isEqualTo("user-1");
            assertThat(second.get()).isEqualTo("user-2");
            assertThat(invocationCount.get())
                    .as("both loads issued before dispatch must batch into a single call")
                    .isEqualTo(1);
            assertThat(receivedBatches).containsExactly(List.of(1L, 2L));
        }

        @Test
        @DisplayName("registers a DataLoader for every named batch loader")
        void registersEveryLoader() {
            registrar.registerBatchLoader("users", sampleLoader());
            registrar.registerBatchLoader("posts", (BatchLoaderFunction<Long, String>) keys ->
                    CompletableFuture.completedFuture(keys.stream().map(k -> "post" + k).toList()));

            DataLoaderRegistry dataLoaderRegistry = DataLoaderRegistry.newRegistry().build();
            registrar.registerDataLoaders(dataLoaderRegistry, GraphQLContext.newContext().build());

            assertThat(dataLoaderRegistry.getDataLoadersMap()).containsKeys("users", "posts");
        }
    }
}
