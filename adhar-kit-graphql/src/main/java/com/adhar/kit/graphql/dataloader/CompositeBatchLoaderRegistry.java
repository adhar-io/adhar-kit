package com.adhar.kit.graphql.dataloader;

import graphql.GraphQLContext;
import org.dataloader.DataLoaderRegistry;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.graphql.execution.DefaultBatchLoaderRegistry;

/**
 * A {@link BatchLoaderRegistry} that combines Spring GraphQL's standard fluent batch
 * loader registration (as used by {@code @BatchMapping} controller methods, via
 * {@link #forName(String)} / {@link #forTypePair(Class, Class)}) with the Adhar
 * {@link DataLoaderRegistrar}'s simple named-loader registrations.
 *
 * <p>Spring Boot's GraphQL auto-configuration wires exactly one
 * {@code BatchLoaderRegistry} bean into the {@code ExecutionGraphQlService}. Exposing
 * this composite as that bean (see {@code GraphQlAutoConfiguration}) ensures that
 * batch loaders registered through either mechanism end up in the same per-request
 * {@link DataLoaderRegistry}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CompositeBatchLoaderRegistry implements BatchLoaderRegistry {

    private final BatchLoaderRegistry delegate;
    private final DataLoaderRegistrar adharRegistrar;

    /**
     * Creates a composite registry backed by a fresh {@link DefaultBatchLoaderRegistry}.
     *
     * @param adharRegistrar the Adhar DataLoader registrar to merge in
     */
    public CompositeBatchLoaderRegistry(DataLoaderRegistrar adharRegistrar) {
        this(new DefaultBatchLoaderRegistry(), adharRegistrar);
    }

    /**
     * Creates a composite registry backed by the given delegate.
     *
     * @param delegate       the delegate handling the fluent {@code forName}/{@code forTypePair} API
     * @param adharRegistrar the Adhar DataLoader registrar to merge in
     */
    public CompositeBatchLoaderRegistry(BatchLoaderRegistry delegate, DataLoaderRegistrar adharRegistrar) {
        this.delegate = delegate;
        this.adharRegistrar = adharRegistrar;
    }

    @Override
    public <K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType) {
        return delegate.forTypePair(keyType, valueType);
    }

    @Override
    public <K, V> RegistrationSpec<K, V> forName(String name) {
        return delegate.forName(name);
    }

    @Override
    public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
        delegate.registerDataLoaders(registry, context);
        adharRegistrar.registerDataLoaders(registry, context);
    }

    @Override
    public boolean hasRegistrations() {
        return delegate.hasRegistrations() || adharRegistrar.hasRegistrations();
    }
}
