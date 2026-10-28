package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.config.AdharCommonsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The major versions of the service's HTTP API and what is announced about each.
 *
 * <p>Built at startup from the OpenAPI documents shipped in the artifact
 * ({@code api/v<major>/openapi.yaml}, whose {@code info.version} is the version every
 * response under {@code /api/v<major>} reports) and from
 * {@code adhar.commons.api-versioning.versions.v<major>.*}, which can override the
 * version and announces deprecation, sunset and successor. Majors the application maps
 * without either are added once the handler mappings exist, so every versioned path
 * gets its headers.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class ApiVersionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionRegistry.class);

    private static final String PROPERTY_PREFIX = "adhar.commons.api-versioning.versions.";
    private static final Pattern SPEC_LOCATION = Pattern.compile(".*/api/v(0|[1-9]\\d{0,8})/openapi\\.ya?ml$");
    private static final Pattern VERSION_KEY = Pattern.compile("^[vV]?(0|[1-9]\\d{0,8})$");

    private volatile NavigableMap<Integer, ApiMajorVersion> versions;

    /**
     * Creates a registry of the given versions.
     *
     * @param versions one entry per major
     * @throws IllegalArgumentException when a major appears twice
     */
    public ApiVersionRegistry(Collection<ApiMajorVersion> versions) {
        NavigableMap<Integer, ApiMajorVersion> byMajor = new TreeMap<>();
        for (ApiMajorVersion version : versions) {
            if (byMajor.putIfAbsent(version.major(), version) != null) {
                throw new IllegalArgumentException("/api/v" + version.major() + " is declared twice");
            }
        }
        this.versions = Collections.unmodifiableNavigableMap(byMajor);
    }

    /**
     * The version serving {@code /api/v<major>}.
     *
     * @param major the major
     * @return the version, or empty when the service does not serve that major
     */
    public Optional<ApiMajorVersion> get(int major) {
        return Optional.ofNullable(versions.get(major));
    }

    /**
     * The version a path belongs to.
     *
     * @param pathWithinApplication the path, without context path
     * @return the version, or empty for paths outside the versioned API or of an unknown major
     */
    public Optional<ApiMajorVersion> forPath(String pathWithinApplication) {
        OptionalInt major = ApiPaths.majorOf(pathWithinApplication);
        return major.isPresent() ? get(major.getAsInt()) : Optional.empty();
    }

    /** Every version, lowest major first. */
    public List<ApiMajorVersion> versions() {
        return List.copyOf(versions.values());
    }

    /**
     * Records a major the application maps but neither ships a spec for nor configures;
     * its version is reported as {@code <major>.0.0}.
     *
     * @param major the major
     * @return whether it was new
     */
    public synchronized boolean registerMapped(int major) {
        if (versions.containsKey(major)) {
            return false;
        }
        NavigableMap<Integer, ApiMajorVersion> copy = new TreeMap<>(versions);
        copy.put(major, ApiMajorVersion.of(major, major + ".0.0"));
        versions = Collections.unmodifiableNavigableMap(copy);
        log.info("/api/v{} is mapped but has no api/v{}/openapi.yaml and no {}v{}.version; reporting {}.0.0",
                major, major, PROPERTY_PREFIX, major, major);
        return true;
    }

    /**
     * Builds the registry from configuration and the specs shipped on the classpath.
     *
     * @param properties the versioning properties
     * @param resolver   resolves {@code specLocations}
     * @return the registry
     * @throws IllegalStateException when the configuration announces something invalid
     */
    public static ApiVersionRegistry load(AdharCommonsProperties.ApiVersioning properties,
                                          ResourcePatternResolver resolver) {
        Map<Integer, Resource> specs = shippedSpecs(properties.getSpecLocations(), resolver);
        Map<Integer, AdharCommonsProperties.ApiVersioning.Version> configured = configured(properties.getVersions());

        TreeSet<Integer> majors = new TreeSet<>(specs.keySet());
        majors.addAll(configured.keySet());
        List<ApiMajorVersion> versions = new ArrayList<>();
        for (int major : majors) {
            versions.add(version(major, specs.get(major), configured.get(major)));
        }
        return new ApiVersionRegistry(withDefaultSuccessors(versions));
    }

    private static ApiMajorVersion version(int major, Resource spec,
                                           AdharCommonsProperties.ApiVersioning.Version settings) {
        String prefix = PROPERTY_PREFIX + "v" + major + ".";
        String version = settings != null && settings.getVersion() != null && !settings.getVersion().isBlank()
                ? settings.getVersion().trim() : specVersion(major, spec);
        try {
            if (settings == null) {
                return new ApiMajorVersion(major, version, null, null, null, null, null, spec);
            }
            Instant deprecation = ApiVersionDates.deprecation(settings.getDeprecated(), prefix + "deprecated");
            Instant sunset = ApiVersionDates.instant(settings.getSunset(), prefix + "sunset");
            if (sunset != null && deprecation != null && sunset.isBefore(deprecation)) {
                throw new IllegalArgumentException(prefix + "sunset is before its deprecation");
            }
            String successor = settings.getSuccessor() == null || settings.getSuccessor().isBlank()
                    ? null : settings.getSuccessor().trim();
            return new ApiMajorVersion(major, version, deprecation, sunset, successor,
                    settings.getDeprecationLink(), settings.getSunsetLink(), spec);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid API version configuration for /api/v" + major + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * A deprecated version without a configured successor points at the next major the
     * service serves, so clients learn where to go without anyone writing it down.
     */
    private static List<ApiMajorVersion> withDefaultSuccessors(List<ApiMajorVersion> versions) {
        List<ApiMajorVersion> result = new ArrayList<>(versions.size());
        for (int i = 0; i < versions.size(); i++) {
            ApiMajorVersion version = versions.get(i);
            if (version.deprecationAnnounced() && version.successor() == null && i + 1 < versions.size()) {
                version = version.withSuccessor(versions.get(i + 1).prefix());
            }
            result.add(version);
        }
        return result;
    }

    private static Map<Integer, AdharCommonsProperties.ApiVersioning.Version> configured(
            Map<String, AdharCommonsProperties.ApiVersioning.Version> settings) {
        Map<Integer, AdharCommonsProperties.ApiVersioning.Version> byMajor = new LinkedHashMap<>();
        if (settings == null) {
            return byMajor;
        }
        for (Map.Entry<String, AdharCommonsProperties.ApiVersioning.Version> entry : settings.entrySet()) {
            Matcher matcher = VERSION_KEY.matcher(entry.getKey().trim());
            if (!matcher.matches()) {
                throw new IllegalStateException(PROPERTY_PREFIX + entry.getKey()
                        + " does not name a major version; use v1, v2, ...");
            }
            byMajor.put(Integer.parseInt(matcher.group(1)), entry.getValue());
        }
        return byMajor;
    }

    private static Map<Integer, Resource> shippedSpecs(List<String> locations, ResourcePatternResolver resolver) {
        Map<Integer, Resource> specs = new TreeMap<>();
        if (locations == null) {
            return specs;
        }
        for (String location : locations) {
            Resource[] resources;
            try {
                resources = resolver.getResources(location);
            } catch (IOException e) {
                log.warn("Cannot look up OpenAPI specs at {}: {}", location, e.getMessage());
                continue;
            }
            for (Resource resource : resources) {
                OptionalInt major = majorOf(resource);
                if (major.isEmpty()) {
                    continue;
                }
                Resource first = specs.putIfAbsent(major.getAsInt(), resource);
                if (first != null && !first.equals(resource)) {
                    log.warn("Two OpenAPI specs for /api/v{} on the classpath; serving {} and ignoring {}",
                            major.getAsInt(), first.getDescription(), resource.getDescription());
                }
            }
        }
        return specs;
    }

    private static OptionalInt majorOf(Resource resource) {
        try {
            Matcher matcher = SPEC_LOCATION.matcher(resource.getURL().toString());
            return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
        } catch (IOException e) {
            return OptionalInt.empty();
        }
    }

    private static String specVersion(int major, Resource spec) {
        String fallback = major + ".0.0";
        if (spec == null) {
            return fallback;
        }
        String version = infoVersion(spec);
        if (version == null || !ApiMajorVersion.isSemanticVersion(version) || !version.startsWith(major + ".")) {
            log.warn("{} declares info.version '{}', which is not a semantic version of /api/v{}"
                    + " (the openapi-lint goal rejects it); reporting {}", spec.getDescription(), version, major, fallback);
            return fallback;
        }
        return version;
    }

    private static String infoVersion(Resource spec) {
        if (!ClassUtils.isPresent("org.yaml.snakeyaml.Yaml", ApiVersionRegistry.class.getClassLoader())) {
            log.warn("SnakeYAML is not on the classpath; cannot read info.version of {}", spec.getDescription());
            return null;
        }
        try (InputStream in = spec.getInputStream()) {
            Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            if (document instanceof Map<?, ?> root && root.get("info") instanceof Map<?, ?> info) {
                Object version = info.get("version");
                return version == null ? null : String.valueOf(version);
            }
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot read info.version of {}: {}", spec.getDescription(), e.getMessage());
            return null;
        }
    }
}
