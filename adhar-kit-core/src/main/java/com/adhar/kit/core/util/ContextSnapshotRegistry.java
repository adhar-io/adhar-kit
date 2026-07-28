package com.adhar.kit.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link ContextSnapshot} providers consulted by
 * {@link ContextPropagatingExecutor} to decide which pieces of thread-bound
 * context to propagate across an asynchronous hand-off.
 *
 * <p>The process-wide {@linkplain #getDefault() default registry} always
 * contains the built-in {@link MdcContextSnapshot} and additionally discovers
 * any {@link ContextSnapshot} providers declared through the
 * {@link ServiceLoader}. Callers can also add providers programmatically via
 * {@link #register(ContextSnapshot)} - this is how a module such as a
 * {@code TenantContext} plugs itself in without core depending on it.</p>
 *
 * <p>Independent registries can be built with {@link #withDefaults()} or
 * {@link #empty()} for isolated use (e.g. in tests). Instances are thread-safe.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class ContextSnapshotRegistry {

    private static final ContextSnapshotRegistry DEFAULT = withDefaults().loadFromServiceLoader();

    private final List<ContextSnapshot> snapshots = new CopyOnWriteArrayList<>();

    private ContextSnapshotRegistry() {
    }

    /**
     * Returns the process-wide default registry used by
     * {@link ContextPropagatingExecutor} when no explicit snapshots are supplied.
     *
     * @return the shared default registry
     */
    public static ContextSnapshotRegistry getDefault() {
        return DEFAULT;
    }

    /**
     * Creates an empty registry with no snapshot providers.
     *
     * @return a new empty registry
     */
    public static ContextSnapshotRegistry empty() {
        return new ContextSnapshotRegistry();
    }

    /**
     * Creates a registry containing only the built-in {@link MdcContextSnapshot},
     * without running {@link ServiceLoader} discovery.
     *
     * @return a new registry with the MDC snapshot registered
     */
    public static ContextSnapshotRegistry withDefaults() {
        ContextSnapshotRegistry registry = new ContextSnapshotRegistry();
        registry.snapshots.add(new MdcContextSnapshot());
        return registry;
    }

    /**
     * Registers an additional snapshot provider. Providers are consulted in
     * registration order.
     *
     * @param snapshot the snapshot provider to add
     * @return this registry, for chaining
     * @throws NullPointerException if the snapshot is null
     */
    public ContextSnapshotRegistry register(ContextSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        snapshots.add(snapshot);
        return this;
    }

    /**
     * Removes a previously registered snapshot provider.
     *
     * @param snapshot the snapshot provider to remove
     * @return this registry, for chaining
     */
    public ContextSnapshotRegistry unregister(ContextSnapshot snapshot) {
        snapshots.remove(snapshot);
        return this;
    }

    /**
     * Discovers and registers {@link ContextSnapshot} providers via the
     * {@link ServiceLoader}.
     *
     * @return this registry, for chaining
     */
    public ContextSnapshotRegistry loadFromServiceLoader() {
        for (ContextSnapshot snapshot : ServiceLoader.load(ContextSnapshot.class)) {
            snapshots.add(snapshot);
        }
        return this;
    }

    /**
     * Returns an immutable snapshot of the currently registered providers.
     *
     * @return the registered snapshot providers, in registration order
     */
    public List<ContextSnapshot> snapshots() {
        return Collections.unmodifiableList(new ArrayList<>(snapshots));
    }
}
