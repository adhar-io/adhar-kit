package com.adhar.adharkit.cache.partition;

/**
 * SPI resolving the current cache key partition (typically the current tenant).
 *
 * <p>The default implementation, {@link ThreadLocalKeyPartitionResolver}, reads
 * the partition from this module's {@link TenantContextHolder}. Applications
 * can supply their own bean (e.g. an adapter over a commons {@code TenantContext})
 * to plug a different tenant source in — the auto-configuration registers the
 * default only when no {@code KeyPartitionResolver} bean is present.</p>
 *
 * <p>Used by {@link com.adhar.adharkit.cache.key.CacheKeyGenerator} to prefix
 * generated keys when partitioning is enabled (globally via
 * {@code adhar.cache.partitioning.enabled} or per method via
 * {@link com.adhar.adharkit.cache.annotation.CachePartition}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@FunctionalInterface
public interface KeyPartitionResolver {

    /**
     * Resolves the partition for the current execution context.
     *
     * @return the partition identifier, or {@code null}/blank when no
     *         partition applies (keys are then left unprefixed)
     */
    String resolvePartition();
}
