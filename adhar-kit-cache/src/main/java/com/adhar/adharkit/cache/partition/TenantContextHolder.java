package com.adhar.adharkit.cache.partition;

/**
 * Thread-local holder for the current tenant identifier used by the default
 * {@link ThreadLocalKeyPartitionResolver}.
 *
 * <p>Set the tenant at the start of request processing (e.g. in a servlet
 * filter or interceptor) and always {@link #clear()} it afterwards to avoid
 * leaking tenants across pooled threads:</p>
 *
 * <pre>{@code
 * try {
 *     TenantContextHolder.setTenant(request.getHeader("X-Tenant-Id"));
 *     chain.doFilter(request, response);
 * } finally {
 *     TenantContextHolder.clear();
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
        // static holder
    }

    /**
     * Sets the tenant for the current thread.
     *
     * @param tenant the tenant identifier (null clears the tenant)
     */
    public static void setTenant(String tenant) {
        if (tenant == null) {
            TENANT.remove();
        } else {
            TENANT.set(tenant);
        }
    }

    /**
     * Gets the tenant bound to the current thread.
     *
     * @return the tenant identifier, or null if none is set
     */
    public static String getTenant() {
        return TENANT.get();
    }

    /**
     * Clears the tenant bound to the current thread.
     */
    public static void clear() {
        TENANT.remove();
    }
}