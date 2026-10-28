package com.adhar.kit.commons.web.versioning;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The discovery endpoints of a versioned API:
 * <ul>
 *   <li>{@code GET /api} lists every major version the service serves -
 *       {@code [{major, version, status, sunset, spec}]}, lowest first - so a client
 *       or the platform's catalog finds them without knowing the service;</li>
 *   <li>{@code GET /api/v<major>/openapi.yaml} serves the OpenAPI document shipped in
 *       the artifact, the same file the code was generated from, with an ETag so a
 *       catalog can poll it cheaply.</li>
 * </ul>
 *
 * <p>Functional routes rather than a controller: they sit in front of the annotated
 * controllers, so a service's own {@code /api/v1/{id}} mapping cannot capture the spec
 * path.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class ApiVersionEndpoints {

    /** {@code GET /api}. */
    public static final String INDEX_PATH = "/api";

    private ApiVersionEndpoints() {
    }

    /**
     * Routes for {@code registry}: the index, and one spec route per major that ships a
     * spec.
     *
     * @param registry the versions
     * @return the routes
     */
    public static RouterFunction<ServerResponse> routes(ApiVersionRegistry registry) {
        return routes(registry, Clock.systemUTC());
    }

    static RouterFunction<ServerResponse> routes(ApiVersionRegistry registry, Clock clock) {
        RouterFunctions.Builder routes = RouterFunctions.route()
                .GET(INDEX_PATH, request -> index(registry, clock, request));
        for (ApiMajorVersion version : registry.versions()) {
            if (version.spec() != null) {
                ShippedSpec spec = ShippedSpec.read(version);
                routes.GET(version.specPath(), request -> spec.serve(request));
            }
        }
        return routes.build();
    }

    private static ServerResponse index(ApiVersionRegistry registry, Clock clock, ServerRequest request) {
        String contextPath = request.servletRequest().getContextPath();
        Instant now = clock.instant();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ApiMajorVersion version : registry.versions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("major", version.major());
            entry.put("version", version.version());
            entry.put("status", version.status(now));
            entry.put("sunset", version.sunset() == null ? null : version.sunset().toString());
            entry.put("spec", version.spec() == null ? null : contextPath + version.specPath());
            entries.add(entry);
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(entries);
    }

    /**
     * A spec read once at startup: it is part of the artifact and never changes while the
     * service runs.
     */
    private record ShippedSpec(byte[] content, MediaType contentType, String etag) {

        static ShippedSpec read(ApiMajorVersion version) {
            byte[] content;
            try (InputStream in = version.spec().getInputStream()) {
                content = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the OpenAPI spec of " + version.prefix()
                        + " from " + version.spec().getDescription(), e);
            }
            // JSON is valid YAML too; label it as what it is, so a browser shows it as such.
            boolean json = new String(content, StandardCharsets.UTF_8).stripLeading().startsWith("{");
            return new ShippedSpec(content, json ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_YAML,
                    "\"" + sha256(content) + "\"");
        }

        ServerResponse serve(ServerRequest request) {
            return request.checkNotModified(etag).orElseGet(() -> ServerResponse.ok()
                    .contentType(contentType)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .body(content));
        }

        private static String sha256(byte[] content) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required of every JVM", e);
            }
        }
    }
}
