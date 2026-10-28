package com.adhar.kit.commons.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the adhar-kit-commons auto-configuration.
 *
 * <p>All features default to enabled. Example:</p>
 * <pre>
 * adhar:
 *   commons:
 *     exception-handler:
 *       enabled: true
 *       problem-details: versioned-api   # always | never
 *     idempotency:
 *       enabled: true
 *     correlation:
 *       enabled: true
 *     tenant:
 *       enabled: true
 *     api-versioning:
 *       enabled: true
 *       reject-mismatched-major: true
 *       versions:
 *         v1:
 *           deprecated: 2026-09-01
 *           sunset: 2027-01-31
 *           successor: /api/v2
 *     error-catalog:
 *       enabled: true
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.commons")
public class AdharCommonsProperties {

    /** Global exception handler settings. */
    private final ExceptionHandler exceptionHandler = new ExceptionHandler();

    /** Idempotency runtime settings. */
    private final Idempotency idempotency = new Idempotency();

    /** Correlation-id filter settings. */
    private final Correlation correlation = new Correlation();

    /** Tenant-context filter settings. */
    private final Tenant tenant = new Tenant();

    /** API versioning settings. */
    private final ApiVersioning apiVersioning = new ApiVersioning();

    /** Error catalog (i18n) settings. */
    private final ErrorCatalog errorCatalog = new ErrorCatalog();

    /** Toggle for the {@code SpringGlobalExceptionHandler} bean. */
    @Data
    public static class ExceptionHandler {
        /** Whether the global exception handler is registered. */
        private boolean enabled = true;
        /**
         * Which error responses are RFC 9457 problem details
         * ({@code application/problem+json}) rather than the {@code ErrorResponse} body.
         */
        private ProblemDetailsScope problemDetails = ProblemDetailsScope.VERSIONED_API;
    }

    /** Which error responses are written as RFC 9457 problem details. */
    public enum ProblemDetailsScope {
        /**
         * Responses to {@code /api/v<major>} requests, whose specs declare every error as
         * {@code application/problem+json}; other paths keep the {@code ErrorResponse} body.
         */
        VERSIONED_API,
        /** Every error response. */
        ALWAYS,
        /** None: the {@code ErrorResponse} body everywhere. */
        NEVER
    }

    /** Toggle for the idempotency store and aspect beans. */
    @Data
    public static class Idempotency {
        /** Whether the idempotency runtime is registered. */
        private boolean enabled = true;
    }

    /** Toggle for the correlation-id servlet filter. */
    @Data
    public static class Correlation {
        /** Whether the correlation-id filter is registered. */
        private boolean enabled = true;
    }

    /** Toggle for the tenant-context servlet filter. */
    @Data
    public static class Tenant {
        /** Whether the tenant-context filter is registered. */
        private boolean enabled = true;
    }

    /**
     * Settings for API versioning: the major version is the path segment of
     * {@code /api/v<major>}, and every response below it carries the full version.
     */
    @Data
    public static class ApiVersioning {
        /**
         * Whether API versioning is configured: Spring's path-segment version resolution,
         * the {@code API-Version}/{@code Deprecation}/{@code Sunset}/{@code Link} headers,
         * {@code GET /api} and the spec endpoints.
         */
        private boolean enabled = true;
        /**
         * Whether a request whose {@code API-Version} header names another major than its
         * {@code /api/v<major>} path is rejected with a 400 problem.
         */
        private boolean rejectMismatchedMajor = true;
        /**
         * Legacy: whether the deprecated {@code @ApiVersion} interceptor rejects a
         * mismatching {@code X-API-Version} header with 400 (paths outside
         * {@code /api/v<major>} only).
         */
        private boolean validateRequestVersion = false;
        /** Where the OpenAPI documents shipped in the artifact are found, one per major. */
        private List<String> specLocations = new ArrayList<>(List.of("classpath*:api/v*/openapi.yaml"));
        /** The {@code GET /api} index and {@code GET /api/v<major>/openapi.yaml}. */
        private final Endpoints endpoints = new Endpoints();
        /** Per-major settings, keyed {@code v1}, {@code v2}, ... */
        private final Map<String, Version> versions = new LinkedHashMap<>();

        /** Toggle for the versioning endpoints. */
        @Data
        public static class Endpoints {
            /** Whether {@code GET /api} and the spec endpoints are served. */
            private boolean enabled = true;
        }

        /** What is announced about one major version. */
        @Data
        public static class Version {
            /** Full semantic version reported for the major; defaults to the shipped spec's {@code info.version}. */
            private String version;
            /**
             * When the major was (or will be) deprecated: an ISO-8601 date or date-time,
             * epoch seconds, or {@code true} when no date is stated.
             */
            private String deprecated;
            /** When the major stops answering (RFC 8594 {@code Sunset}); same formats. */
            private String sunset;
            /** URL of the version replacing it; defaults to the next major the service serves. */
            private String successor;
            /** Documentation about the deprecation, linked with {@code rel="deprecation"}. */
            private URI deprecationLink;
            /** Documentation about the sunset, linked with {@code rel="sunset"}. */
            private URI sunsetLink;
        }
    }

    /** Toggle for the localized {@code ErrorCatalog} bean. */
    @Data
    public static class ErrorCatalog {
        /** Whether the error catalog is registered and wired into the exception handler. */
        private boolean enabled = true;
    }
}
