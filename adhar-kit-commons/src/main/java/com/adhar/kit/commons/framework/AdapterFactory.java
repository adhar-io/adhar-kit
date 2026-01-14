package com.adhar.kit.commons.framework;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Factory for creating framework-specific adapters.
 * Uses ServiceLoader pattern for discovery.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public final class AdapterFactory {

    private AdapterFactory() {
        // Utility class
    }

    /**
     * Create an adapter for the detected framework.
     *
     * @param serviceType the service interface type
     * @param <T> service type
     * @return framework-specific adapter
     * @throws IllegalStateException if no suitable adapter found
     */
    public static <T> FrameworkAdapter<T> createAdapter(Class<T> serviceType) {
        Framework framework = FrameworkDetector.detect();
        return createAdapter(serviceType, framework);
    }

    /**
     * Create an adapter for a specific framework.
     *
     * @param serviceType the service interface type
     * @param framework target framework
     * @param <T> service type
     * @return framework-specific adapter
     * @throws IllegalStateException if no suitable adapter found
     */
    @SuppressWarnings("unchecked")
    public static <T> FrameworkAdapter<T> createAdapter(Class<T> serviceType, Framework framework) {
        // Load all adapters for the service type
        ServiceLoader<FrameworkAdapter> loader = ServiceLoader.load(FrameworkAdapter.class);

        List<FrameworkAdapter<T>> candidates = new ArrayList<>();
        for (FrameworkAdapter<?> adapter : loader) {
            if (adapter.getSupportedFramework() == framework) {
                candidates.add((FrameworkAdapter<T>) adapter);
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                String.format("No adapter found for %s on framework %s",
                    serviceType.getSimpleName(), framework)
            );
        }

        if (candidates.size() > 1) {
            log.warn("Multiple adapters found for {}, using first: {}",
                serviceType.getSimpleName(), candidates.get(0).getClass().getName());
        }

        FrameworkAdapter<T> adapter = candidates.get(0);
        log.debug("Created {} adapter for framework {}", serviceType.getSimpleName(), framework);

        return adapter;
    }

    /**
     * Get service directly without adapter wrapper.
     *
     * @param serviceType the service interface type
     * @param <T> service type
     * @return service instance
     */
    public static <T> T getService(Class<T> serviceType) {
        return createAdapter(serviceType).getService();
    }

    /**
     * Check if an adapter is available for the service type.
     *
     * @param serviceType the service interface type
     * @return true if adapter is available
     */
    public static boolean isAdapterAvailable(Class<?> serviceType) {
        try {
            createAdapter((Class<Object>) serviceType);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}

