package com.adhar.kit.starter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the running Adhar Kit version from the build itself, rather than a
 * hardcoded literal that inevitably drifts from {@code project.version}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>{@code META-INF/adhar-kit.properties} - filtered by Maven at build time
 *       with {@code ${project.version}} (see {@code adhar-kit-starter/pom.xml}
 *       resource filtering configuration)</li>
 *   <li>The jar manifest's {@code Implementation-Version} entry</li>
 *   <li>{@code META-INF/maven/com.adhar.kit/adhar-kit-starter/pom.properties},
 *       present in Maven-built jars</li>
 *   <li>{@code "unknown"} as a last resort (e.g. running from raw IDE output
 *       with no resource filtering applied)</li>
 * </ol>
 *
 * @since 0.1.0
 */
@Slf4j
public final class AdharKitVersion {

    private static final String BUILD_INFO_RESOURCE = "/META-INF/adhar-kit.properties";
    private static final String POM_PROPERTIES_RESOURCE =
            "/META-INF/maven/com.adhar.kit/adhar-kit-starter/pom.properties";
    private static final String UNKNOWN_VERSION = "unknown";

    private static volatile String cachedVersion;

    private AdharKitVersion() {}

    /** Returns the resolved Adhar Kit version, computed once and cached. */
    public static String getVersion() {
        String version = cachedVersion;
        if (version == null) {
            synchronized (AdharKitVersion.class) {
                version = cachedVersion;
                if (version == null) {
                    version = resolve();
                    cachedVersion = version;
                }
            }
        }
        return version;
    }

    private static String resolve() {
        String version = readProperty(BUILD_INFO_RESOURCE, "adhar-kit.version");
        if (isUsable(version)) {
            return version;
        }

        version = AdharKitVersion.class.getPackage().getImplementationVersion();
        if (isUsable(version)) {
            return version;
        }

        version = readProperty(POM_PROPERTIES_RESOURCE, "version");
        if (isUsable(version)) {
            return version;
        }

        return UNKNOWN_VERSION;
    }

    /** A value is unusable if absent, blank, or an un-filtered Maven placeholder. */
    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !value.startsWith("@") && !value.startsWith("${");
    }

    private static String readProperty(String resource, String key) {
        try (InputStream stream = AdharKitVersion.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(stream);
            return props.getProperty(key);
        } catch (IOException e) {
            log.debug("Could not read {} from {}", key, resource, e);
            return null;
        }
    }
}
