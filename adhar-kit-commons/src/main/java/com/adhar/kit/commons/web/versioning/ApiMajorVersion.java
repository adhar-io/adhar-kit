package com.adhar.kit.commons.web.versioning;

import org.springframework.core.io.Resource;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * One major version of the service's HTTP API, served under {@code /api/v<major>}.
 *
 * <p>The major is in the path because it is the only change a client must opt into;
 * minor and patch releases keep the path and only move {@link #version()}, which every
 * response under the prefix carries in the {@code API-Version} header.</p>
 *
 * @param major           the {@code N} of {@code /api/vN}
 * @param version         the full semantic version ({@code info.version} of the spec)
 * @param deprecation     when the version was (or will be) deprecated; null while current
 * @param sunset          when it stops answering; null when none is announced
 * @param successor       URL of the version replacing it, e.g. {@code /api/v2}; null for none
 * @param deprecationLink documentation about the deprecation ({@code rel="deprecation"})
 * @param sunsetLink      documentation about the sunset ({@code rel="sunset"})
 * @param spec            the OpenAPI document shipped for this major; null when none is
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record ApiMajorVersion(int major, String version, Instant deprecation, Instant sunset, String successor,
                              URI deprecationLink, URI sunsetLink, Resource spec) {

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    /** Index status of a version that is not deprecated (or not yet). */
    public static final String STATUS_CURRENT = "current";

    /** Index status of a version past its deprecation date. */
    public static final String STATUS_DEPRECATED = "deprecated";

    public ApiMajorVersion {
        if (major < 0) {
            throw new IllegalArgumentException("API major version must be >= 0, got " + major);
        }
        if (!isSemanticVersion(version)) {
            throw new IllegalArgumentException("API version '" + version + "' of /api/v" + major
                    + " is not a semantic version (MAJOR.MINOR.PATCH)");
        }
        if (!version.startsWith(major + ".")) {
            throw new IllegalArgumentException("API version '" + version + "' does not belong to /api/v" + major);
        }
    }

    /**
     * A current version with nothing but a number, as known from a mapping alone.
     *
     * @param major   the major
     * @param version the full version
     * @return the version
     */
    public static ApiMajorVersion of(int major, String version) {
        return new ApiMajorVersion(major, version, null, null, null, null, null, null);
    }

    /** Whether {@code version} is a Semantic Versioning 2.0.0 version. */
    public static boolean isSemanticVersion(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    /** The path prefix, {@code /api/v<major>}. */
    public String prefix() {
        return "/api/v" + major;
    }

    /** Where the shipped spec is served, {@code /api/v<major>/openapi.yaml}. */
    public String specPath() {
        return prefix() + "/openapi.yaml";
    }

    /** Whether a deprecation date is announced (it may still lie ahead). */
    public boolean deprecationAnnounced() {
        return deprecation != null;
    }

    /**
     * {@value #STATUS_DEPRECATED} once the deprecation date has passed, otherwise
     * {@value #STATUS_CURRENT}.
     *
     * @param now the current instant
     * @return the status for the {@code GET /api} index
     */
    public String status(Instant now) {
        return deprecation != null && !deprecation.isAfter(now) ? STATUS_DEPRECATED : STATUS_CURRENT;
    }

    /** The RFC 9745 {@code Deprecation} header value ({@code @<epoch-seconds>}), or null. */
    public String deprecationHeader() {
        return deprecation == null ? null : "@" + deprecation.getEpochSecond();
    }

    /** The RFC 8594 {@code Sunset} header value (an HTTP-date), or null. */
    public String sunsetHeader() {
        return sunset == null ? null
                : DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(sunset, ZoneOffset.UTC));
    }

    /** The {@code Link} header naming the successor version, or null. */
    public String successorLinkHeader() {
        return successor == null ? null : "<" + successor + ">; rel=\"" + ApiPaths.SUCCESSOR_VERSION_REL + "\"";
    }

    /**
     * A copy with the given successor.
     *
     * @param newSuccessor the successor URL
     * @return the copy
     */
    public ApiMajorVersion withSuccessor(String newSuccessor) {
        return new ApiMajorVersion(major, version, deprecation, sunset, newSuccessor, deprecationLink, sunsetLink, spec);
    }
}
