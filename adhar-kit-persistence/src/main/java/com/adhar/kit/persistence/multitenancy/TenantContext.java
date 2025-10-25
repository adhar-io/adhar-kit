package com.adhar.kit.persistence.multitenancy;

/**
 * Manages the current tenant context for multi-tenancy.
 * Thread-safe implementation using ThreadLocal.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "default";

    /**
     * Set the current tenant.
     */
    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Get the current tenant.
     */
    public static String getTenant() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    /**
     * Clear the current tenant.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Check if tenant is set.
     */
    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}

