package com.adhar.kit.graphql.dataloader;

import graphql.GraphQLContext;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompositeBatchLoaderRegistry}.
 */
class CompositeBatchLoaderRegistryTest {

    private DataLoaderRegistrar adharRegistrar;
    private DefaultBatchLoaderRegistry delegate;
    private CompositeBatchLoaderRegistry composite;

    @BeforeEach
    void setUp() {
        adharRegistrar = new DataLoaderRegistrar();
        delegate = new DefaultBatchLoaderRegistry();
        composite = new CompositeBatchLoaderRegistry(delegate, adharRegistrar);
    }

    @Test
    @DisplayName("forTypePair delegates to the underlying BatchLoaderRegistry")
    void forTypePairDelegates() {
        composite.forTypePair(Long.class, String.class).registerBatchLoader((keys, env) -> reactor.core.publisher.Flux.empty());

        assertThat(delegate.hasRegistrations()).isTrue();
    }

    @Test
    @DisplayName("forName delegates to the underlying BatchLoaderRegistry")
    void forNameDelegates() {
        composite.forName("named").registerBatchLoader((keys, env) -> reactor.core.publisher.Flux.empty());

        assertThat(delegate.hasRegistrations()).isTrue();
    }

    @Test
    @DisplayName("registerDataLoaders populates from both the delegate and the Adhar registrar")
    void registerDataLoadersMergesBoth() {
        composite.forName("fromDelegate").registerBatchLoader((keys, env) -> reactor.core.publisher.Flux.empty());
        adharRegistrar.registerBatchLoader("fromAdhar",
                keys -> CompletableFuture.completedFuture(keys.stream().map(String::valueOf).toList()));

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry().build();
        composite.registerDataLoaders(registry, GraphQLContext.newContext().build());

        assertThat(registry.getDataLoadersMap()).containsKeys("fromDelegate", "fromAdhar");
    }

    @Test
    @DisplayName("hasRegistrations is true when either the delegate or Adhar registrar has registrations")
    void hasRegistrationsChecksBoth() {
        assertThat(composite.hasRegistrations()).isFalse();

        adharRegistrar.registerBatchLoader("users",
                keys -> CompletableFuture.completedFuture(keys.stream().map(String::valueOf).toList()));

        assertThat(composite.hasRegistrations()).isTrue();
    }

    @Test
    @DisplayName("single-arg constructor wires a fresh DefaultBatchLoaderRegistry delegate")
    void singleArgConstructorWorks() {
        DataLoaderRegistrar registrar = new DataLoaderRegistrar();
        registrar.registerBatchLoader("users",
                keys -> CompletableFuture.completedFuture(keys.stream().map(String::valueOf).toList()));
        CompositeBatchLoaderRegistry standalone = new CompositeBatchLoaderRegistry(registrar);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry().build();
        standalone.registerDataLoaders(registry, GraphQLContext.newContext().build());

        assertThat(registry.getDataLoadersMap()).containsKey("users");
    }
}
