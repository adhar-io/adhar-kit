package com.adhar.kit.starter.actuator;

import com.adhar.kit.starter.AdharKitVersion;
import com.adhar.kit.starter.config.AdharKitAutoConfiguration.AdharKitModuleRegistry;
import com.adhar.kit.starter.config.AdharKitAutoConfiguration.AdharKitModuleRegistry.ModuleInfo;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.List;

/**
 * Actuator endpoint exposing Adhar Kit module status.
 *
 * <ul>
 *   <li>{@code GET /actuator/adhar} - version plus the enabled/disabled state
 *       of every registered module (surfaces {@link AdharKitModuleRegistry},
 *       which is built at startup but otherwise never exposed)</li>
 *   <li>{@code GET /actuator/adhar/{id}} - status of a single module by id</li>
 * </ul>
 *
 * <p>Only active when Spring Boot Actuator is on the classpath. See
 * {@code AdharKitAutoConfiguration.AdharKitEndpointConfiguration}.</p>
 *
 * @since 0.1.0
 */
@Endpoint(id = "adhar")
public class AdharKitEndpoint {

    private final AdharKitModuleRegistry registry;

    public AdharKitEndpoint(AdharKitModuleRegistry registry) {
        this.registry = registry;
    }

    /** Returns the overall Adhar Kit report: version and every module's status. */
    @ReadOperation
    public Report adhar() {
        List<ModuleStatus> modules = registry.getAllModules().stream()
                .map(AdharKitEndpoint::toStatus)
                .toList();
        long enabled = modules.stream().filter(ModuleStatus::enabled).count();
        return new Report(AdharKitVersion.getVersion(), modules.size(), enabled, modules.size() - enabled, modules);
    }

    /** Returns the status of a single module, or {@code null} if the id is unknown. */
    @ReadOperation
    public ModuleStatus module(@Selector String id) {
        return registry.getModule(id).map(AdharKitEndpoint::toStatus).orElse(null);
    }

    private static ModuleStatus toStatus(ModuleInfo info) {
        return new ModuleStatus(
                info.id(),
                info.name(),
                info.description(),
                info.enabled(),
                info.enabled() ? "UP" : "DISABLED");
    }

    /** Top-level endpoint payload. */
    public record Report(
            String version,
            int totalModules,
            long enabledModules,
            long disabledModules,
            List<ModuleStatus> modules) {
    }

    /** Per-module status payload. */
    public record ModuleStatus(
            String id,
            String name,
            String description,
            boolean enabled,
            String status) {
    }
}
