package com.adhar.kit.commons.web.versioning;

import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.MappingMatch;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The URL and header grammar of the platform's API versioning: the major version is
 * the second path segment ({@code /api/v<major>/...}), and a client may name the
 * version it was written against in an {@code API-Version} request header.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class ApiPaths {

    /** Response (and request) header carrying the full semantic version of the API. */
    public static final String API_VERSION_HEADER = "API-Version";

    /** RFC 9745 deprecation response header. */
    public static final String DEPRECATION_HEADER = "Deprecation";

    /** RFC 8594 sunset response header. */
    public static final String SUNSET_HEADER = "Sunset";

    /** Link relation naming the version that replaces a deprecated one. */
    public static final String SUCCESSOR_VERSION_REL = "successor-version";

    private static final Pattern VERSIONED_PATH = Pattern.compile("^/api/v(0|[1-9]\\d{0,8})(?:/.*)?$");

    // "1", "v1", "1.4", "1.4.2", "2.0.0-rc.1": what clients put in API-Version.
    private static final Pattern REQUESTED_VERSION = Pattern.compile(
            "^[vV]?(0|[1-9]\\d{0,8})(?:\\.(?:0|[1-9]\\d*)(?:\\.(?:0|[1-9]\\d*)(?:[-+][0-9A-Za-z.+-]*)?)?)?$");

    private ApiPaths() {
    }

    /**
     * The major version a path belongs to: {@code /api/v2/orders} → 2. {@code /api/v2}
     * itself belongs to v2; {@code /api/v2x}, {@code /api} and {@code /health} belong to
     * none.
     *
     * @param path a path within the application (no context path)
     * @return the major, or empty for paths outside the versioned API
     */
    public static OptionalInt majorOf(String path) {
        if (path == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = VERSIONED_PATH.matcher(path);
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    /**
     * The major version an {@code API-Version} request header asks for:
     * {@code 1.4.2}, {@code 1} and {@code v1} all ask for 1.
     *
     * @param header the header value
     * @return the major, or empty when the value is not a version
     */
    public static OptionalInt majorOfRequestedVersion(String header) {
        if (header == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = REQUESTED_VERSION.matcher(header.trim());
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    /**
     * The request's path below the context path and, for a DispatcherServlet mapped to
     * a prefix ({@code spring.mvc.servlet.path}), below that prefix too: the path the
     * application's own mappings see.
     *
     * @param request the request
     * @return the path, starting with {@code /}
     */
    public static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
        HttpServletMapping mapping = request.getHttpServletMapping();
        if (mapping != null && mapping.getMappingMatch() == MappingMatch.PATH) {
            String servletPath = request.getServletPath();
            if (servletPath != null && !servletPath.isEmpty() && path.startsWith(servletPath)) {
                path = path.substring(servletPath.length());
            }
        }
        return path.isEmpty() ? "/" : path;
    }
}
