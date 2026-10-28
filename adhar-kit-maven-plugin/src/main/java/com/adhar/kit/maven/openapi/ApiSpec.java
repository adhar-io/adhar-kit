package com.adhar.kit.maven.openapi;

import java.nio.file.Path;
import java.util.Objects;

/**
 * An OpenAPI document the plugin lints and generates code from, resolved to the major
 * version it serves, the preset that generates it and the Java package it lands in.
 *
 * @param file        the spec file
 * @param major       the major version, the {@code N} of {@code /api/vN}
 * @param preset      the code-generation preset
 * @param packageName the package of the generated interfaces; models go to
 *                    {@code <packageName>.model}
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record ApiSpec(Path file, int major, OpenApiPreset preset, String packageName) {

    public ApiSpec {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(packageName, "packageName");
        if (major < 0) {
            throw new IllegalArgumentException("major must be >= 0, got " + major);
        }
    }

    /**
     * Whether the spec describes the service's own API, which is then shipped in the
     * artifact so the service can serve it at {@link #servedPath()}.
     */
    public boolean shipped() {
        return preset.server();
    }

    /** Where the spec lives on the runtime classpath, {@code api/v<major>/openapi.yaml}. */
    public String classpathLocation() {
        return "api/v" + major + "/" + ApiSpecs.SPEC_FILE_NAME;
    }

    /** The URL path the service serves the spec at, {@code /api/v<major>/openapi.yaml}. */
    public String servedPath() {
        return "/" + classpathLocation();
    }

    /** The package of the generated models. */
    public String modelPackage() {
        return packageName + ".model";
    }
}
