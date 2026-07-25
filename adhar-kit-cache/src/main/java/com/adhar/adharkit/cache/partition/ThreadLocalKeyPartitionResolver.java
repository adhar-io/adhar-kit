package com.adhar.adharkit.cache.partition;

/**
 * Default {@link KeyPartitionResolver} backed by {@link TenantContextHolder}'s
 * thread-local tenant.
 *
 * <p>Returns {@code null} when no tenant is bound to the current thread, in
 * which case keys are left unpartitioned.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class ThreadLocalKeyPartitionResolver implements KeyPartitionResolver {

    @Override
    public String resolvePartition() {
        return TenantContextHolder.getTenant();
    }
}