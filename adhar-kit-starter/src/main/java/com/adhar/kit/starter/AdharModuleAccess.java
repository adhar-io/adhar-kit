package com.adhar.kit.starter;

import java.util.Map;
import java.util.Set;

/**
 * Framework-neutral source of truth for which Adhar Kit modules are enabled.
 *
 * <p>{@link AdharFacade} consults an {@code AdharModuleAccess} before
 * constructing or returning any sub-facade, so a module that is toggled off
 * (for example via {@code adhar.kit.modules.<name>.enabled=false} on Spring
 * Boot) is never initialized and cannot be accessed. The same instance is
 * used by {@code AdharKitModuleRegistry} on Spring Boot, so both components
 * agree on module state at all times.</p>
 *
 * <p>Module ids are plain strings (e.g. {@code "cache"}, {@code "ai"},
 * {@code "event-sourcing"}) matching the ids used by the module registry and
 * the {@code adhar.kit.modules.*} configuration properties.</p>
 *
 * @since 0.1.0
 */
public final class AdharModuleAccess {

    /** Access instance under which every module id resolves to enabled. */
    public static final AdharModuleAccess ALL_ENABLED = new AdharModuleAccess(Map.of(), true);

    private final Map<String, Boolean> toggles;
    private final boolean defaultEnabled;

    private AdharModuleAccess(Map<String, Boolean> toggles, boolean defaultEnabled) {
        this.toggles = Map.copyOf(toggles);
        this.defaultEnabled = defaultEnabled;
    }

    /**
     * Builds an access object from an explicit id-to-enabled map. Any module
     * id not present in the map defaults to enabled, so newly introduced
     * modules stay opt-out rather than silently disabled.
     */
    public static AdharModuleAccess of(Map<String, Boolean> toggles) {
        return new AdharModuleAccess(toggles, true);
    }

    /** Returns whether the given module id is currently enabled. */
    public boolean isEnabled(String moduleId) {
        return toggles.getOrDefault(moduleId, defaultEnabled);
    }

    /** Returns the ids explicitly toggled off. */
    public Set<String> disabledModuleIds() {
        return toggles.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
