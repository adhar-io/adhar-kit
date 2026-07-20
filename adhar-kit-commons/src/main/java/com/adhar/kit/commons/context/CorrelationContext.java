package com.adhar.kit.commons.context;

import java.util.function.Supplier;

/**
 * Thread-local holder for the current correlation id and request id used for
 * distributed request tracing.
 *
 * <p>Populated by {@link com.adhar.kit.commons.web.CorrelationIdFilter} from the
 * {@code X-Correlation-ID} / {@code X-Request-ID} headers in web applications, but can
 * also be managed manually via the setters / {@link #clear()} or scoped with
 * {@link #runWith(String, Runnable)}.</p>
 *
 * <p><b>Important:</b> always {@link #clear()} in a {@code finally} block when setting
 * values manually - thread pools reuse threads.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private CorrelationContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the correlation id bound to the current thread, or {@code null} if none.
     *
     * @return the current correlation id or {@code null}
     */
    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }

    /**
     * Binds the correlation id to the current thread.
     *
     * @param correlationId the correlation id (may be {@code null} to unset)
     */
    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    /**
     * Returns the request id bound to the current thread, or {@code null} if none.
     *
     * @return the current request id or {@code null}
     */
    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    /**
     * Binds the request id to the current thread.
     *
     * @param requestId the request id (may be {@code null} to unset)
     */
    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    /**
     * Removes the correlation id and request id from the current thread.
     */
    public static void clear() {
        CORRELATION_ID.remove();
        REQUEST_ID.remove();
    }

    /**
     * Runs the action with the given correlation id bound, restoring the previous
     * value (or clearing) afterwards.
     *
     * @param correlationId the correlation id to bind for the duration of the action
     * @param action        the action to run
     */
    public static void runWith(String correlationId, Runnable action) {
        String previous = CORRELATION_ID.get();
        CORRELATION_ID.set(correlationId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    /**
     * Calls the supplier with the given correlation id bound, restoring the previous
     * value (or clearing) afterwards.
     *
     * @param correlationId the correlation id to bind for the duration of the call
     * @param action        the supplier to call
     * @param <T>           the result type
     * @return the supplier's result
     */
    public static <T> T callWith(String correlationId, Supplier<T> action) {
        String previous = CORRELATION_ID.get();
        CORRELATION_ID.set(correlationId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous != null) {
            CORRELATION_ID.set(previous);
        } else {
            CORRELATION_ID.remove();
        }
    }
}
