package com.adhar.kit.commons.context;

import java.util.function.Supplier;

/**
 * Thread-local holder for the current tenant identifier in multi-tenant services.
 *
 * <p>Populated by {@link com.adhar.kit.commons.web.TenantContextFilter} from the
 * {@code X-Tenant-ID} header in web applications, but can also be managed manually
 * (e.g. in messaging listeners) via {@link #setTenantId(String)} / {@link #clear()}
 * or scoped with {@link #runWith(String, Runnable)}.</p>
 *
 * <p><b>Important:</b> always {@link #clear()} in a {@code finally} block when setting
 * the tenant manually - thread pools reuse threads.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the tenant id bound to the current thread, or {@code null} if none.
     *
     * @return the current tenant id or {@code null}
     */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * Binds the tenant id to the current thread.
     *
     * @param tenantId the tenant id (may be {@code null} to unset)
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * Returns whether a tenant id is bound to the current thread.
     *
     * @return {@code true} if a tenant id is set
     */
    public static boolean isSet() {
        return TENANT_ID.get() != null;
    }

    /**
     * Removes the tenant id from the current thread.
     */
    public static void clear() {
        TENANT_ID.remove();
    }

    /**
     * Runs the action with the given tenant id bound, restoring the previous
     * value (or clearing) afterwards.
     *
     * @param tenantId the tenant id to bind for the duration of the action
     * @param action   the action to run
     */
    public static void runWith(String tenantId, Runnable action) {
        String previous = TENANT_ID.get();
        TENANT_ID.set(tenantId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    /**
     * Calls the supplier with the given tenant id bound, restoring the previous
     * value (or clearing) afterwards.
     *
     * @param tenantId the tenant id to bind for the duration of the call
     * @param action   the supplier to call
     * @param <T>      the result type
     * @return the supplier's result
     */
    public static <T> T callWith(String tenantId, Supplier<T> action) {
        String previous = TENANT_ID.get();
        TENANT_ID.set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous != null) {
            TENANT_ID.set(previous);
        } else {
            TENANT_ID.remove();
        }
    }
}
