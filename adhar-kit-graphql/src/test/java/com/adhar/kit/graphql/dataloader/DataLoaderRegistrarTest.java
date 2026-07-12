package com.adhar.kit.graphql.dataloader;

import com.adhar.kit.graphql.dataloader.DataLoaderRegistrar.BatchLoaderFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
}
